> **CAPTURE-P0-001 STOP THE LINE (2026-10-30):** The user reports zero JPEG photos after Start on Samsung S21. Before any public release: emulator UI automation **and** real S21 must each produce at least 10 valid, correctly dimensioned, unique JPEG originals in the selected project, surviving restart. An emulator screenshot, compile, lint or UI counter alone does **not** satisfy this gate. The UI must show STARTING until a real first JPEG is verified, then RECORDING. [Issue #3](https://github.com/jboehm1/timelapse/issues/3) · [Master handoff](HANDOFF_MASTER_2026-10-30.md).

# TEST_PLAN — deterministic tests and multi-day device validation

## 1. Continuous integration (every PR)
- Clean Android compile, lint, Kotlin/JUnit logic tests.
- State transition tests: capture/day/night/overheat/disk error/retry exhaustion, idempotent Stop, no burst after late callback.
- Deterministic time & timezone tests: leap day, DST start/end Europe/Berlin, crossing midnight, Monday boundary, clock rollback.
- Encoder tests with synthetic JPEG series: None, blur, light trails; verify output stream FPS, width, duration, playback and sample count by ffprobe or MediaExtractor.
- Fault-injection fakes: camera callback timeout, MediaMuxer error, disk full, duplicate frame IDs, corrupted staging JPEG, low battery, thermal transition.

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
