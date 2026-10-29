# BUILD REPORT — 2026-10-30 (CET)

## Release identity
- **Type:** Debug-signed Android alpha APK — NOT stable, not S21 hardware tested.
- **Source revision (compiled):** `1e9eeb2b20f80a9d9fda113de8361b3d526b5ef7` on `feat/android-capture-export-alpha` (PR #2).
- **GitHub Actions run:** [#40972789344](https://github.com/jboehm1/timelapse/actions/runs/40972789344)
- **Result:** **SUCCESS**, `:app:testDebugUnitTest`, `:app:assembleDebug` and `:app:lintDebug` all passed; independent pure-Kotlin policy workflow passed in same revision.
- **APK artifact:** [timelapse-debug-apk (artifact #12941815183)](https://github.com/jboehm1/timelapse/actions/runs/40972789344/artifacts/12941815183)
- **Artifact retention:** GitHub Actions artifact may expire after 14 days from run. The user has also received a direct APK file in ChatGPT.
- **Extracted APK filename:** `app-debug.apk`
- **Extracted APK bytes:** **17,555,394**
- **Extracted APK SHA-256:** `0f9c6f5e02bf40484ce8ecccb0ba17965dff3918399b210fbf83a40a0fc28f81`
- **Compile SDK:** 36 (required by CameraX 1.6.2), minSdk 29, targetSdk 35, JDK 17, Gradle 8.13.
- **Android package:** `eu.example.constructionlapse`, version `0.2.0-alpha` (versionCode 2).

## What is proven
- GitHub clean-checkout Android compilation, debug APK generation, Gradle JVM unit tests and Android lint (zero blocking lint errors).
- Pure Kotlin capture timing, effect window boundaries and frame-store recovery unit checks pass in CI.
- SHA-256 integrity of the downloaded APK is independently verified against this report.

## What is NOT proven (release blockers)
- APK installation and launch on real Samsung Galaxy S21.
- Correct CameraX capture session, independent AF/AE region mapping, lens enumeration, manual ISO/shutter focus on actual hardware.
- Accurate capture intervals with the physical display OFF, 07:00–18:00 scheduled capture, unattended morning reopening under Doze/Samsung OEM policies.
- Exported MP4 decoding/playback, correct orientation, 4K/8K capability, long image-sequence Motion Blur/Light Trails performance.
- No corruption through real disk-full, battery detach, thermal events, process termination.
- 8h, 72h and 7d reliability measurements.
- RAW DNG, advanced Photolapse editor, external-SD direct project storage, full PRO parity. These remain in spec/MASTER_TODO.
- Commercial release signing / Play Store deployment. **Do not publish this debug build as a production-ready app.**

## Architecture decision ADR-005: CameraX versus Camera2
**Choice: hybrid CameraX + Camera2Interop.** CameraX builds upon Camera2 and supplies higher-level camera lifecycle, preview, common JPEG capture, camera compatibility and binding; this is preferred for long-session complexity. Use Camera2 features through `Camera2CameraInfo`/`Camera2CameraControl` and opt-in experimental interoperability only where we require per-lens physical capabilities, AF/AE state and manual ISO/shutter/diopter settings. For unsupported RAW/Bayer/DNG, deeply manual multi-stream or hardware-only modes, consider a separate Camera2 backend behind a tested `CameraBackend` interface; don't maintain both live camera sessions simultaneously. Camera2 isn't inherently higher image quality; output depends on capture request/ISP/camera device and feature support. **Caution**: Camera2 interop can override CameraX capture flags and requires extra device tests.

Android official references:
- https://developer.android.com/media/camera/choose-camera-library
- https://developer.android.com/media/camera/camera2
- https://developer.android.com/media/camera/camerax

## Next tests on Galaxy S21
1. Install debug APK, grant camera and notification, select one project, capture ten JPEG frames at 10-second interval.
2. Verify two independent targets (green AF on wall, yellow AE on sky) and manual controls only if sensor supports them.
3. Encode a short MP4 with None, Motion Blur and Light Trails; validate output in Android gallery and external ffprobe.
4. Capture for 8 hours with display OFF, inspect missed deadlines, thermal battery and storage log.
5. Test daily 07:00–18:00 schedule through overnight screen-off, 72-hour run and process kill; document any required user interaction.
6. Complete feature checklist and reproduce 7-day reliability run before declaring stable.

**Scope:** This report is evidence for an **APK build**, not a claim of feature completeness.
