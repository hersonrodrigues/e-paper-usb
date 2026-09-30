---
name: epaper-usb
description: Develop, diagnose, translate, test and release the Android USB OTG app and desktop Web Serial app in the e-paper-usb repository. Use for this project's Good Display GDP075FU1 and ImageToUSB v4.0 workflows, not unrelated e-paper firmware or general USB tasks.
license: MIT
---

# E-paper USB maintenance

This skill is scoped to the checkout containing this file. The full project reference is [SKILLS.md](../../SKILLS.md); paths in that reference start at the repository root. Keep this entry point with the repository rather than copying it alone into a global skill directory.

Read the reference's **Product and implementation boundaries**, **USB protocol contract** and **Validation baseline** first. Then select the task's workflow:

| Task | Read next |
| --- | --- |
| Android changes or permission/connect problems | **Android development**, then [Android guide](../../android/README.md) and the relevant Java classes |
| Web UI or serial failure | **Browser development**, **Diagnosis and physical testing**, then [Browser guide](../../docs/browser.md) |
| Colors, framing, ACKs or transfer timing | [v4.0 protocol analysis](../../docs/imagetousb40-protocol.md) and the corresponding Java/JavaScript tests |
| Translation or image behavior | **Images and localization**, the locale catalogs and image-processing code |
| APK or repository publication | **Releases and attribution**, current build metadata and the existing release |

Preserve these project decisions:

- The firmware determines the wire protocol. Implement it internally; do not invent a new header, ACK, abort command or raw fallback to bypass a failure.
- A four-color payload is 96000 bytes while the header encodes 48000. Require a complete, checksum-valid F1 ACK before pixels, and preserve the documented packing, block delimiters and pacing.
- Start image transmission only on an explicit Send action. An incomplete transfer requires physical reset; it must not retry automatically.
- Keep preview and payload consistent. Android locale/RTL changes must not silently change prepared pixels.
- Preserve the browser's zero external-library runtime, persistent reader and 25-second wait. Android's pinned USB driver and different read/refresh lifecycle are separate implementation facts.
- Distinguish host writes, device acknowledgment and physical display refresh. Test fixtures, ASCII output, emulators and APK installation do not establish hardware success.
- Preserve the MIT attribution to Herson Santos and third-party notices. This skill does not grant additional permission to publish, send pixels or change accounts.

Inspect the current branch and relevant source before editing. Apply the checks appropriate to the changed implementation, as described in **Validation baseline**; documentation-only work needs link/content validation rather than another Android build. Report what changed, checks actually run, artifacts produced and remaining physical-test requirements. Update maintained project docs when new evidence changes the recorded behavior.
