# ImageToEpd v3.6 protocol investigation

Status: recovered through static inspection of the official Windows .NET executable. Not yet validated against this connected board. The 2026-09-29 probe opened `/dev/cu.usbserial-2130` but received no response; it sent the handshake only and did not send image payload. See `logs/imagetoepd36-upload.json`.

Source: https://github.com/GoodDisplay/Forked-Software-ImageToEPD-for-E-paper-display/blob/main/ImageToEpd%20v3.6.exe

Executable SHA-256: `047a51f995cc6e01da161920b1c80c0b3a92377a3eae3e6ee507599b5c87be03`

The downloaded executable was inspected, not executed. Methods inspected: `SetPortProperty`, `Shake_hands_data`, `sp_DataReceived` callback, `Picture_data_send`, `GetPictureData`, `button_Pic_Open_Click`, and static initializer.

- Serial: 115200 8N1; RTS enabled, DTR left at .NET's default false.
- Request: 22 bytes, initially zero. Bytes 0–2: AA 55 E1. Byte 5: F0 for full refresh (F1 partial). Byte 6: model code; C4 corresponds to UC 800x480. Byte 7: 0 for monochrome/no red. Bytes 8–9 and 10–11: big-endian width and height. Byte 18: sum of bytes 0–17 modulo 256. Bytes 19–21: FF 0D 0A.
- Reply: 10 bytes beginning A0 50, ending FF. Byte 8 is sum of bytes 0–7 modulo 256. Byte 2=F1 accepts the handshake. Byte 3=80 reports SSD; the application's other branch identifies UC.
- A successful handshake triggers image sending. Our Python adapter rejects SSD replies because its currently implemented 800x480 profile is UC.
- Mono 800x480 data: row-major MSB-first, **black=1 and white=0**. The vendor explicitly inverts this size's normal white=1 bitmap.
- Transfer: blocks of 4096 image bytes each followed by 0D 0A; 100 ms delay after each full block. Send final remainder and 0D 0A, even for an empty remainder. For 48000 image bytes: 11 full blocks and a 2944-byte remainder.
- The recovered sender does not wait for per-block acknowledgment or a final refresh acknowledgment. A valid initial handshake still cannot prove the panel refreshed.

Only the 800x480 monochrome UC profile is implemented in `tools/imagetoepd36.py`. The actual panel model/resolution remains unconfirmed; 800x480 came from the user's browser settings, not hardware identification. Other model mappings and alternate scan formats exist in the vendor program and must be implemented based on the real panel.

Run the diagnostic with:

```sh
.venv/bin/python tools/serial_upload.py send --port /dev/cu.usbserial-2130 --image example-mountains.jpg --width 800 --height 480 --protocol imagetoepd36 --seconds 8 --log logs/imagetoepd36-upload.json
```

The browser's existing ESP32/raw profile is **not** this protocol. Do not use that profile for ImageToEpd desktop firmware. The v3.6 adapter is currently Python-only pending hardware validation.

## Vendor documentation and GitHub source search (2026-09-29)

- Official manual/download page: https://www.good-display.com/companyfile/1353.html
- The vendor-published v3.6 manual is now saved as `docs/vendor/ImageToEpd-v3.6-manual.pdf`. It is a 12-page Chinese manual dated 2022-02-09. PDF page 6 says the user must select the controller family based on the panel model (generally UC for GDEW; SSD for GDEH/GDEY/GDEM/GDEQ). Page 11 distinguishes successful serial connection from the required successful upload handshake. Pages 11–12 suggest checking board power, cable, and reconnecting the selected serial port. It does not publish byte-level protocol fields.
- Software repository: https://github.com/GoodDisplay/Forked-Software-ImageToEPD-for-E-paper-display — current tree contains v3.6/v3.7 EXEs, a PDF, video, and README; no C# project/source files.
- Upstream repository: https://github.com/smartboxchannel/Good-Display-Software — current `main` tree contains the v3.0 EXE, PDF, video, and README; no C# project/source files. The inspected history entry named `Create ImageToEPD` added a blank file, not source code.
- Firmware examples: https://github.com/gooddisplayepaper/E-paperExample_GoodDisplay — genuine C/C++ source for STM32, Arduino, ESP32/ESP8266, and Raspberry Pi examples.
- Inspected 800x480 firmware entry point: https://github.com/gooddisplayepaper/E-paperExample_GoodDisplay/blob/main/STM32/7.5/S-GDEW075T7_GUI-20200810/USER/main.c . It initializes the panel, displays a compiled image array, draws GUI examples, then stops in a loop. It does not initialize a serial image receiver or implement the ImageToEpd handshake in this entry point. The included `SYSTEM/usart/usart.c` is a generic CRLF-terminated UART receive routine, not the matching host-protocol implementation.
- The software repository links to https://www.good-display.com/product/418.html . That current product page describes GDU075R1/IL075RU 7.5-inch 800x480 black/white/red signage with an STM32F1 MCU, and currently offers ImageToUSB v4.0. This is a possible product family, not identification of the user's device.
- Related USB board: https://www.good-display.com/product/565.html (IL075U), with direct USB updates and multiple compatible 7.5-inch panels. Again, not confirmed as the attached hardware.

Finding: public documentation supports the distinction between C-array export and direct serial upload, and requires selecting the appropriate controller family. No matching board-side ImageToEpd v3.6 receiver source or formal byte-level protocol specification was found in the inspected resources. The Python protocol remains based on static inspection of the official EXE, not a verified receiver implementation. The connected device has not acknowledged it.
