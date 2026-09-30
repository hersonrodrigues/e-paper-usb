# Validation — 2026-09-30

Android version **1.2.0**, version code **4**. The source includes the Android USB OTG app, a separate browser application, and historical Python diagnostic tools.

## Automated checks

| Check | Result |
| --- | --- |
| Android `testDebugUnitTest` | 33 passed, 0 failed or skipped |
| Android `lintDebug` | 0 errors, 6 warnings |
| Android `assembleDebug` | Debug APK built successfully on macOS |
| Translation catalog validation | 25 locale variants, 77 strings each; complete keys, matching format arguments and consistent Android/Gradle registration |
| JavaScript `npm test` | 33 passed, including continuous RX, ASCII diagnostics, stale ACK rejection, refresh waiting period and stream cleanup |
| Python unittest discovery | 7 passed in the previous validation; diagnostic code unchanged |

The Android suite covers the recovered protocol vectors, palette packing, checksums, fragmented/concatenated ACKs, invalid replies, timeouts, disconnects, cancellation, concurrent operations, USB permission callbacks, reconnects, incomplete-session recovery, image fitting, EXIF orientation, transparency and preview/payload consistency. Transports in these tests are simulated.

Localization tests verify all supported variants, regional fallbacks, ordered language preferences, native digit formatting, and preservation of the prepared image during language changes, including switching to Arabic. Arabic, Persian and Urdu controls mirror while the image preview remains left-to-right. The build filters library resources to supported languages so an unsupported language present only in a dependency cannot outrank the user's next supported language.

Lint warnings concern the Android 13+ `localeConfig` attribute, newer dependency/Gradle versions, and plural suggestions for the fixed 96000-byte payload status (duplicated English fallback/explicit resources). Dependencies remain pinned to the versions that were tested.

The AGP 8.10 `UnusedTranslation` warning for Indonesian is suppressed: Android resources use legacy `in`, while modern app-language settings require `id` in `localeConfig`. The catalog validator checks both registrations explicitly. A trial using `in` in `localeConfig` rendered Indonesian correctly but omitted it from the emulator's language picker; the final build uses `id` there. Robolectric does not populate the application's locale-config metadata, so the language picker is verified on the emulator instead.

## Android interface and installation

- The localized APK was installed on an Android emulator and on a Pixel 10 using wireless ADB.
- Version 1.2.0 emulator checks cover Arabic layout and color-chart text, Tamil text wrapping, Indonesian selection, and Android's app-language picker. Width/height labels are isolated left-to-right so RTL text cannot reverse their order. The emulator's temporary app-language override is cleared after testing.
- Previous emulator checks confirmed German labels, French selection after an unsupported first preference, and readable Hindi and Traditional Chinese layouts.
- The app uses device language settings by default, with per-app language selection available in Android 13+ settings. Existing prepared image bytes are preserved when the UI language changes.
- Detailed local test reports, screenshots and device identifiers are excluded from Git.

## Physical hardware status

The CH340 converter (`1A86:7523`) was detected on the Pixel through USB OTG, and version 1.0.1 reached the app's USB-ready state after the permission callback fix. This confirms detection and serial-port opening in that session.

**The Android implementation has not yet validated a real ImageToUSB v4.0 ACK, a complete image transmission or a physical e-paper refresh.** The browser later recorded one real F1 ACK, as detailed below; device-side receipt of a complete image and physical refresh remain unverified. Color codes, orientation, power stability and refresh timing still require the physical test in the [Android README](../android/README.md#roteiro-do-primeiro-teste-físico). The localized build has not sent pixels automatically.

## Browser diagnosis

The local web app loaded and produced the expected 800 × 480, 96000-byte preview. Inspection of the active browser session found two writes using the separate ESP32 raw profile. This is not the GDP075FU1 v4.0 protocol. The session was disconnected and switched to **GDP075FU1 · ImageToUSB v4.0**, preserving the selected image. No new transfer was started during diagnosis. Power-cycle the display before reconnecting and testing the correct profile.

A later user-triggered browser transfer recorded:

- 12:36:08: header `AA 55 E1 BB 80 04 C4 01 E4 FF 0D 0A`; valid reply `A0 50 F1 00 00 00 00 00 E1 FF`.
- 12:36:16: host reported transfer finished; physical refresh not verified.
- 12:36:17: another handshake started, one second after the previous writes completed.
- 12:36:27: handshake timeout; the received tail decodes to `7926fe:15\r\n81471fe:16\r\n82697fe:17\r\n82697The Endlen=8269710fe:0\r\n`.

This establishes a successful initial handshake and an immediate repeat request during the documented refresh window. It suggests the display was still busy; the text alone cannot prove that diagnosis, establish the meaning of the reported length or confirm that the first image arrived intact.

The browser now drains RX throughout the connection and enforces a 25-second waiting period after successful writes. It logs readable device text alongside hex and can export the recent diagnostic log locally. All 33 JavaScript tests pass. In a separate browser test with simulated serial input, the UI sent exactly 96000 image bytes in 24 blocks, kept reading a delayed ASCII message after the last block, disabled Send with a countdown, enabled it again after the wait and released both stream locks on disconnect. The downloaded diagnostic text contained the header, ACK, post-transfer RX and wait completion. This is simulated evidence, not a new physical-device test.

## Repeat the checks

From the repository root:

```sh
python3 android/tools/check_locales.py
android/tools/build-macos.sh
npm test
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements.txt
.venv/bin/python -m unittest discover -s tests -p 'test_*.py'
```

Configure Android Studio's SDK and JDK first as described in the Android README. The APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`. APKs, signing keys, caches and machine-specific SDK paths are not committed.
