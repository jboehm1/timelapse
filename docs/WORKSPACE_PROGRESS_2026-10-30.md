# Diagnostic v0.3 — projects, planning and actual export validation

Baseline: `db0e482`, PR #2. All changes remain on `feat/android-capture-export-alpha`; no release/merge.

## Implemented and compiled locally

- Compose Material 3 projects workspace entered by tapping the project name under the camera. Search and Timelapse/Photolapse/Trash filters, create/rename, copy presets without copying originals, reversible trash/restore. Existing project IDs and original paths stay unchanged. Corrupt metadata falls back with a warning, without hiding originals.
- Coil local-file thumbnails and original preview, lazy/paged gallery, project counts and stored bytes. No Internet or storage permission; Coil contributes the normal ACCESS_NETWORK_STATE permission, with no runtime grant.
- Pure Kotlin planner: nominal intervals down to 500 ms, active-day counts, photo/video storage and playback duration, actual JPEG median, disk runway with 512 MiB/5% reserve and 20% JPEG-size allowance. Fixed sessions use exact new-frame limit; unlimited sessions have explicit projections.
- Per-project interval/daily window/weekdays/frame-limit persistence, reused by existing camera UI/service. No OS background camera permission bypass.
- Test Photo and Photolapse: exactly one new original. Finite recording stops at N new commits. Notification Stop action.
- MP4 cancellation, disk preflight, preserved originals and propagated muxer-finalization errors. Visible diagnostic export buttons corrected (old vertical-parent button width was zero).

## Local evidence

`testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` PASS. **22 JUnit tests**: 5 existing reliability tests, 10 planner/library/export-budget tests and 7 thermal/telemetry/DST tests. Coverage includes 105 projects, stable original bytes/path through rename/trash/restore, legacy compatibility, damaged metadata, path confinement, independent preset duplication, precise 0.5/2-second daily counts, fixed frame totals and disk reserves.

## Expanded emulator gate (execution pending at this checkpoint)

Three instrumentation cases:
1. Real UI capture + background/persistence + host JPEG hashes. In addition, encode four actual camera originals with None/Motion Blur/Light Trails, check AVC dimensions/duration/sample timestamps and decode an MP4 frame with Android MediaMetadataRetriever. Cancel a separate export before encoder startup and compare original hashes afterward.
2. Unavailable-camera failure without false Recording.
3. Compose UI create → planner → 500 ms preset → stop at 3 new photos → test photo adds exactly 1 → gallery → rename → empty preset copy → trash → restore. Compare all four original hashes. Screenshot every key screen.

Host verifier requires actual JPEG and MP4 archives plus project evidence; missing bytes/incorrect counts or hashes fail CI. S21, physical display OFF and 8h/72h/7d remain unverified.

## Scope still unfinished

Full Compose camera controls, RAW, external SD persistence, advanced color/monitoring, full timeline/editor, real hardware export quality/4K/8K, unattended morning wake, long soaks, localization/accessibility certification and commercial signing remain incomplete. Reversible trash does not permanently delete or reclaim storage. Metadata is filesystem-based; Room frame indexing is not implemented.

## First emulator results and follow-up

Source `009b331`: Android/JUnit/lint PASS [41069297379](https://github.com/jboehm1/timelapse/actions/runs/41069297379); pure Kotlin PASS [41069297428](https://github.com/jboehm1/timelapse/actions/runs/41069297428).

Emulator run [41069297365](https://github.com/jboehm1/timelapse/actions/runs/41069297365): unavailable-camera and real capture/export tests PASS. Three actual H.264 MP4s (None/Motion Blur/Light Trails), each 640×360, 4 samples, 30 FPS, duration 0.133333 s, were decoded on Android and independently inspected with local `ffprobe` after downloading the artifact. Original JPEG hashes unchanged. The overall run FAILED in the new workspace test at project-name entry/creation; no successful workspace journey is claimed. The test now resolves the actual editable Compose descendant, verifies entered text/enabled Create, and saves dialog/failure hierarchies.

Follow-up implementation: sustained-low-temperature hysteresis (minimum 3 min at NONE/LIGHT after SEVERE), explicit attempted/skipped-slot counters, periodic thermal/battery/disk diagnostic events, active-only missed-slot accounting, asynchronous camera-screen inventory with bounded pending scans, and success feedback after actual preset persistence. Morning alarms skip excluded weekdays and handle both autumn overlap offsets and missing spring windows. New deterministic thermal/telemetry/DST tests increase local JUnit coverage to **22 tests**. Hardware behavior remains unverified.

## Further correctness fixes before final UI validation

- Project-specific planning now derives active hours from the actual daily window, or 24 h/day for continuous sessions. Free planning remains adjustable (e.g. 8 h). This prevents underestimating a continuously armed project as an 8-hour shift.
- Process-wide import/export ownership, cancellation and progress survive Activity recreation. Capture and project switching cannot overlap active file work, including night/thermal standby and unbound-Activity picker callbacks. No background/process-death recovery claim.
- Compose text-entry testing now uses actual focus/key input, waits for fresh editable-node text and checks the enabled Create action, rather than reading a stale accessibility snapshot immediately after setting text. The first two emulator attempts therefore do not establish a working complete workspace journey.
- Expanded workspace test also verifies two Photolapse button presses produce exactly two originals in a separate project, preserve the original Timelapse project's four hashes, and refuse test capture while file work is active.

## Latest CI checkpoint

Source `0498860`: Android APK, 22 unit tests and lint PASS ([41075800419](https://github.com/jboehm1/timelapse/actions/runs/41075800419)); pure Kotlin policy tests PASS. Emulator [41075800385](https://github.com/jboehm1/timelapse/actions/runs/41075800385) passes real capture/export and unavailable-camera tests, but the workspace case still fails at text entry. Diagnostic unlock removes the previous startup blockage. No complete workspace pass is claimed. The next gate uses Compose's text-edit semantics with recomposition synchronization rather than emulator keyboard injection, and emits compact app UI nodes on failure.
