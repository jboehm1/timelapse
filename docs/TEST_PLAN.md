> **CAPTURE-P0-001 STOP THE LINE (2026-10-30):** The user reports zero JPEG photos after Start on Samsung S21. Before any public release: emulator UI automation **and** real S21 must each produce at least 10 valid, correctly dimensioned, unique JPEG originals in the selected project, surviving restart. An emulator screenshot, compile, lint or UI counter alone does **not** satisfy this gate. The UI must show STARTING until a real first JPEG is verified, then RECORDING. [Issue #3](https://github.com/jboehm1/timelapse/issues/3) · [Master handoff](HANDOFF_MASTER_2026-10-30.md).

# TEST_PLAN — deterministic tests and multi-day device validation

## 1. Continuous integration (every PR)
- Clean Android compile, lint, Kotlin/JUnit logic tests.
- State transition tests: capture/day/night/overheat/disk error/retry exhaustion, idempotent Stop, no burst after late callback.
- Deterministic time & timezone tests: leap day, DST start/end Europe/Berlin, crossing midnight, Monday boundary, clock rollback.
- Encoder tests with synthetic JPEG series: None, blur, light trails; verify output stream FPS, width, duration, playback and sample count by ffprobe or MediaExtractor.
- Fault-injection fakes: camera callback timeout, MediaMuxer error, disk full, duplicate frame IDs, corrupted staging JPEG, low battery, thermal transition.
- Original-photo export tests stream JPEG/PNG files through the same bounded exporter used by SAF folder output and ZIP. They compare extracted/output hashes, check ordering and MIME types, reject invalid input sets before writing, and inject destination failures while verifying every source hash remains unchanged.
- Capture-session gap telemetry uses monotonic time between successful JPEG commits, exposes the longest gap alongside attempts/skipped slots, and resets per session. A JVM fault test covers nonuniform gaps and reset; emulator evidence requires at least ten committed originals plus internally consistent attempts/skips/gap metrics. These checks do not validate S21 cadence.
- The emulator's invalid-camera fault scenario requires the visible status to show zero saved photos and a positive error count separately, and saves counters as structured evidence. This passed in run [#41197640738](https://github.com/jboehm1/timelapse/actions/runs/41197640738): 0 saved, 0 shutter attempts (camera bind failed), 5 bind errors, 0 skipped slots. This validates C-16 counter distinction under synthetic failure without implying successful physical capture.
- Frame-journal recovery includes a torn final append without newline. Fault tests start with a durable original and partial journal row, recover through a fresh store instance, and require a parseable integrity row plus clean subsequent audit. This is crash-boundary simulation, not physical power-loss testing.
- Storage policy tests both sides of the reserve threshold (below pauses, exactly at the threshold resumes), and verifies critical thermal cooldown takes precedence when storage is also low. The capture service maps these decisions to its foreground notification. Kotlin policy run [#41198508756](https://github.com/jboehm1/timelapse/actions/runs/41198508756) and Android run [#41198508753](https://github.com/jboehm1/timelapse/actions/runs/41198508753) passed. The latter also ran the bounded diagnostics rotation/failure test.
- Privacy CI also parses `aapt dump permissions` from the compiled debug APK, so permissions merged from dependency manifests fail the build if they exceed the source allowlist. The exact `ACCESS_NETWORK_STATE` and package-scoped AndroidX dynamic-receiver protection permission are permitted because CameraX/AndroidX contribute them; neither is `INTERNET`. Parser tests cover these expected entries, dependency-added Internet, SDK-scoped Internet, and missing required capture permission. Android run [#41197032178](https://github.com/jboehm1/timelapse/actions/runs/41197032178) passed APK build, unit tests, merged APK audit and lint; cached Kotlin run [#41197032068](https://github.com/jboehm1/timelapse/actions/runs/41197032068) passed the deterministic reliability suites. This verifies current permissions and local-only network policy, not packet-level auditing of future changes.
- Camera-original video export generates four-frame H.264 clips at 24, 25, 30 and 60 fps, checks encoder timestamps and duration on Android, then requires independent ffprobe/full decode on the host. Emulator run [#41196363177](https://github.com/jboehm1/timelapse/actions/runs/41196363177) passed all four rates; committed measurements are in `docs/screenshots/emulator/fps-output-validation.json`. This is emulator-only and does not establish S21 encoder support or performance.
- The synthetic worker export suite now measures all five Blur windows (1/3/5/7/9), correlates every decoded Normal-frame worker centroid to its matching source JPEG, and counts retained vest pixels in Light Trails. Host validation independently decodes each resulting MP4; this G-03/G-05/G-06 evidence is pending emulator CI.
- Project-library scale test creates 105 independent projects/presets, reopens the catalogue, renames all projects, moves 100 into reversible trash, and restores them after constructing a fresh library instance.
- Every emulator/build evidence row records the tested source SHA, workflow/artifact reference, measured outputs and limits in `docs/DEVICE_TEST_EVIDENCE.md`; physical Galaxy S21 remains a separate `NOT RUN / BLOCKING` entry until the required hardware protocol is executed.

### Fast feedback and expensive simulation
- Local-only privacy guard: `tools/test_app_permission_policy.py` exercises the minimal camera manifest, direct and SDK-scoped permission escalation, explicit component export state, backup/cleartext defaults and application network-client detection (including wildcard Java imports). `tools/verify_app_permissions.py` checks the source manifest plus Java/Kotlin under `app/src/main`; Android PR CI runs both before Gradle. This static check does not inspect packets or the dependency-merged manifest.
- Original sequence destination failure: the fault-injection test fails after 32 bytes in the second SAF-like output, asserts the current partial destination is deleted, retains the first completed destination, and compares every original SHA-256 before and after. Provider cleanup is best effort; already completed exports are intentionally kept.
- ZIP destination failure also invokes a best-effort delete callback; the Android SAF caller removes the selected archive document on stream-open/write/close failure. JVM and Android tests inject a failing ZIP stream and verify cleanup is requested and original hashes remain unchanged.
- A literal backslash traversal filename is rejected before sequence output is opened; ZIP rejection requests destination cleanup and preserves the source hash. Android and pure-Kotlin suites passed this test on `89715227e7ed71cba1c8abcaa140d3eefeabf605` (runs #41194560591 and #41194560596).
- Emulator recording smoke also streams camera-generated JPEGs through the individual-file exporter and ZIP exporter, decodes every sequence JPEG, compares every exported hash to its source, and rechecks source hashes. The host verifier repeats JPEG decoding and hash checks against captured sources, and rejects unsafe ZIP paths. This verifies the production exporter core on real emulator capture outputs; the SAF document-picker interaction itself and S21 remain separate.
- Local/core feedback: `bash tools/test-policy.sh` compiles the four deterministic JVM suites (capture policy, FrameStore, original exporter and timelapse logic) in one Kotlin compiler invocation, runs each independently, then checks the generated progress dashboard. It needs JDK 17 and Kotlin CLI 2.0.21, but no Android SDK.
- The pure-policy GitHub workflow caches that pinned Kotlin CLI by version/runner OS, so warm runs do not download and unpack the compiler again.
- Android PR CI remains the authoritative build/unit/lint check. The emulator job reuses its existing committed screenshot-evidence commit as a checkpoint: any app/core/emulator-harness changes since that tested commit trigger the full virtual-device path; documentation-only and unrelated tool updates skip it. Manual dispatch always runs the full simulation. It exercises camera, saved JPEGs, export effects/batches and workspace UI, then independently verifies evidence on the host.
- Emulator success is a simulated-camera regression signal only; the S21 gate below remains mandatory for device claims.

### Moving-people export fixture (emulator)
- `PersonWalkExportSimulationTest` generates seven deterministic JPEG frames of two high-visibility workers walking left-to-right across a fixed construction-site scene. This is a synthetic encoder input, not CameraX footage.
- It exports the same originals as `NONE` and `MOTION_BLUR` MP4s (640×360, 7 fps, five-sample blur window), checks both workers' progression, frame count/metadata, decodes the middle output frame on Android, and requires a visible pixel difference between the two effects.
- CI installs FFmpeg and `verify-emulator-evidence.py` requires both host `ffprobe` and `ffmpeg`; missing independent decoders now fail the evidence job. It checks hashes and probes codec, frame count, dimensions, frame rate and duration for all three camera-input effects, the two person-walk outputs, cache benchmarks, single-frame output and both day-batch MP4s, then fully decodes every MP4 and validates the PNG comparison. The expanded verifier passed in run #41194560639. Artifacts include `person-walk-comparison.png`, decoded frames, MP4s, and `person-walk-validation.json`.
- This demonstrates the visual and export-path behavior on the emulator only. It says nothing about real pedestrians, physical camera motion, or S21 performance.

## 2. Device test matrix (Samsung S21 target)
| Case | Setup / action | Expected |
|---|---|---|
| T01 | Capture 1s, 10s, 30s for 1h | Single in-flight request, no burst, frame IDs monotonic |
| T02 | Tap AF, AE, both at multiple spots | Distinct correct actions and feedback |
| T03 | Capture 8h wall power, display OFF | Measure actual wakeups, heat, frames missing; no app crash |
| T04 | Night schedule 07:00–18:00 for 72h, screen truly OFF | No night frames, morning resumes if supported, otherwise explicit “needs user” |
| T05 | Device becomes HOT/SEVERE | Safe pause, clean state; recovery after cooldown |
| T06 | Fill storage to safety threshold | No new frames, originals intact, clear warning |
| T07 | Remove charging cable, battery low | Safe pause / warning, no endless wakeups |
| T08 | Force-stop app | Record limitation; no impossible auto-recovery claim |
| T09 | Reboot while session armed | State preserved; permitted notification/user action |
| T10 | Clock jump and DST | Schedule logically correct; frame cadence stays monotonic |
| T11 | Export 4K Motion Blur and Trails during idle | Valid MP4, original JPG unchanged |
| T12 | Cancel export/kill app during encoding | Partial video cleaned or marked incomplete; originals intact |
| T13 | Open another camera app / deny permission | Bounded recovery, actionable status |
| T14 | Camera subsystem hung / frame save error | Timeout and limited retries, transition to NEEDS_USER_ACTION |
| T15 | Network disabled | Capture unaffected |
| T16 | 7-day scheduled soak | Exportable frames and full timeline/accounting; no resource leak trends |

## 3. Required diagnostics
- Reference device model, Android security patch and build number, app commit SHA and APK SHA256.
- Expected frame count *based on active windows*, saved valid JPEG count, missed deadlines count, longest gap, capture p50/p95/p99 latency.
- Battery charged/uncharged %, estimated power if measured, USB power source, thermals over time, screen power state.
- App resident/FGS status and exceptions; file/manifest validation and storage levels.
- FPS/duration/codec/bitrate via media inspection.
- Attach anonymized reproducible test report to PR or release.

## 4. Release criteria
Prototype: build succeeds.
Alpha: T01/T02 and 8h day session without crash, all outputs validated.
Beta: T04–T15 and 72h soak pass with limitations documented.
Stable candidate: two 7-day runs with reproducible no-critical-data-loss metrics. Never write “100% reliable” or “guaranteed 24/7” without bounded stated conditions.

## 5. Command snippets
```bash
./gradlew testDebugUnitTest assembleDebug
adb shell dumpsys power
adb shell dumpsys thermalservice
adb shell dumpsys activity services
adb logcat -v time > capture-logcat.txt
ffprobe -v error -show_entries format=duration:stream=width,height,r_frame_rate,bit_rate -of json output.mp4
```
Check Samsung "Unrestricted" battery setting during device runs but do not use it as a substitute for app lifecycle engineering.


## Export efficiency and day batches (new gate)

Run ExportPlanningTest plus ExportPerformanceCheck in RecordingSmokeTest. Require 30 output frames in both cache modes, decoded-source calls 88→30 for window 3, bounded bitmap bytes, playable one-frame output, inclusive timezone/DST day grouping, two separately readable two-frame day MP4s, first-day persistence and zero pending rows after mid-second-day cancellation, and original hashes unchanged. Record real elapsed time and encoder hardware/software identity without claiming an S21 speedup. Require host benchmark/batch JSON and MP4 bytes. 10k/4K/8K and thermal stress remain separate unexecuted device checks.

## CI follow-up — 2026-10-31

PR #2 head `cc8f031a4af31840c00b555f07c38a753f559dc6` produced a red Android build because three invalid-input tests omitted the required `openOutput` callback. Commit `5aca98301f95c6523444dd358cc95a911ab53dab` records the fix; the current branch head `404e4c7ca11f3607177046cf77a14fd901691f31` contains the explicit callbacks in all three calls. Android run [#41191004498](https://github.com/jboehm1/timelapse/actions/runs/41191004498) passes for that head.

The same PR head's emulator job compiled the app and instrumentation APK, and its recording/export cases passed. The workspace journey failed at the project detail's Rename action because UiAutomator could not resolve the Compose text node. The action buttons now expose stable content descriptions; the emulator journey must pass again before updating its evidence checkpoint or any acceptance score. This is an automation selector correction, not evidence of new device behavior.

The strict follow-up emulator run [#41194560639](https://github.com/jboehm1/timelapse/actions/runs/41194560639) passed on tested application source `89715227e7ed71cba1c8abcaa140d3eefeabf605` (evidence checkpoint `db265b27b91f8e0e3f7c2520309deb4b458b3761`). All four instrumentation tests, 12 camera-emulator JPEG hash/decode checks, original sequence/ZIP hash checks, and host FFmpeg verification of ten MP4s passed. Those ten files comprise three camera-input effects, two synthetic two-worker clips, two cache benchmarks, one single-frame output, and two day-batch clips. All probed as H.264 and fully decoded. This validates emulator outputs and pipeline behavior only; the S21 gate remains open.

The next two-worker regression also decodes each Normal MP4 frame and checks its moving-worker centroid against the same indexed source JPEG (<12 px tolerance), plus monotonically advancing direction. This tighter source-to-output assertion is pending fresh emulator CI and receives no score until it passes.

The accessibility-description correction passed all three instrumentation tests on source SHA `ec6daaebce3f4924835f78ef6feeda3d7c6fecb9` in [emulator run #41190162334](https://github.com/jboehm1/timelapse/actions/runs/41190162334). Host evidence independently decoded and hash-checked 12 emulator JPEGs. Latest storage-journal CI on `404e4c7` has policy and Android runs green; emulator run [#41191004588](https://github.com/jboehm1/timelapse/actions/runs/41191004588) was still running at this update. This closes the Compose selector regression only; it does not change the S21 acceptance gate or checklist score.

## S21 first-capture acceptance run

1. Record `adb shell getprop ro.product.model`, `adb shell getprop ro.build.fingerprint`, Android security patch, app commit SHA and APK SHA-256. Install with `adb install -r`; do not uninstall an existing app to bypass a signing mismatch because that can delete private originals. Grant camera and notification permissions through Android's UI.
2. Create a new Timelapse project with a unique name, select a 2-second interval, choose a reported rear lens and the largest offered JPEG resolution. Record the selected Camera2 camera ID and resolution. Start capture with the screen visible; the UI must remain STARTING until the first committed JPEG is listed, then show RECORDING.
3. Wait for 10 consecutive new originals. Record the app's resolved project directory, file names, sizes, dimensions, EXIF timestamps, SHA-256 hashes, camera ID, requested/actual dimensions and capture latency. Decode all ten files independently on a host; do not rely on the UI counter or Gallery thumbnail.
4. Stop capture, force-close/reopen the Activity without clearing data, reopen the project and verify the same ten files and hashes. Export a copy of the originals through the app if `adb pull` cannot access the app-specific Pictures directory. Never edit or delete originals during verification.
5. Repeat with the display physically OFF for at least 10 additional intervals. Confirm actual display state with `adb shell dumpsys power`; collect `adb logcat -v time`, `adb shell dumpsys thermalservice`, and `adb shell dumpsys activity services` through the run. A dark screen inside the app is not proof that the display is off.
6. Stop before changing lens or resolution. Select another offered rear lens and resolution, start a separate test project, and verify 10 new files plus the actual capture ID/dimensions. Unsupported combinations must fail visibly; no silent fallback may be reported as the requested mode.
7. Attach only anonymized logs and a compact result manifest to the issue/PR. Keep construction images local unless the owner explicitly approves sharing them. Mark CAPTURE-P0-001 complete only after every required test and restart check passes on the physical S21.
