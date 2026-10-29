# Mandatory working rules for humans and AI agents

This file is binding for all changes to this repository.

1. Read **README.md** and **docs/PROJECT_SPEC.md** completely before touching code. Read **docs/ARCHITECTURE.md** and **docs/TEST_PLAN.md** for any camera, scheduler, export or lifecycle change.
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
16. Every feature should have a measurable acceptance criterion (testable input, expected output, failure behavior), not a subjective success claim.

## Release gates
- **Prototype:** builds, core unit tests, short manual camera test.
- **Alpha:** no crash in 8-hour daylight run, proper export, frame accounting.
- **Beta:** scheduled day/night with screen OFF, thermal and storage fault injection, at least 72-hour on-device soak, verified recovery policy.
- **Stable:** repeatable >=7-day soak under specified equipment and OS build, recovered frames measured, lifecycle/reboot limitations explicitly documented.

No smartphone Android app can honestly guarantee literally zero missed frames through physical power loss, OS force-stop, hardware failure or shutdown.
