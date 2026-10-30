# Mandatory working rules for humans and AI agents

This file is binding for all changes to this repository.

1. Read **README.md**, **docs/PROJECT_SPEC.md**, **docs/MASTER_TODO.md**, and **docs/OPEN_SOURCE_REUSE.md** completely before touching code. Read **docs/PRO_PARITY.md** for the complete PRO feature contract; read **docs/ARCHITECTURE.md** and **docs/TEST_PLAN.md** for any camera, scheduler, export or lifecycle change.
2. Treat the requirement IDs in PROJECT_SPEC as the source of truth. Changes to requirements require a documented decision in the same PR, not silent feature changes.
3. Never claim a feature is implemented, verified or production-ready unless the corresponding tests and evidence are linked. Distinguish: proposed / implemented / compiled / emulator-tested / device-tested / soak-tested.
4. Reliability and preservation of original frames override visual effects, FPS, exact timing and power efficiency. Do not drop or overwrite valid originals to recover from an export error.
5. Do not add proprietary APKs, decompiled application code, icons, trademarks, image assets, signing keys or credentials to this public repository. Velocity Lapse serves only as observed functional reference. Implement independently.
6. Camera access and scheduling must respect Android 14+ foreground-service camera and while-in-use restrictions. No covert workarounds. No claims of silent cold start after reboot on Android 15.
7. Keep core policy deterministic and independently testable without Android. Implement bounded retries, explicit stop/degrade decisions, metrics and failure injection.
8. Never make capture dependent on network connectivity; external uploads and remote control are optional and isolated.
9. No unbounded allocations, no persistent full-resolution bitmaps, no frame accumulation in RAM, no UI-thread image encoding. Limit queue length and retry counts.
10. Record frame paths and successful write timestamps durably. Prefer atomic temp-file rename and recover incomplete files on startup. Include storage-pressure stop thresholds.
11. A camera stall, fatal thermal state, storage exhaustion or user Stop must produce a clearly visible state and logs. Do not restart forever.
12. Code changes require unit tests for pure logic and review of the device test matrix. PR must note limitations and exact validations done vs not done.
13. The initial device target is Samsung Galaxy S21 (Android 15). Test on-device explicitly; generic emulator success does not validate Samsung power/camera behavior.
14. Update the status matrix and changelog in docs when implementing milestones. Keep the main README short and link to authoritative docs.
15. Use feature branches + PRs; protect `main` after bootstrap, and never merge unverified long-session claims.
16. In every code PR, update **docs/MASTER_TODO.csv** item scores to the *highest evidenced stage*, regenerate the dashboard with `python tools/progress.py --write`, and run `python tools/progress.py --check`. Note the affected item IDs and build/test evidence in the PR. Never advance a score solely because new source code was written; 25% is the uncompiled prototype maximum. 100% requires the item-specific acceptance test, not the mere existence of code.
17. Every feature should have a measurable acceptance criterion (testable input, expected output, failure behavior), not a subjective success claim.

## Release gates
- **Prototype:** builds, core unit tests, short manual camera test.
- **Alpha:** no crash in 8-hour daylight run, proper export, frame accounting.
- **Beta:** scheduled day/night with screen OFF, thermal and storage fault injection, at least 72-hour on-device soak, verified recovery policy.
- **Stable:** repeatable >=7-day soak under specified equipment and OS build, recovered frames measured, lifecycle/reboot limitations explicitly documented.

No smartphone Android app can honestly guarantee literally zero missed frames through physical power loss, OS force-stop, hardware failure or shutdown.

18. Before writing new camera, encoder, scheduling, storage or networking infrastructure, review OPEN_SOURCE_REUSE for existing official Apache/MIT alternatives. Source imports require upstream SHA, copyright, full license obligations and tests; reject GPL code unless the project owner explicitly chooses GPL-compatible distribution.
19. Keep the pure-JVM FrameStore integrity and TimelapseLogic policy tests passing; their passing does NOT substitute for an Android APK and physical S21 test.


## Absolute release blocker: real JPEG capture (user report 2026-10-30)
**P0 / STOP THE LINE.** The user reports that pressing **Start / Record does not actually save photographs** on the Samsung S21. A green compile, lint result, passing emulator launch, preview image, or successful video export is **not** evidence that the app records.

1. **Do not mark a capture feature verified, declare feature completion, make a public GitHub Release, publish to Google Play, or promote/share any new APK as a usable alpha/beta/stable app until a real recording test passes.** CI may generate *diagnostic/internal testing* build artifacts clearly labelled UNVERIFIED, but these must never be presented as a finished recording product.
2. A successful Start must produce a durable, valid JPEG **in the selected project's originals directory**, and the UI may show **RECORDING / ● red** only after camera binding is confirmed and actual JPEG commits succeed. If no frame arrives, show explicit **STARTING / CAMERA ERROR / WAITING / NO FRAMES SAVED**, with remediation; never leave silent false success.
3. Required gate **CAPTURE-P0-001**: on the S21, select/create project, set 2–5 s interval, press Start, wait for **10 consecutive verified JPEG files**, verify their real dimensions, JPEG decoding, EXIF/timestamps, nonzero lengths and filename uniqueness; stop/reopen and verify all files persist. Repeat with physical screen OFF and with lens/resolution changes. On emulator, repeat with emulated camera to catch regressions.
4. Record logcat, camera ID/capabilities, permissions, actual saved path, source SHA/APK SHA, in-flight retries and exception stack traces. Instrument the complete button → intent → FGS → CameraX bind → capture callback → atomic file store → listing path chain. Investigate no-output before doing cosmetic polish.
5. The green workflow run #40978329365 verifies compilation/testing/lint, while #40978329505 verifies launch/screenshots only; **neither validates saved images**. All claims and handbooks must retain this limitation until overwritten by reproducible device evidence.
6. After capture works, finish UI design review, capacity estimates, Motion Blur controls, editor/project gallery; then thermal/night/endurance. For upstream reference use the official Velocity Lapse Play listing and guide, plus lawful black-box comparison; never copy its protected source/assets.
7. **Do not merge PR #2 into main** merely to expose new documents or screenshots: keep documents committed to the feature branch until actual recording is proven and the release gate is met.
