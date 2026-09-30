#!/usr/bin/env python3
"""Send monochrome images with explicit raw-USB or ImageToEpd v3.6 protocols.
Serial completion is not confirmation of a physical screen refresh.
"""
import argparse
import json
import time
import sys
import hashlib
from pathlib import Path
import serial
from serial.tools import list_ports
from PIL import Image, ImageOps, ImageDraw, ImageFont
try:
    from . import imagetoepd36
except ImportError:
    import imagetoepd36


def receive(port, seconds):
    end = time.monotonic() + seconds
    data = bytearray()
    while time.monotonic() < end:
        chunk = port.read(min(port.in_waiting or 1, 4096))
        if chunk:
            data.extend(chunk)
            if len(data) >= 16384:
                break
    return {"length": len(data), "hex": data.hex(), "text": data.decode("utf-8", errors="replace")}


def validate_dimensions(width, height):
    if width < 1 or height < 1 or width * height > 2_000_000:
        raise ValueError('Invalid canvas dimensions')


def test_pattern(width, height):
    """High-contrast diagnostic with a unique timestamp and orientation markers."""
    validate_dimensions(width, height)
    canvas = Image.new('RGB', (width, height), 'white')
    draw = ImageDraw.Draw(canvas)
    unit = max(1, min(width, height)//24)
    draw.rectangle((unit, unit, width-unit-1, height-unit-1), outline='black', width=unit)
    font = ImageFont.load_default(size=max(10, min(width//13, height//7)))
    small = ImageFont.load_default(size=max(8, min(width//30, height//18)))
    draw.text((3*unit, 3*unit), 'EPAPER TEST', font=font, fill='black')
    draw.text((3*unit, height//2), time.strftime('%Y-%m-%d %H:%M:%S'), font=small, fill='black')
    draw.text((3*unit, height//2+3*unit), 'TOP / LEFT', font=small, fill='black')
    for index in range(8):
        left = 3*unit + index * max(1, (width-6*unit)//8)
        right = 3*unit + (index+1) * max(1, (width-6*unit)//8)-1
        draw.rectangle((left, height-6*unit, right, height-3*unit),
                       fill='black' if index % 2 == 0 else 'white', outline='black')
    return canvas


def mono_bytes(path, width, height, preview=None):
    validate_dimensions(width, height)
    with Image.open(path) as original:
        image = ImageOps.exif_transpose(original).convert('RGBA')
        fitted = ImageOps.contain(image, (width, height), Image.Resampling.LANCZOS)
        canvas = Image.new('RGB', (width, height), 'white')
        canvas.paste(fitted, ((width-fitted.width)//2, (height-fitted.height)//2), fitted)
        # Pillow's mode 1 is MSB-first, white=1, row-padded with zero bits.
        mono = canvas.convert('L').convert('1', dither=Image.Dither.FLOYDSTEINBERG)
        if preview:
            preview.parent.mkdir(parents=True, exist_ok=True)
            mono.save(preview)
        return mono.tobytes()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['list', 'listen', 'send'])
    parser.add_argument('--port')
    parser.add_argument('--baud', type=int, default=115200)
    parser.add_argument('--seconds', type=float, default=5)
    parser.add_argument('--image', type=Path)
    parser.add_argument('--test-pattern', type=Path, help='Generate and send a timestamped diagnostic PNG')
    parser.add_argument('--preview', type=Path, help='Save the exact monochrome conversion as a PNG')
    parser.add_argument('--width', type=int)
    parser.add_argument('--height', type=int)
    parser.add_argument('--log', type=Path)
    parser.add_argument('--protocol', choices=['raw', 'imagetoepd36'])
    parser.add_argument('--handshake-timeout', type=float, default=10)
    args = parser.parse_args()
    if args.action == 'list':
        for p in list_ports.comports():
            print(p.device, p.description, p.hwid)
        return
    if not args.port:
        parser.error('--port is required')
    if args.seconds < 0 or args.handshake_timeout <= 0:
        parser.error('seconds must be nonnegative and handshake-timeout must be positive')
    if args.action == 'send':
        if not (args.width and args.height and args.protocol):
            parser.error('send requires --width, --height, and an explicit --protocol')
        if bool(args.image) == bool(args.test_pattern):
            parser.error('send requires exactly one of --image or --test-pattern')
        validate_dimensions(args.width, args.height)
        if args.protocol == 'imagetoepd36':
            imagetoepd36.handshake_packet(args.width, args.height)
            if args.baud != 115200:
                parser.error('ImageToEpd v3.6 requires 115200 baud')
        if args.test_pattern:
            args.test_pattern.parent.mkdir(parents=True, exist_ok=True)
            test_pattern(args.width, args.height).save(args.test_pattern)
            args.image = args.test_pattern
    payload = mono_bytes(args.image, args.width, args.height, args.preview) if args.action == 'send' else None
    report = {"action": args.action, "port": args.port, "baud": args.baud, "started": time.strftime('%Y-%m-%dT%H:%M:%S%z'), "refresh_confirmed": False, "transfer_complete": False, "status": "opening_port"}
    if payload is not None:
        report.update(width=args.width, height=args.height, mode='mono', encoding=args.protocol,
                      image=str(args.image.resolve()), payload_sha256=hashlib.sha256(payload).hexdigest(),
                      bytes_total=len(payload), bytes_sent=0)
    port = serial.Serial(port=None, baudrate=args.baud, timeout=.1, write_timeout=5, exclusive=True)
    # Do not deliberately pulse reset/boot pins during this diagnostic.
    port.dtr = False
    port.rts = args.protocol == 'imagetoepd36'
    port.port = args.port
    try:
        port.open()
        print(f'Opened {args.port} at {args.baud} baud, 8N1.', flush=True)
        report['before'] = receive(port, args.seconds if payload is None else 2)
        print('Received before sending:', json.dumps(report['before'], ensure_ascii=True), flush=True)
        if payload is not None:
            started = time.monotonic()
            if args.protocol == 'imagetoepd36':
                imagetoepd36.send(port, payload, args.width, args.height, report, args.handshake_timeout)
            else:
                report['status'] = 'sending_image'
                for offset in range(0, len(payload), 512):
                    chunk = payload[offset:offset+512]
                    count = port.write(chunk)
                    report['bytes_sent'] += count
                    if count != len(chunk):
                        raise RuntimeError(f'Incomplete serial write: {count}/{len(chunk)}')
                    # Mild pacing gives the receiver time between blocks.
                    time.sleep(.01)
            deadline = time.monotonic() + 10
            while port.out_waiting:
                if time.monotonic() > deadline:
                    raise TimeoutError('Serial output did not drain within 10 seconds')
                time.sleep(.05)
            report['elapsed_seconds'] = round(time.monotonic()-started, 2)
            report.update(transfer_complete=True, status='awaiting_visual_confirmation')
            print(f"Sent {report['bytes_sent']} bytes in {report['elapsed_seconds']} seconds. Waiting for device output…", flush=True)
            report['after'] = receive(port, args.seconds)
            print('Received after sending:', json.dumps(report['after'], ensure_ascii=True), flush=True)
            print('Transfer finished; only visual observation can confirm screen refresh for this protocol.', flush=True)
        else:
            report['status'] = 'listen_complete'
    except Exception as exc:
        report['error'] = str(exc)
        report['failed_at'] = report['status']
        report['status'] = 'failed'
        print(f'Upload/diagnostic failed: {exc}', file=sys.stderr, flush=True)
        return 1
    finally:
        port.close()
        if args.log:
            args.log.parent.mkdir(parents=True, exist_ok=True)
            args.log.write_text(json.dumps(report, indent=2, ensure_ascii=True)+'\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
