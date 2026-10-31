# Continuation report — 2026-10-31

## Repository and PR state

- Repository: `jboehm1/timelapse`; PR #2 remains **open and unmerged**, targeting `main`.
- Branch: `feat/android-capture-export-alpha`.
- Initial local HEAD was `5aca98301f95c6523444dd358cc95a911ab53dab`; initial remote PR head was `cc8f031a4af31840c00b555f07c38a753f559dc6`.
- The local `5aca983` fix explicitly passed `openOutput` to all three invalid-input test calls. It was included with the subsequent workspace accessibility correction in tested source commit `ec6daaebce3f4924835f78ef6feeda3d7c6fecb9`.
- After successful emulator validation, the emulator workflow committed refreshed screenshot and JSON evidence at `9f955557699277c06e4057294a169bbd1c497427`, now the PR head. That commit changes emulator evidence only; it does not change tested app source.
- Direct `git push` could not authenticate in this environment. The connected GitHub repository integration published the prepared commit with a guarded fast-forward; local branch was then synced to the remote. No force-push or merge occurred.

## Initial CI failures and fixes

The initial head `cc8f031` had three Android test compilation errors: missing `openOutput` arguments at `OriginalPhotoExporterTest.kt:79–81` ([Android run #41189583904](https://github.com/jboehm1/timelapse/actions/runs/41189583904)). Its emulator build and recording/export cases passed, but the workspace journey failed at `WorkspaceSmokeTest.kt:171` with `Missing Rename` ([emulator run #41189583923](https://github.com/jboehm1/timelapse/actions/runs/41189583923)). Logs were inspected through the Kotlin compiler errors and the instrumentation stack trace.

The test callbacks were fixed in `5aca983`. Project detail actions now provide explicit accessibility descriptions, and the workspace test selects Rename and Copy preset through those descriptions.

## Validation on tested source `ec6daaebce3f4924835f78ef6feeda3d7c6fecb9`

- Pure Kotlin reliability policy suite: **PASS**, [run #41190162316](https://github.com/jboehm1/timelapse/actions/runs/41190162316).
- Android APK build, JUnit tests and lint: **PASS**, [run #41190162329](https://github.com/jboehm1/timelapse/actions/runs/41190162329). The debug APK remains an unverified diagnostic build.
- Emulator suite: **PASS**, [run #41190162334](https://github.com/jboehm1/timelapse/actions/runs/41190162334). All three instrumentation tests passed. Host Pillow decoded and hash/length-checked 12 emulator JPEGs at 1856×1392. The workspace test passed project creation, planning, finite capture, Test Photo, gallery, rename, preset copy, trash/restore and Photolapse. None, Motion Blur and Light Trails MP4s decoded at 640×360 with four samples and 133333 μs duration.
- Export performance on the software emulator: 30 frames; source JPEG decodes 88→30; 58 cache hits; peak cache 2,764,800 bytes; elapsed 1010→606 ms. This single emulator comparison does not predict S21 speed.
- Two-day batch cancellation kept day one, left zero pending rows and preserved source hashes.
- Refreshed screenshot and JSON evidence is in commit `9f955557699277c06e4057294a169bbd1c497427`. Workflows created for this evidence-only commit are marked `action_required` with no jobs; the validated app source is the parent `ec6daae`.

## Local checks and progress

- `git diff --check`, `python3 tools/progress.py --write` and `python3 tools/progress.py --check` pass.
- The weighted tracker remains **23%**, with **14/233** items at 100%. No score increased because these results do not meet the item-specific physical-device acceptance criteria.
- `bash tools/test-policy.sh` could not run locally because `kotlinc` is absent; there is no local Gradle executable or wrapper. The corresponding CI policy and Android workflows passed.

## Remaining blocker

**CAPTURE-P0-001 remains OPEN.** Emulator evidence does not validate a Galaxy S21. A physical S21 must save ten consecutive valid JPEGs, retain them after stop/reopen, report real dimensions and EXIF/timestamps, and pass screen-off plus lens/resolution checks. The step-by-step protocol is in `docs/TEST_PLAN.md`.

Do not merge PR #2 or publish an app until the S21 and other required hardware gates pass.

## Follow-up — moving-person export fixture and evidence documents

- PR #2 remains open/unmerged. The branch head `5b41a9ef1f943ea4e1aa0b9a213cdcbf77c6a7b2` adds a deterministic seven-frame, left-to-right walking-worker fixture on a fixed construction-site background. It encodes the same originals in Normal and Motion Blur, checks the worker centroid moves across source frames, checks output metadata and middle-frame visual difference, and asks the host verifier to hash, probe and decode the MP4 files. It is explicitly synthetic and does not use the emulator camera or S21 camera.
- Android build/unit/lint: **PASS**, [run #41191466063](https://github.com/jboehm1/timelapse/actions/runs/41191466063). Pure Kotlin policy tests: **PASS**, [run #41191466117](https://github.com/jboehm1/timelapse/actions/runs/41191466117).
- [Emulator run #41191466072](https://github.com/jboehm1/timelapse/actions/runs/41191466072) passed all four instrumentation tests. The fixture produced Normal 21,808 B and Motion Blur 21,728 B H.264 MP4s (640×360, 7 fps, seven frames, 1 s). Seven source worker centroids moved monotonically from x=114.5 to x=522.5; the selected output frame differed by 17,815 pixels above the RGB threshold. The artifact was downloaded and both files independently passed local ffprobe/ffmpeg 6.1.1 probing and full decode.
- The emulator runner did not have ffmpeg/ffprobe, so its JSON recorded both host checks as false even though the older verifier still permitted that. The workflow now installs FFmpeg and the host verifier fails if either tool is absent; a second full emulator run is required to validate that stricter pipeline.
- Latest prior emulator Normal and Motion Blur camera-simulator exports are 640×360, four samples, 133333 μs: 8563 bytes (`NONE`, SHA-256 `777ff94438f43bfde33755aeea2ffb3e3e331c0e0df2f0a6c69b71b10c9b65e1`) and 7083 bytes (`MOTION_BLUR`, SHA-256 `6887f9ad5fef44351c80e488022bb8e053ab65e7907cd383770a06a4221e0038`). They are separate from the new walking-person inputs. Both are emulator-only.
- Added versioned ADR, risk, S21 installation, field operations, focus/exposure, proposed local API, device evidence, release gate and changelog documents. The S21 record remains `NOT RUN`; the API document is a proposal, not implemented service behavior.
- Evidence-based documentation scores moved K-09–K-15 and K-17 to 100%; K-16 is 55% because the register and entry template exist but the physical S21 row is not populated. Relevant passing tests then moved B-06/07/08/10/11, C-04, E-03, F-01/03/04/05/10/11/12/20/23/24/29, G-01, H-01 and I-07 to the automated-test stage of 55%. The tracker is **29% weighted, 22/233 tasks at 100%**. Per-item evidence is in the records above and the linked CI runs. The checklist score remains separate from product release readiness.
- `git diff --check`, verifier `py_compile`, emulator shell `bash -n`, and `python3 tools/progress.py --write/--check` pass locally. The local checkout has no `kotlinc`, Gradle or Android SDK executable; hosted workflows provide the code/test results. No S21 device result is available.
