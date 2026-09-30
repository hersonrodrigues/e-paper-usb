# Browser application

[← Project overview](../README.md)

Run the commands below from the repository root.

A local web app for fitting an image to an e-paper display, previewing its palette, exporting C image data, and sending pixels directly over USB serial.

The default is **GDP075FU1 / ImageToUSB v4.0: 800 × 480, black/white/red/yellow**. This matches the manufacturer's manual supplied by the user. The browser adapter was recovered from the user's `ImageToUSB v4.0.exe`; it implements the 12-byte handshake, four-color encoding, and paced image transfer. Automated tests and a simulated device validate the host implementation; a physical display refresh remains unverified.

## Run and send an image

Install Node.js 20 or newer, then run:

```sh
npm start
```

Open **http://localhost:5173** in desktop Chrome or Edge. No npm dependencies are needed. On Windows, double-click `start-windows.cmd` if preferred. Keep the terminal open while using the app.

1. Connect GDP075FU1 using a Micro USB **data** cable. Close ImageToUSB and other programs using its serial port. On Windows, install the CH340 driver if no COM port appears.
2. Choose an image or try the built-in sample. Leave the canvas at **800 × 480** and colors at **black / white / red / yellow**, including for black-and-white artwork on GDP075FU1.
3. Adjust fit, rotation, contrast, and dithering. Check the preview.
4. Leave **GDP075FU1 · ImageToUSB v4.0** selected under upload software.
5. Click **Choose serial port**, select the device's COM/USB serial port, then **Send image**.
6. The app waits for a valid handshake before sending any image bytes. After transfer, keep USB connected and allow approximately **20 seconds** for the physical display to refresh.

Successful serial writes do not prove the screen refreshed: this protocol has no final refresh acknowledgment. If a transfer is stopped, fails, or times out, reset/reconnect the display before retrying. Stop cancels a handshake wait or pauses between image writes. If an OS serial write stalls, unplug/reset the board to release it.

Only an 800 × 480 canvas is supported by the v4.0 adapter. Its 2- and 3-color settings support other matching v4.0 devices; the supplied GDP075FU1 manual specifically requires **4-Color**. Six-color mode is unavailable with this profile. Choose **Export only** or the separate ESP32 profile for other conversion formats.

Image processing happens locally. The server binds only to the loopback interface; images are not sent to a service. PNG, JPEG, WebP, and BMP are supported when the browser can decode them, with 25 MB / 50 megapixel input limits. Convert HEIC, RAW, or SVG first. Safari and Firefox can convert/export; USB upload uses [Web Serial in desktop Chrome/Edge](https://developer.chrome.com/docs/capabilities/serial).

## GDP075FU1 data and protocol

- [Collected specifications (JSON)](gdp075fu1-specs.json)
- [Archived vendor manual](vendor/EN-GDP075FU1.pdf), V1.0, dated 2024-12-16
- [Protocol, packet fields, encoding, and source evidence](imagetousb40-protocol.md)

The supplied manual specifies a 7.5-inch four-color display, STM32F103C6T6 MCU, CH340 serial connection, 5 V Micro USB power, and an approximately 20-second refresh. It documents the Windows workflow; the byte-level transport comes from static inspection of the EXE.

The browser uses 115200 baud, 8N1, RTS=true, DTR=false. It sends a 12-byte request containing one-plane byte count, color mode, model code, red flag, and checksum, then validates a 10-byte device reply. Image payload is written in 4,096-byte blocks followed by CRLF, with 100 ms pauses after full blocks.

| ImageToUSB v4.0 mode | Payload | Encoding |
| --- | --- | --- |
| Black/white | 48,000 bytes | Row-major, MSB first; white=1, black=0 |
| Black/white/red | 96,000 bytes | BW then red plane; white=(1,1), black=(0,1), red=(1,0) |
| Black/white/red/yellow | 96,000 bytes | Four pixels per byte; black=0, white=1, red=3, yellow=2 |

The exported C array uses the selected profile's encoding and labels it in the file header. It contains image data only, without the serial handshake or block delimiters. It is not a flashable program. The PNG export contains the preview palette; actual panel colors will differ.

## Separate ESP32 raw profile

The optional **Good Display ESP32 · USB web-tool firmware** profile follows [Good Display's USB web tool](https://www.e-paper-display.com/usb2epd.html), inspected on 2026-09-29. It targets compatible ESP32E6-E01-family firmware and requires confirming its dimensions and colors. CH340 alone does not identify a device's protocol.

This profile uses 115200 8N1 and raw image data in 512-byte writes, with no handshake or refresh acknowledgment. It is a different protocol from ImageToUSB v4.0.

| Raw web-tool / export-only mode | Encoding |
| --- | --- |
| Black/white | Row-major, MSB first; white=1, black=0; row stride `ceil(width/8)`, zero padding |
| Black/white/red | BW then red plane; white=(1,1), black=(0,1), red=(0,0) |
| Four colors | High bits first; black=0, white=1, red=2, yellow=3; width divisible by 4 |
| Six colors | One byte per pixel in clockwise-rotated traversal; black=0x00, white=0xff, red=0x4c, yellow=0xe2, green=0x96, blue=0x1d |

These formats are firmware-specific, not universal panel buffers. Seven-color panels are not supported.

## Development and verification

```sh
npm test
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements.txt
.venv/bin/python -m unittest discover -s tests -p 'test_*.py'
```

The JavaScript tests cover palette conversion and known byte patterns; v4.0 handshake fields, checksum validation and split replies; complete mono/tri/four transfers, delimiters and timing; timeout, cancellation, read/write failures and concurrent operations; and C exports. Serial tests use simulated ports, not hardware.

Browser verification covers actual image import, GDP075FU1 defaults, profile switching, unsupported dimensions, C export matching all 96,000 transmitted payload bytes, progress, cancellation, a 10-second handshake timeout, reconnect, and mobile layout. Local screenshots and device diagnostics are excluded from Git.

- [src/imagetousb40.js](../src/imagetousb40.js): recovered v4.0 packing, handshake and reply parser.
- [src/serial.js](../src/serial.js): Web Serial configuration, connection lifecycle and transfer.
- [src/conversion.js](../src/conversion.js): palette reduction, raw packing and C generation.
- [src/app.js](../src/app.js): local image loading, transforms, preview and upload UI.
- [server.mjs](../server.mjs): dependency-free local HTTP server with an explicit asset allowlist.

## Historical Python diagnostics

`tools/serial_upload.py` supports `raw` and the older `imagetoepd36` adapter only. **It does not implement ImageToUSB v4.0; use the browser for GDP075FU1.** The old v3.6 protocol has a different 22-byte handshake and different 800 × 480 mono polarity.

```sh
.venv/bin/python tools/serial_upload.py list
```

The 2026-09-29 local logs record an unsuccessful v3.6 attempt with no handshake reply and no image transferred. They are retained for context, not evidence about the new v4.0 implementation. See [the earlier investigation](imagetoepd36-protocol.md). The CH340 was subsequently detected on a Pixel 10 through USB OTG and the Android app opened its serial port. Physical display transfer and refresh remain unverified; see [current validation status](validation.md).
