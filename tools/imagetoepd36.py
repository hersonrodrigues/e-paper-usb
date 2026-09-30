"""ImageToEpd v3.6 800x480 UC mono transport, derived from vendor .NET IL.
Source: GoodDisplay/Forked-Software-ImageToEPD-for-E-paper-display,
ImageToEpd v3.6.exe: Shake_hands_data, Picture_data_send, GetPictureData,
SetPortProperty, and sp_DataReceived callback. No firmware flashing.
"""
import time


class HandshakeTimeout(TimeoutError):
    def __init__(self, received):
        self.received = received
        super().__init__(f'No valid ImageToEpd handshake reply. Received: {received.hex(" ") or "nothing"}')


def handshake_packet(width=800, height=480):
    if (width, height) != (800, 480):
        raise ValueError('This adapter currently implements only the inspected 800x480 UC monochrome profile.')
    packet = bytearray(22)
    packet[:3] = bytes.fromhex('aa55e1')
    packet[5] = 0xf0  # full refresh
    packet[6] = 0xc4  # UC 800x480 profile
    packet[7] = 0     # no red plane
    packet[8:10] = width.to_bytes(2, 'big')
    packet[10:12] = height.to_bytes(2, 'big')
    packet[18] = sum(packet[:18]) & 255
    packet[19:] = bytes.fromhex('ff0d0a')
    return bytes(packet)


def valid_reply(frame):
    return len(frame) == 10 and frame[:2] == b'\xa0\x50' and frame[9] == 255 and (sum(frame[:8]) & 255) == frame[8]


def await_handshake(port, timeout=5):
    end = time.monotonic() + timeout
    buffer = bytearray()
    captured = bytearray()
    while time.monotonic() < end:
        chunk = port.read(min(port.in_waiting or 1, 4096))
        buffer.extend(chunk)
        captured.extend(chunk)
        while len(buffer) >= 10:
            if valid_reply(buffer[:10]):
                frame = bytes(buffer[:10])
                del buffer[:10]
                if frame[2] == 0xf1:
                    return frame, bytes(captured)
            else:
                del buffer[0]
        if len(captured) > 16384:
            break
    raise HandshakeTimeout(bytes(captured))


def write_exact(port, data):
    count = port.write(data)
    if count != len(data):
        raise IOError(f'Incomplete serial write: {count}/{len(data)}')


def send(port, mono, width, height, report, handshake_timeout=5):
    packet = handshake_packet(width, height)
    if len(mono) != width*height//8:
        raise ValueError('Image size mismatch')
    port.reset_input_buffer()
    report.update(bytes_sent=0, wire_bytes_sent=0, handshake_valid=False,
                  transfer_complete=False, status='awaiting_handshake')
    report['handshake_tx'] = packet.hex(' ')
    print('Handshake TX:', report['handshake_tx'], flush=True)
    write_exact(port, packet)
    report['wire_bytes_sent'] = len(packet)
    try:
        frame, received = await_handshake(port, timeout=handshake_timeout)
    except HandshakeTimeout as exc:
        report['handshake_received_hex'] = exc.received.hex(' ')
        report['status'] = 'handshake_timeout'
        raise
    report['handshake_received_hex'] = received.hex(' ')
    report['handshake_rx'] = frame.hex(' ')
    report['handshake_valid'] = True
    report['controller_family'] = 'SSD' if frame[3] == 0x80 else 'UC'
    print(f"Handshake accepted: {frame.hex(' ')} ({report['controller_family']})", flush=True)
    if report['controller_family'] != 'UC':
        raise ValueError('Device reports SSD controller; aborting UC image transfer.')
    # GetPictureData inverts BW values at 800x480; source mono has white=1.
    payload = bytes(b ^ 255 for b in mono)
    report['status'] = 'sending_image'
    full, remainder = divmod(len(payload), 4096)
    for block in range(full):
        data = payload[block*4096:(block+1)*4096]
        write_exact(port, data)
        write_exact(port, b'\r\n')
        report['bytes_sent'] += len(data)
        report['wire_bytes_sent'] += len(data)+2
        time.sleep(.1)
    if remainder:
        write_exact(port, payload[full*4096:])
    # Vendor writes the final delimiter even when remainder=0.
    write_exact(port, b'\r\n')
    report['bytes_sent'] += remainder
    report['wire_bytes_sent'] += remainder+2
