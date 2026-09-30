# E-paper USB — project and agent reference

Use this guide when developing, diagnosing, translating, testing or releasing this repository. It records project-specific decisions and evidence; it does not authorize a hardware transfer or publication by itself. Follow the user's current task and existing authorization.

**Maintainer:** Herson Santos · **Repository:** [hersonrodrigues/e-paper-usb](https://github.com/hersonrodrigues/e-paper-usb) · **License:** [MIT](LICENSE), Copyright (c) 2026 Herson Santos.

**Baseline reviewed: 2026-09-30.** Version numbers, test counts and hardware observations below describe that baseline. Check the referenced source files and [validation record](docs/validation.md) before treating them as current.

The reusable skill entry point is [skills/epaper-usb/SKILL.md](skills/epaper-usb/SKILL.md). It is repository-scoped and relies on this checkout; neither file installs a global skill or changes an agent's permissions.

## Contents

- [Product and implementation boundaries](#product-and-implementation-boundaries)
- [Source map](#source-map)
- [USB protocol contract](#usb-protocol-contract)
- [Android development](#android-development)
- [Browser development](#browser-development)
- [Images and localization](#images-and-localization)
- [Diagnosis and physical testing](#diagnosis-and-physical-testing)
- [Validation baseline](#validation-baseline)
- [Releases and attribution](#releases-and-attribution)
- [Evidence and maintenance](#evidence-and-maintenance)

## Product and implementation boundaries

Prepare a photo locally and send its converted pixels over USB serial to a compatible e-paper controller. The target is the **Good Display GDP075FU1 USB model, 800 × 480, black/white/yellow/red**. The [example product page](https://www.good-display.com/product/640.html) also lists Wi-Fi models; they are not the USB target. CH340 VID/PID identifies the serial bridge, not the display model or firmware.

| Implementation | Runtime and transport | Scope |
| --- | --- | --- |
| Android app | Android 8.0+ / API 26+, USB host/OTG; `usb-serial-for-android` 3.11.0 with CH34x | Four-color 800 × 480 ImageToUSB v4.0; 25 locale variants |
| Browser app | Desktop Chrome/Edge, built-in Web Serial, localhost or HTTPS; Node.js 20+ serves files | Default v4.0 profile, conversion/export and a separate optional ESP32 raw profile |
| Historical Python tools | Separate virtual environment with Pillow 12.3.0 and pyserial 3.5 | Earlier raw/v3.6 diagnostics; not the current v4.0 backend |

The browser implements the wire protocol in project-owned JavaScript and has **no npm dependencies or external serial library**. Keep that property for ordinary browser changes. The Android driver library is an intentional, pinned dependency; do not claim the whole repository is dependency-free. A Python backend is not needed by either app.

The Mac develops and builds the Android APK; the phone hosts the display through OTG. The desktop browser can instead host a display connected directly to the computer. This is not MTP file copying, Wi-Fi transfer, EXE emulation or firmware flashing. Do not install the Windows CH340 `SETUP.EXE` on macOS or Android. A browser still needs OS serial-driver support and the user's port permission.

## Source map

Paths below are relative to the repository root. Read only the implementation relevant to the task.

| Area | Entry points |
| --- | --- |
| Browser UI and local image workflow | [index.html](index.html), [src/app.js](src/app.js), [src/style.css](src/style.css) |
| Browser palette and exports | [src/conversion.js](src/conversion.js) |
| v4.0 packing, handshake and persistent reader | [src/imagetousb40.js](src/imagetousb40.js) |
| Browser serial lifecycle, blocks and refresh wait | [src/serial.js](src/serial.js) |
| Local HTTP server | [server.mjs](server.mjs), [package.json](package.json) |
| Android UI/state | [MainActivity.java](android/app/src/main/java/com/santos/epaperusb/MainActivity.java), [AppModel.java](android/app/src/main/java/com/santos/epaperusb/AppModel.java), [UiText.java](android/app/src/main/java/com/santos/epaperusb/UiText.java) |
| Android permission and port lifecycle | [UsbController.java](android/app/src/main/java/com/santos/epaperusb/usb/UsbController.java) |
| Android protocol | [EpaperProtocol.java](android/app/src/main/java/com/santos/epaperusb/protocol/EpaperProtocol.java), [AckParser.java](android/app/src/main/java/com/santos/epaperusb/protocol/AckParser.java), [TransferEngine.java](android/app/src/main/java/com/santos/epaperusb/protocol/TransferEngine.java) |
| Android image processing and immutable result | [image/](android/app/src/main/java/com/santos/epaperusb/image/) |
| Android diagnostic export | [DiagnosticLog.java](android/app/src/main/java/com/santos/epaperusb/DiagnosticLog.java) |
| Localization source and generators | [android/tools/locales/](android/tools/locales/), [write_locales.py](android/tools/write_locales.py), [check_locales.py](android/tools/check_locales.py) |
| Tests | [JavaScript/Python tests](tests/), [Android tests](android/app/src/test/java/com/santos/epaperusb/) |
| Original engineering evidence | [android/evidence/](android/evidence/), [protocol notes](docs/imagetousb40-protocol.md) |

## USB protocol contract

Read [the detailed v4.0 analysis](docs/imagetousb40-protocol.md) before changing packet bytes, framing, colors or timing. Its source is static inspection of the user's ImageToUSB v4.0 executable and the preserved dossier. The manual establishes product behavior, not the complete byte protocol.

### Serial and header

Use **115200 baud, 8 data bits, no parity, 1 stop bit, no automatic flow control, RTS=true, DTR=false**. Android filters **VID `0x1A86`, PID `0x7523`**. The browser uses the port selected by the user; it does not establish panel identity from that selection.

For four-color 800 × 480, the header is 12 bytes:

```text
AA 55 E1 BB 80 04 C4 RR SS FF 0D 0A
```

| Field | Meaning |
| --- | --- |
| `AA 55 E1` | Request prefix and command |
| `BB 80` | **48000**, the one-plane size `800 × 480 / 8`; not the four-color payload size |
| `04` | Four colors |
| `C4` | Resolution/model code used by this path |
| `RR` | `01` if the prepared image contains red; otherwise `00` |
| `SS` | Sum of bytes 0–7 modulo 256; `E4` with red, `E3` without red |
| `FF 0D 0A` | Header terminator |

### ACK and payload

- Wait at most **10 seconds** for a complete **10-byte** reply: prefix `A0 50`, command `F1` at byte 2, checksum at byte 8 equal to the sum of bytes 0–7 modulo 256, and `FF` at byte 9.
- Accumulate fragmented reads, tolerate noise and concatenated frames, and bound buffer memory. Do not require a read to contain exactly one reply.
- Bytes 3–7 are not interpreted as panel identity in this path. `F2`, an invalid checksum, a partial frame or printable diagnostic text does **not** authorize image bytes.
- Four-color wire codes are black=`00`, white=`01`, yellow=`10`, red=`11`. Pack four pixels per byte, most significant pair first, rows top-to-bottom and pixels left-to-right. Black/white/yellow/red is the golden byte `1B`.
- The payload is exactly **96000 bytes**: **23 blocks of 4096**, then **1792**. Append `0D 0A` after every block, including the last. Pause **100 ms after each full block**.
- `0D 0A` is a fixed delimiter, not a calculated CRC. Totals are **96048** bytes for blocks plus delimiters, **96060** including the header.
- Connecting never sends pixels automatically. Start only through an explicit Send action. Do not retry a partial transfer automatically, synthesize an ACK, call `DiscardOutBuffer` after sending, or invent an abort/refresh command.
- Successful host writes are not proof of complete device-side receipt or a physical refresh. No final refresh acknowledgment is confirmed in the recovered path.

The browser's internal palette order is black/white/**red/yellow** (`0,1,2,3`); its v4.0 encoder maps those last two indexes to wire codes `3,2`. Android uses final ARGB pixels. Preserve wire bytes and preview colors rather than copying palette indexes between implementations.

The browser also has v4.0 mono/tri-color paths for other matching devices. Their framing/encoding is documented in the protocol notes and tests. GDP075FU1 remains **four-color even for black-and-white artwork**. The optional ESP32 raw profile and historical ImageToEpd v3.6 protocol are different formats; do not use them as automatic fallbacks.

## Android development

Package: **`com.santos.epaperusb`**. Baseline version: **1.2.0**, version code **4**. Sources of truth are [app/build.gradle](android/app/build.gradle), [build.gradle](android/build.gradle), [settings.gradle](android/settings.gradle) and [the wrapper properties](android/gradle/wrapper/gradle-wrapper.properties).

| Build component | Pinned/tested baseline |
| --- | --- |
| Compile / target SDK | 36 / 36 |
| Minimum SDK | 26 |
| Gradle / Android Gradle Plugin | 8.12 / 8.10.1 |
| JDK used on macOS | Android Studio JBR 21; Java source/target 17 |
| SDK Build Tools used | 35.0.0 |
| USB serial / ExifInterface | 3.11.0 / 1.4.2 |
| AndroidX Activity / Lifecycle | 1.13.0 / 2.10.0 |
| Unit tests | JUnit 4.13.2, Robolectric 4.16.1 |

Configure the Android SDK in Android Studio. From the **repository root**:

```sh
python3 android/tools/check_locales.py
android/tools/build-macos.sh
```

The script runs `testDebugUnitTest lintDebug assembleDebug`. It honors configured `JAVA_HOME` and `ANDROID_HOME`; its defaults are Android Studio's bundled JDK and `$HOME/Library/Android/sdk`. The first build needs internet for dependencies. Use the committed Gradle wrapper; preserve its distribution checksum.

Output: **`android/app/build/outputs/apk/debug/app-debug.apk`**. This is a debug APK, not a Play Store release. The standalone Java files in `android/evidence/` are historical evidence, not a Gradle project. See [the Android guide](android/README.md) for direct Gradle commands and installation.

### Lifecycle invariants

- Declare USB host support. Keep USB I/O on its dedicated executor and UI updates on the main thread. Serial writes have a **5000 ms timeout**; ACK reads use slices up to **250 ms** within the deadline.
- Preserve the USB permission fix: Android fills result extras into a scoped mutable `PendingIntent` on API 31+. The callback uses a private receiver, package restriction, random action and request epoch. Confirm access using `UsbManager.hasPermission`; ignore stale/wrong-device replies. Recheck pending permission on resume and through the existing button.
- Send requires both a prepared image and USB `READY`. Accepting the permission dialog alone does not satisfy those conditions.
- Persist the incomplete-transfer marker **before the header**. A failure closes the session and requires physical power reset; clear the marker only after completed writes or the explicit reset confirmation. Do not silently clear it to enable Send.
- Leaving the foreground closes the port/cancels active transfer; a configuration change preserves the ViewModel. There is no background upload service.
- The manifest has no internet permission. Keep photo processing and diagnostics local; do not add telemetry as part of USB fixes.

**Implementation difference:** Android 1.2.0 reads while waiting for the initial ACK and returns to `READY` after successful writes. It does not yet implement the browser's persistent reader or enforced 25-second post-send wait. Do not describe those browser changes as included in the APK.

## Browser development

From the repository root:

```sh
npm start
```

Open **http://localhost:5173** in desktop Chrome or Edge. `node server.mjs` is equivalent; no `npm install` is needed. `start-windows.cmd` is a Windows launcher. Safari/Firefox can use conversion/export, but this app's USB path requires Web Serial. It is not the Android OTG implementation.

Run the dependency-free test suite with:

```sh
npm test
```

Preserve these behaviors when editing the browser:

- `server.mjs` binds to `127.0.0.1`, serves only an explicit file allowlist, uses a same-origin CSP and disables caching. Add newly required modules to that allowlist; do not expose the repository, logs, SDK configuration or credentials through a general file server.
- The UI explicitly passes the selected protocol into `SerialConnection.connect`. Its class-level default is the legacy raw profile; do not accidentally rely on that default in v4.0 callers.
- One `ImageToUSB40Input` reader drains RX while connected, including during pixels and refresh. Arm the handshake wait before writing the header. An ACK consumed while idle must not authorize a later upload.
- On disconnect/failure, cancel the reader, await its loop, release read/write locks, then close the port. A read error must stop subsequent pixel blocks. Preserve rejection handling for a header write that fails while an ACK promise is pending.
- After completed v4.0 writes, enforce `DISPLAY_SETTLE_MS = 25000` in both transport and UI. The manual estimates roughly 20 seconds; the extra time is a host-side margin, not a device-ready signal. The current page retains the wait across disconnect; a reload is not persistent recovery state.
- Snapshot outgoing bytes before awaiting I/O. Disable Send during conversion, transfer, a tainted session or the refresh wait. Conversion/preview/export must continue to describe the same prepared image.
- Web Serial has no application-level write timeout in this implementation. Cancellation is checked between writes; an OS write that stalls may require unplugging/resetting the device. Do not present it as the Android 5-second write timeout.
- Log header TX, RX hex/readable text and outcomes. The browser retains 200 recent log entries, shows the last 50 and limits each displayed RX sample to 128 bytes. **Save diagnostic log** exports locally; do not log outgoing pixel buffers or upload diagnostics.

The server does not hot-reload JavaScript. Reload the page after source changes to test the new code, accounting for the selected image/connection being lost. Use simulated serial input for automated UI tests; do not connect a fake-ACK implementation to a real device.

## Images and localization

The physical target requires 800 × 480 final pixels. Preserve proportional crop or white borders, rotation, a white transparency background and optional Floyd–Steinberg dithering. The final preview and packed payload must come from the same pixel result.

Android uses an immutable `PreparedImage`, normalizes all eight EXIF orientations, caps input copies at 32 MiB and samples decode size to approximately 3 million pixels / 4096 per side. The browser currently limits file size to 25 MiB and checks 50 million pixels **after browser image decoding**; it does not have the same bounded decode strategy as Android. Do not claim identical image-memory guarantees. Changing language or screen direction must not mirror/recompute a prepared payload silently.

Android follows device language preferences, falls back to English and exposes per-app language settings on Android 13+. Browser UI is currently English. Baseline Android locale tags:

```text
en, pt-BR, pt-PT, es, fr, it, de, ko, ja, hi, zh-Hans, zh-Hant,
ar, bn, ru, id, tr, vi, th, ur, fa, pl, nl, uk, ta
```

To update translations, edit UTF-8 `key=value` catalogs in `android/tools/locales/`, then run:

```sh
python3 android/tools/write_locales.py
python3 android/tools/check_locales.py
```

Do not hand-edit generated `strings.xml` files without updating their catalogs. When adding a language, update `FOLDERS` in the generator, `resourceConfigurations` in `app/build.gradle`, and the README's language summaries. The generator writes `locales_config.xml` and the explicit English resource copy. Preserve resource IDs and formatted arguments such as `%1$s`.

Non-obvious locale requirements:

- English uses both `values` and `values-en` so ordered language preferences resolve correctly.
- Portuguese Portugal uses `values-pt`; Brazil uses `values-pt-rBR`. Chinese uses `values-b+zh+Hans` / `values-b+zh+Hant`.
- Indonesian is **`id` in the catalog and localeConfig**, **`values-in` in resources**, and **`in` in Gradle filters**. Using `in` in localeConfig made Indonesian disappear from the tested system language picker. A narrowly documented manifest Lint suppression covers this AGP alias mismatch; the catalog checker verifies registrations.
- Arabic, Persian and Urdu controls use RTL. Keep image/pixel order LTR, isolate dimensions so 800 × 480 is not reordered, and preserve `StaticLayout` bidi shaping for color-test labels.
- `UiText` stores resource IDs/arguments rather than pretranslated state. Locale changes preserve the prepared image. A generated color chart changes its embedded labels only when the user creates it again.
- Baseline: 25 locale variants and 77 strings each. Check the generator/checker for current counts instead of hard-coding those counts into new features.

## Diagnosis and physical testing

Begin with the current profile, dimensions, color mode, actual header/reply and timestamps. Read the existing activity log before disconnecting, reloading or replacing a photo.

| Symptom | Evidence to check / next step |
| --- | --- |
| Android permission accepted but Send disabled | Check `UsbManager.hasPermission`, current request epoch, USB state and whether image preparation completed. Preserve the permission fix; use the existing authorization recheck. |
| Browser serial button unavailable | Check desktop Web Serial support and localhost/HTTPS. Conversion/export can still work. |
| Port opens but no handshake | Check selected port, 115200 8N1 / RTS / DTR, power, another program holding the port, and the exact v4.0 header. Do not send pixels after timeout. |
| First upload accepted, immediate second upload times out | Compare send times with the refresh interval. Preserve the 25-second wait and continuous input reader; inspect fresh diagnostics after power reset. |
| Reply contains `fe:...` or `The Endlen=...` | Decode it as ASCII diagnostic output, not an ACK. It does not prove completion, identify firmware or establish what the reported number means. |
| Raw profile reports success but no refresh | A completed raw write is not compatibility evidence. GDP075FU1 should use v4.0; reset power after a wrong/partial transfer before retrying. |
| Wrong colors or rotation | Compare final preview and known packing vectors, then inspect the physical color/orientation pattern. Do not swap wire colors solely to make a screenshot look better. |

A real browser log showed a valid F1 ACK at **12:36:08**, completed host writes at **12:36:16**, and another header at **12:36:17**. The second attempt timed out with text including `The Endlen=82697`. This supports investigating a busy device and queued diagnostics; it does not prove that the first 96000-byte payload arrived intact. The browser changes were subsequently verified with simulated streams, not another physical transfer. See [the full validation record](docs/validation.md).

For an authorized hardware test: verify the physical model label, power-cycle after an incomplete session, connect one matching controller using a data cable/OTG as appropriate, choose the color/orientation test and send once. Record the real header, ACK, 24 blocks and outcome. Keep power on and compare the physical screen to the preview before declaring success. No automatic retry after failure. Do not infer a completed screen update from the emulator, a checksum fixture, an installed APK or a host progress bar.

For Android debugging, list currently available ADB devices and select the intended device explicitly; wireless addresses and serial names are session-specific. A phone's USB port hosts the display during OTG tests, so Wi-Fi ADB is useful. Debugging authorization or pairing must already be available. Do not reuse an old serial path or alter unrelated phone settings to make a test pass.

## Validation baseline

Recorded results, not a claim that tests ran during every documentation edit:

| Check | Recorded result |
| --- | --- |
| Android unit tests | 33 passed |
| Android Lint | 0 errors, 6 warnings |
| Android debug build | 1.2.0 / code 4 built and installed on Pixel 10 and emulator |
| Locale checker | 25 variants, 77 strings; complete registrations and formatting arguments |
| Browser JavaScript tests | 33 passed after continuous-RX/refresh-wait changes |
| Browser simulation | 96000 bytes / 24 blocks, post-send RX, countdown, re-enable, local log export and lock release verified |
| Historical Python tests | 7 passed in earlier validation; not evidence about v4.0 hardware |
| Physical device | Android USB permission/port opening and one browser F1 ACK observed; complete device receipt and screen refresh unverified |

The six Android Lint warnings concern the Android 13+ locale configuration, newer dependency/Gradle versions and plural suggestions for fixed-size status text. See the current report before assuming the count or causes remain unchanged. Do not suppress new errors to reproduce an old count.

For browser behavior/protocol changes, run `npm test` and exercise the affected UI path. For Android logic/resources, run the locale checker as applicable and the Gradle test/Lint/build command above. Do not rebuild the APK for a browser-only or documentation-only change. Use meaningful regression cases: known pixel/checksum vectors, fragmented/bad/late ACKs, no pixels without ACK, cancellation, disconnect, input draining, retry blocking and preview preservation.

Only when working on the historical Python tools:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r tools/requirements.txt
.venv/bin/python -m unittest discover -s tests -p 'test_*.py'
```

Those Python dependencies do not belong in the browser startup path. `tools/identify_serial.py` is an explicit UART bootloader identification probe, not normal app connection logic; do not run it merely because a photo transfer failed.

## Releases and attribution

The public Android download is delivered through **GitHub Releases**, not an APK committed into Git history. Keep the direct download and installation instructions at the top of [README.md](README.md) and [android/README.md](android/README.md).

Baseline published artifact:

- [Release v1.2.0](https://github.com/hersonrodrigues/e-paper-usb/releases/tag/v1.2.0)
- [Epaper-USB-1.2.0-debug.apk](https://github.com/hersonrodrigues/e-paper-usb/releases/download/v1.2.0/Epaper-USB-1.2.0-debug.apk)
- [SHA-256 file](https://github.com/hersonrodrigues/e-paper-usb/releases/download/v1.2.0/Epaper-USB-1.2.0-debug.apk.sha256)

The v1.2.0 tag targets `cf4e118220b8a5540f36887e8129091580bc801a`, the tested Android source. Later main-branch changes include browser fixes and licensing/docs; downloading that tag's source is not equivalent to current `main`. Never silently replace an existing published APK with different bytes under the same version.

When a new release is requested:

1. Update Android `versionName` and increment `versionCode` for an Android build change. Confirm package ID, minimum SDK and output metadata; do not confuse the separate browser `package.json` version with the APK version.
2. Build and run relevant checks. Record the exact source commit, actual test outcomes and remaining hardware limitations.
3. Stage a versioned APK and SHA-256 file outside tracked source, such as `android/deliverables/`. Preserve the existing signing identity for updates; a different debug key cannot replace an installed app signed by another key. Do not upload private signing material.
4. Check the destination repository, authenticated GitHub account and existing release/tag. Use a release asset upload only within the user's authorized publishing task. Upload the reviewed APK/checksum and name the exact source commit for the tag; label a debug build honestly.
5. Download the public asset once, verify its SHA-256 against the built APK and update both README download links. Confirm the pushed branch/commit and working-tree status.

Authentication is local configuration: do not place tokens in source, docs, logs or remote URLs. If GitHub access fails despite a valid login, inspect the chosen account and repository permissions; Git URL rewrite rules can send HTTPS remotes through an unrelated SSH account. Fix the task's authentication without changing unrelated global accounts/settings.

Preserve **Copyright (c) 2026 Herson Santos** and the [MIT license](LICENSE). Original project code/documentation allows commercial use, modification and redistribution with the copyright and permission notices retained. Vendor manuals, third-party evidence and dependencies retain their own terms; the project license does not relicense them. Keep [the USB library notice](android/evidence/usb-serial-for-android-LICENSE.txt).

Respect [.gitignore](.gitignore): SDK paths, `local.properties`, keys, caches, generated builds, APK/AAB binaries, device logs and local reports stay out of commits. Debug artifacts go to release assets. Do not copy machine-specific Downloads paths or old Windows `C:/` paths into portable build instructions.

## Evidence and maintenance

Use these references when the relevant question arises:

- [README](README.md): user overview, APK download, supported-language summaries and license.
- [Android guide](android/README.md): SDK/build, permission recovery, image limits and physical test procedure.
- [Browser guide](docs/browser.md): profiles, dependency-free runtime, conversion/export, diagnostics and known transfer limitations.
- [v4.0 protocol](docs/imagetousb40-protocol.md): executable hash, methods inspected, packet fields and manual references.
- [Original dossier](android/evidence/Protocolo-USB.txt) and [IL dump](android/evidence/ImageToUSB.il.txt): historical technical evidence. Preserve originals; put corrections or new findings in maintained docs.
- [v3.6 investigation](docs/imagetoepd36-protocol.md): historical, distinct protocol; use only for that work.
- [Validation record](docs/validation.md): separate build success, offline tests, UI checks, device acknowledgment and physical display results.

When changing behavior, update its source, relevant tests and the corresponding maintained guide together. Record newly observed hardware evidence with its date, profile and limits. Keep this reference synchronized with significant architecture, protocol, locale or release-workflow changes; avoid copying transient device IDs, local screenshots or entire raw logs into it.
