# GDP075FU1 / ImageToUSB v4.0

Implemented in the browser on 2026-09-30 using static inspection of the user's `ImageToUSB v4.0.exe` from Downloads and the GDP075FU1 manual supplied by the user. The executable was inspected, not run. Unit tests and a simulated serial device validate the host implementation; a physical display refresh has not been verified.

## Sources and device specifications

- [Manufacturer's PDF](https://v4.cecdn.yun300.cn/100001_1909185148/EN-GDP075FU1.pdf), archived as [EN-GDP075FU1.pdf](vendor/EN-GDP075FU1.pdf). Revision V1.0, dated 2024-12-16. Page numbers below are PDF page numbers, including the cover.
- [Manufacturer's product page](https://www.good-display.com/product/640.html), linking both this manual and ImageToUSB v4.0.
- Executable SHA-256: `8fe1ee060cc4bb73767369dfc397dde87518b7a0b353f42591cbef2224bb5bc2`.
- Inspected methods: `SetPortProperty` (14), `GetPictureData` (22), `GetPictureDataRed` (23), `GetPictureData_4Color` (24), `button_Pic_Open_Click` (25), `Shake_hands_data` (27), `Picture_data_send` (28), static initializer (45), and the active `sp_DataReceived` callback (56).

| Property | Value in the manual | PDF page |
| --- | --- | --- |
| Model | GDP075FU1 | 2, 4 |
| Display | 7.5 inches; 800 × 480 pixels | 4 |
| Palette | Black, white, red, yellow; select **4-Color** | 4, 8 |
| Connection / power | Micro USB, USB 2.0, 5 V | 4–6 |
| Serial bridge | CH340; install its driver if no port appears | 6, 8 |
| MCU | STM32F103C6T6 | 5 |
| RAM / ROM / flash | 10 KB / 64 KB / “2M” (flash unit not specified) | 5 |
| Image refresh | Approximately 20 seconds | 5 |
| Refresh power | Less than 200 mW in this revision | 4 |
| Enclosure / weight | 196 × 138 × 12 mm; approximately 204 g | 4 |
| Operating environment | 0–40 °C; 40–70% humidity | 5 |
| Storage temperature | −25–70 °C | 5 |
| Desktop image inputs | BMP or JPG, 800 × 480 | 6 |
| Power-off behavior | Retains the displayed image without an internal battery | 4 |

The current product page lists a different refresh-power figure in its general specifications. The value above is attributed specifically to the supplied V1.0 PDF. The manual documents operation but does **not** publish the byte protocol: the fields below come from the executable. The manual's page 8 screenshot independently shows a 96,000-byte four-color transfer, matching the recovered encoding.

The web app defaults to this model's 800 × 480 four-color setting. It additionally decodes PNG and WebP locally, fits images to the canvas, and reduces them to the selected palette. The 2- and 3-color paths are available for other matching ImageToUSB v4.0 devices; GDP075FU1 should remain on 4 colors, including for black-and-white artwork.

## Serial configuration and handshake

115200 baud, 8 data bits, no parity, 1 stop bit, no flow control. `SetPortProperty` explicitly enables RTS. DTR is left false in the Windows program; the browser sets `requestToSend: true, dataTerminalReady: false` after opening.

The request is **12 bytes**, not the old ImageToEpd v3.6 22-byte request:

| Offset | Meaning |
| --- | --- |
| 0–2 | `AA 55 E1` |
| 3–4 | Big-endian **one-plane** size, `width × height / 8`: `BB 80` (48,000) for every supported color mode |
| 5 | Color count: `02`, `03`, or `04` |
| 6 | Resolution model: `C4` for 800 × 480 |
| 7 | `01` when the converted image contains red; otherwise `00` |
| 8 | Sum of bytes 0–7 modulo 256 |
| 9–11 | `FF 0D 0A` |

Four colors with red: `AA 55 E1 BB 80 04 C4 01 E4 FF 0D 0A`.
Four colors without red: `AA 55 E1 BB 80 04 C4 00 E3 FF 0D 0A`.
Monochrome: `AA 55 E1 BB 80 02 C4 00 E1 FF 0D 0A`.

The device reply is 10 bytes, starting `A0 50`, ending `FF`, with byte 8 equal to the sum of bytes 0–7 modulo 256. The active callback accepts command byte 2 equal to `F1`. Unlike the old v3.6 callback, it does not use byte 3 to select UC/SSD packing. The separate, unused `SerialDataAnalysis` method mentions `F2`; that is not the command that triggers image sending in the active receive callback.

The browser tolerates noise and fragmented/coalesced replies, validates the full frame and checksum, and waits at most 10 seconds. No image payload is sent without a valid `F1` reply. Timeout, stopped transfers, and serial failures require resetting/reconnecting before retrying. A successful read releases its lock without cancelling the input stream, allowing another handshake on the same connection.

## Pixel encoding

All implemented paths are 800 × 480, row-major, left-to-right and top-to-bottom, with high bits first.

| Mode | Payload | Encoding |
| --- | --- | --- |
| 2-Color | 48,000 bytes | One bit per pixel: black=0, white=1. No v3.6 inversion. |
| 3-Color | 96,000 bytes | 48,000-byte BW plane followed by red plane. Black=(0,1), white=(1,1), red=(1,0). |
| 4-Color | 96,000 bytes | Four pixels per byte: black=`00`, white=`01`, red=`11`, yellow=`10`. |

The app's palette indexes remain black=0, white=1, red=2, yellow=3; the v4 encoder maps those indexes to the wire codes above. This differs from the legacy ESP32 web tool, where red/yellow codes are reversed and the red pixel's BW-plane bit is different.

The vendor identifies colors from RGB thresholds. Our exact-palette output maps to those thresholds, while fitting, contrast, and dithering are browser features and need not produce the same artistic conversion as the Windows tool.

## Transfer framing

After handshake acceptance:

1. Write each full 4,096-byte image block.
2. Write the two bytes `0D 0A`, then pause 100 ms.
3. Write the final remainder and `0D 0A`.

For monochrome: 11 full blocks, remainder 2,944 bytes; 48,036 total wire bytes including handshake and delimiters.
For three/four colors: 23 full blocks, remainder 1,792 bytes; 96,060 total wire bytes including handshake and delimiters. The tri-color plane boundary has no additional header or delimiter.

The EXE has no final refresh acknowledgment. Completion in the UI means the serial writes completed. Keep the display powered and check its physical refresh; the PDF specifies about 20 seconds. The adapter does not send firmware, bootloader commands, or C source text. Exported C data contains the selected protocol's image payload only, not serial framing.

The vendor code contains other dimension mappings, but its long-image transfer branch explicitly checks for 800 × 480 and hardcodes 48,000/96,000-byte copies. This adapter therefore rejects other sizes and six-color mode rather than guessing their behavior.

## Verification

`npm test` covers encoding golden vectors, request fields/checksums, RTS/DTR, fragmented replies, invalid checksums, wrong reply commands, exact mono/tri/four payloads and delimiters, 100 ms pacing, repeated handshakes, timeout, stop, unplug/read failure, signal configuration failure, and C export. Existing conversion and raw transport tests remain in place.

Browser verification uses an isolated headless Chrome and a simulated `navigator.serial` device. It checks image import, defaults, invalid size blocking, profile switching, C export matching the full 96,000-byte transmitted payload, progress, stop, timeout, reconnect, and narrow-screen layout. Screenshots are stored under `logs/web-v4-*.png`.

On 2026-09-30 the OS port list contained no CH340 display. Hardware acceptance and physical refresh remain unverified. Historical `logs/imagetoepd36-*.json` and `logs/diagnostic-upload.json` describe the earlier, different protocol and must not be treated as v4.0 test results.
