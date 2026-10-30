# Capture blocker investigation — 30 October 2026

Baseline inspected: PR #2, `89e4c5c1063e8e273537ed296e987ae0340c15d1`.
CAPTURE-P0-001 remains open; the S21 cause is not yet reproduced or proven fixed.

## Concrete defects corrected

- `bindCamera()` overwrote binding failures with “CAPTURING ready”; binding now returns success explicitly and preserves the error.
- UI displayed RECORDING whenever a session was armed, even at zero new images. It now waits for a successful durable JPEG commit in this session. Existing project originals cannot satisfy that condition.
- CameraX callback and file-publication exceptions were discarded. Logcat tag `TimelapseCapture` now traces intent, binding, JPEG request, commit, dimensions/bytes, and exact failure codes with wall/monotonic clocks.
- Storage/startup errors were immediately overwritten by STOPPED. The terminal error survives cleanup and Activity recreation.
- A camera-provider future or index callback could act after Stop/restart; session/binding generations now reject stale callbacks. Each image callback retains its original FrameStore instead of accidentally using a newer project's store. Already-written originals are preserved.
- A first-photo deadline of 30 seconds while capture is allowed prevents indefinite silent startup. Scheduled night/thermal/storage/battery pauses disarm it. Timeout cancels capture work and requires explicit Stop/retry.
- Camera permission approval now resumes the user's pending Start action.
- Linux/Windows bootstrap target was API 35 despite compileSdk 36; corrected to 36.

## New regression gate

`RecordingSmokeTest` drives project creation, settings and Start through the actual UI. It requires ten newly written JPEGs, then at least two more with Activity backgrounded, Stop/reopen, independent full image decoding, monotonic unique IDs, SHA-256 comparison and FrameStore reconciliation. It writes per-file bytes/dimensions/hashes and screenshots to private test evidence.

`tools/emulator-smoke.sh` runs the instrumentation and fails unless it reports success. Its EXIT trap collects logs even on failure. CI retains synthetic JPEG originals and evidence. No user images are collected/uploaded by this test.

Requirements: CAM-001/004, REL-002/006/010/012; tracker C-16, H-03, J-03, J-24 (prototype scores until execution).

No new runtime dependencies or copied upstream application code. Test-only AndroidX dependencies retain Apache-2.0 terms. No new application permissions.

## Validation

- Static diff/CI shell syntax and progress consistency: executed locally.
- Local Temurin JDK 17.0.21, Gradle 8.13 and Android SDK 36 installed. Initial full `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed (4m 7s).
- GitHub Android/JUnit/lint PASS: run [41062374462](https://github.com/jboehm1/timelapse/actions/runs/41062374462). Pure Kotlin PASS: [41062374351](https://github.com/jboehm1/timelapse/actions/runs/41062374351).
- Emulator run [41062374354](https://github.com/jboehm1/timelapse/actions/runs/41062374354) compiled both APKs but FAILED during official SDK system-image ZIP provisioning, before app installation/test. Added three bounded provisioning attempts; no JPEG evidence claimed.
- Physical S21, screen physically OFF, MP4/8h/72h/7d: not executed. No production release or merge authorized by this evidence.

## Follow-up: on-device diagnostic export

Settings → Export → **Save capture diagnostics (.txt)** now offers an Android document picker. Logs remain private until the user explicitly chooses a file. No images or network upload are included. The trace has a bounded 64-event worker queue and two 256 KiB rotating files; slow/failing diagnostics cannot block the capture/UI thread. Export reports dropped diagnostic events. A JVM regression test checks rotation, recent-error retention and multiline sanitization; all five JUnit tests pass locally. F-13/F-14/H-03/J-03 are compiled-stage only (40%); S21 and actual emulator recording remain unverified at this checkpoint.

## Validation update

- `d499a91`: GitHub Android/JUnit/lint PASS in [41063353243](https://github.com/jboehm1/timelapse/actions/runs/41063353243); pure Kotlin PASS in [41063353156](https://github.com/jboehm1/timelapse/actions/runs/41063353156).
- Local current app: both APKs + five JUnit tests + lint PASS (1m 33s). Diagnostic APK SHA-256: `c70bb5936bf0e7144b2522b744f5350154493095ec19bb7f3f065da4ea31ae3c` (17,854,254 bytes).
- Added and compiled a second instrumentation case: unavailable camera must reach a bounded NEEDS_USER_ACTION error with zero JPEGs and no Recording claim. This is fault injection, not a fake camera producing fixture photos.
- Local software emulator could not finish boot promptly without `/dev/kvm`; stopped in favor of the KVM-enabled CI run. No local camera-runtime result claimed.

## First actual emulator recording proof

Run [41063353155](https://github.com/jboehm1/timelapse/actions/runs/41063353155), source `d499a91`, passed the UI-driven recording test: **12 real CameraX JPEGs**, each decoded on Android, **1856×1392**, 59,125–61,397 bytes, unique IDs 1–12, original SHA-256 hashes unchanged after background capture and Activity reopening. The downloaded TSV and capture logs agree. This is emulated camera output, not S21 photography.

Evidence review found a collection defect: `adb exec-out run-as ... tar /sdcard/...` produced a nonempty permission-error text file, which the script accepted as an archive. It did NOT invalidate the Android image-decoding/hash assertions, but raw JPEG bytes were absent from the downloadable archive. The next iteration copies the synthetic test JPEGs to app-private test evidence and requires independent host-side Pillow decoding, dimensions and SHA-256 agreement. Merely nonempty artifact output is no longer accepted.

Screenshot inspection also showed the capture label lagging the filesystem count (9 displayed while the test had ten files). The test now waits for a visible double-digit count before taking its recording screenshot. Full Compose UX work remains pending; capture functionality takes priority.
