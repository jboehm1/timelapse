# Device test evidence register

This register separates emulator results from physical-device evidence. “Not run” is a valid status; it must never be inferred from green CI.

## Physical target

| Device | OS/build | App source / APK | Test | Result | Evidence |
|---|---|---|---|---|---|
| Samsung Galaxy S21 | Android 15 build and security patch not recorded | No S21-installed APK/result recorded | CAPTURE-P0-001: ten valid JPEGs, restart, screen OFF, lens/resolution | **NOT RUN / BLOCKING** | User reported zero JPEGs on Start; [issue #3](https://github.com/jboehm1/timelapse/issues/3) |

No physical-device claims are accepted until an anonymized manifest supplies the exact device build, source SHA, APK SHA, logs, dimensions, EXIF/timestamps, hashes, power/thermal data and each acceptance result.

## Emulator evidence

| Run | Source | Environment | Measured result | Scope limit |
|---|---|---|---|---|
| [Emulator run #41191004588](https://github.com/jboehm1/timelapse/actions/runs/41191004588) | `404e4c7ca11f3607177046cf77a14fd901691f31` | Android API 35, Pixel 6 x86_64 profile, emulated camera | 12 host-decoded JPEGs at 1856×1392; three 640×360 effects, four frames, 133333 μs; software cache case 88→30 decodes, 58 hits, 2,764,800 peak cache bytes, 1345→664 ms; day-one MP4 retained after day-two cancellation | Camera and codec are virtualized; no S21 timing, heat or screen-off proof |
| Android run #41191466063 (PR merge test SHA `f24ba0dd463bd695471958fa3d6ef4e34d8bf0b2`) | PR head `5b41a9ef1f943ea4e1aa0b9a213cdcbf77c6a7b2` | Hosted Android API 36 build runner | Unit tests, debug APK compilation and lint succeeded | Build/test only; not an installation or physical capture result |
| Policy run #41191466117 | `5b41a9ef1f943ea4e1aa0b9a213cdcbf77c6a7b2` | Hosted JDK 17 + cached Kotlin compiler | FrameStore, schedule, thermal, retry and timelapse logic policy suites passed | Pure JVM scope only |
| [Emulator run #41191466072](https://github.com/jboehm1/timelapse/actions/runs/41191466072) | `5b41a9ef1f943ea4e1aa0b9a213cdcbf77c6a7b2` | Android API 35, Pixel 6 x86_64 profile, emulated camera | Instrumentation **4/4 passed**. 13 emulator-camera JPEGs independently archived at 1856×1392. Synthetic moving-person fixture: seven frames, x centroids 114.5→522.5, Normal 21,808 B / SHA-256 `05bf600618ebcfc4170391b370bd91cb9ae61a0b0d48045b9d027148ba7b6746`; Motion Blur 21,728 B / SHA-256 `b13d8372c62d68260425b617c47959c062bb0b8ed2c19907b3aa5bb9f12ca187`; both H.264, 640×360, 7 fps, seven frames, ~1 s. Middle-frame thresholded RGB difference: 17,815 pixels; absolute RGB sum 1,577,888. Downloaded artifact independently decoded with local ffprobe/ffmpeg 6.1.1; full decode passed. | The CI runner lacked ffmpeg/ffprobe on this run; that gap is being closed by making decoder installation/availability mandatory in the next CI run. Emulator-only; no S21 behavior or performance proof |
| [Emulator run #41192228191](https://github.com/jboehm1/timelapse/actions/runs/41192228191) | Source `55ef4f10ff319cd2bf0fdf81572963c5b9ed9b03`; evidence checkpoint `35eba476d78a3fdc75f3f59315e8f4c6325a2b07` | Android API 35, Pixel 6 x86_64 profile; host FFmpeg/ffprobe installed and required | **4/4 instrumentation tests passed**; 13 real emulator-camera JPEGs decoded/hash-checked at 1856×1392. Synthetic walking worker: seven frames, Normal 21,808 B / SHA-256 `3daafe6096aa6d5bf2400fd4a8756ee5eb0e57e5947462c906b97bd39f9c5569`, Motion Blur 21,728 B / SHA-256 `ff87e9b9e7ad87d254fd3f98b6565d375a085d6c6cf6123fff8bf9d899388d9d`; both H.264, 640×360, 7 fps, seven frames, duration 1,000,000 μs. Host ffprobe reports 0.999989 s and ffmpeg fully decodes both. Middle-frame difference: 17,815 pixels over threshold 30; absolute RGB delta 1,577,888. Cache simulation: 30 frames, 88→30 source decodes, 58 hits, 2,764,800 peak cache bytes, 3791→1646 ms on software codec `c2.android.avc.encoder`. Day-batch cancellation retained day one, removed pending day two, and recorded zero pending rows. [Artifact #13040060257](https://github.com/jboehm1/timelapse/actions/runs/41192228191/artifacts/13040060257), SHA-256 `8835e9915be8f07138c9e37647728c8d91e8b5c67e781989446cf88eda4f7801`. | Entirely emulated; no physical pedestrian/camera, S21 performance, physical screen-off or battery/thermal proof |

The generated image comparison is committed as [person-walk-comparison.png](screenshots/emulator/person-walk-comparison.png); the latest machine-readable metrics are in [person-walk-validation.json](screenshots/emulator/person-walk-validation.json). Run #41192228191 verified the CI-installed host decoder path.

## Entry template for future runs

```text
Run date/time and operator:
Device model / OS / security patch / build fingerprint:
App source SHA / APK SHA-256 / install path:
Camera ID / requested and actual resolution:
Interval / schedule / screen state / power state:
Expected active slots / valid originals / missing slots / longest gap:
JPEG dimensions / EXIF / unique IDs / file hashes:
Capture latency p50 / p95 / p99:
Thermal / battery / storage before and after:
Restart / screen-off / lens-switch outcomes:
Logs and artifact links:
Result, scope, limitations, follow-up:
```
