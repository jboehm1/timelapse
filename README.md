> **Copyright / usage:** The project's original source code is publicly viewable but **not licensed for third-party reuse or redistribution**. See [COPYRIGHT.md](COPYRIGHT.md). Third-party components retain their own licenses.

# Timelapse — construction camera for Android

## Critical capture blocker and full engineering handoff (2026-10-30)

**STOP THE LINE:** The user reports that pressing Start/Record on the Samsung Galaxy S21 saves **zero photos**. Successful Gradle/lint and emulator screenshot runs are NOT proofs of recording. **No public release or usable APK claim until ten validated JPEGs are saved and survive restart on the real S21**, with the reproducible evidence in [issue #3](https://github.com/jboehm1/timelapse/issues/3). Internal diagnostic APK artifacts are for debugging only.

**[Complete self-contained new-chat engineering handbook](docs/HANDOFF_MASTER_2026-10-30.md)** (deep UI rebuild, camera controls, reference Velocity Lapse study, capacity estimates, release and test gates). Baseline emulator screenshots: [home](docs/screenshots/emulator/app-home.png), [settings](docs/screenshots/emulator/app-settings.png), [settings scroll](docs/screenshots/emulator/app-settings-scroll.png). These images may only appear once the emulator screenshot publishing workflow finishes.

Independent, **reliability-first** timelapse application for construction sites, initially targeting **Samsung Galaxy S21 / Android 15**.

> **Source of truth:** [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md). All development, reviews, and test plans MUST respect the numbered requirements there. [AGENTS.md](AGENTS.md) defines the required developer workflow.

## Roadmap exhaustive et progression vérifiable

- **[MASTER_TODO.md : 233 tâches individuelles avec % de progression, priorités, critères de réussite et score global](docs/MASTER_TODO.md)**
- [MASTER_TODO.csv : liste canonique modifiable](docs/MASTER_TODO.csv)
- Mise à jour du tableau de bord : `python tools/progress.py --write`, validation `python tools/progress.py --check`.
- **Progression actuelle : 23 % pondérés, 14/233 tâches entièrement acceptées.** Ce score mesure les critères de la feuille de route, pas le volume de code. Le blocage de capture sur Galaxy S21 reste ouvert ; les APK CI sont diagnostiques et non vérifiés sur téléphone. Voir le tableau de bord pour le détail.

## Reuse of mature open-source components

Our evaluated component inventory is [OPEN_SOURCE_REUSE.md](docs/OPEN_SOURCE_REUSE.md): official CameraX / Camera2 samples, Apache-2.0 OpenCineCam, MIT Simple-Motion-Cam, Jetpack Media3/Room/DataStore/WorkManager. **GPL timelapse apps are behavioral references, not drop-in modules for a closed-source commercial APK.** Prefer selecting small proven modules instead of merging entire competing camera applications.

## Non-negotiable goals
- Capture original JPEG frames and retain them across exports, with uninterrupted, resumable projects.
- Export **image sequence** or **MP4**, with **None / Motion Blur / Light Trails**, configurable resolution, bitrate and FPS.
- **Tap-to-focus**, independently selectable **tap-to-expose** or **both**; never silently confuse focus with exposure.
- Construction-specific daily schedules (e.g. 07:00–18:00). **Screen physically OFF** during overnight standby whenever validated. A black UI is only an optional fallback, not a requirement.
- Recover safely from camera errors, overheating, storage shortages, process death and delays. Never claim unlimited or guaranteed unattended runtime on stock Android.
- Safety before frame rate: no unbounded queues, no aggressive automatic restarts, no data loss on a failed export, no OS permission bypass.

## Honest status
**Diagnostic v0.3.1: emulator-tested, NOT device-validated.** Android compilation, 30 JVM tests, lint, core policy checks and three emulator scenarios passed for source [`741ada4`](https://github.com/jboehm1/timelapse/commit/741ada488c56e534408275e98ab1871b98c8f47d). The emulator produced and independently validated JPEGs and MP4s; this does not prove S21 camera operation. **CAPTURE-P0-001 remains open:** capture, AF/AE behavior, physical screen-off scheduling, battery and long-run endurance have not been verified on the Samsung S21. CI APKs are internal diagnostics only, not a usable release.

## Required documents
- **[Product contract / acceptance criteria](docs/PROJECT_SPEC.md)** — authoritative.
- **[Full Velocity Lapse PRO feature-parity matrix](docs/PRO_PARITY.md)** — mandatory, implemented/verified status tracked.
- [Architecture and day/night strategy](docs/ARCHITECTURE.md).
- [Reliability and soak-test plan](docs/TEST_PLAN.md).
- [AI/developer working rules](AGENTS.md).
- [Technical source references](docs/REFERENCES.md).
- [Architecture decisions and risk register](docs/DECISIONS.md) · [Known product risks](docs/RISK_REGISTER.md).
- [S21 diagnostic install guide](docs/S21_INSTALLATION.md) · [Field operation guide](docs/FIELD_OPERATIONS.md) · [Focus/exposure guide](docs/FOCUS_EXPOSURE_GUIDE.md).
- [Device evidence register](docs/DEVICE_TEST_EVIDENCE.md) · [Release gates](docs/RELEASE_CHECKLIST.md) · [Engineering changelog](docs/CHANGELOG.md).

## Reference application
Velocity Lapse is a **feature reference**, not a code source. Do **not** commit its proprietary APK, resources or decompiled code into this public repository. Use user-observed UI and public documentation to independently implement equivalent behavior.

## Builds and fast feedback
Pure JVM policy checks run via `bash tools/test-policy.sh`; one Kotlin compiler invocation runs all three deterministic suites and checks the progress tracker. The policy workflow caches the pinned Kotlin CLI compiler between runs. Android PR CI runs JVM tests, debug APK compilation and lint; emulator CI runs the recording, export and workspace flows with independent host-side JPEG/MP4 validation. The emulator suite is separate because it needs an Android virtual device and its camera simulation. Green CI **does not** validate real camera operation on Samsung S21. See [TEST_PLAN.md](docs/TEST_PLAN.md) for scopes and remaining hardware gates.


## Repository visibility and build availability (2026-10-30)
The repository is **PUBLIC**, and GitHub-hosted standard runners are available. The original project remains copyright-reserved, without an open-source reuse grant. Public source can still be viewed and copied technically, so commercialization choices require deliberate source-exposure review. Do not treat diagnostic builds as stable production apps.


### Build an APK on Windows without GitHub Actions
On an Internet-connected Windows PC, check out branch `feat/android-capture-export-alpha` and run:

```powershell
powershell -ExecutionPolicy Bypass -File tools/build-windows.ps1
```

The script downloads official Android command-line tools, Gradle and Java if absent, asks for SDK licence acceptance, runs the local Android test+build tasks and, **only upon success**, copies `app-debug.apk` to `dist/Timelapse-debug.apk`. It does not require hosted GitHub Actions. If the build fails, keep its Gradle output for troubleshooting rather than claiming APK success.

### Linux build (without GitHub-hosted runners)

On a Linux PC with outbound HTTPS and Java already installed:

```bash
bash tools/build-linux.sh
```

The script verifies checksums of the official Android command-line SDK and Gradle, requests SDK licence acceptance and builds the debug APK to dist/Timelapse-debug.apk. Pure Kotlin policy logic can also be tested without an Android SDK using `bash tools/test-policy.sh` (with JDK 17 and Kotlin CLI 2.0.21 installed).

## Diagnostic v0.3 workspace

Projects now have a Compose Material 3 workspace: tap the project name below the camera to open the library, photo gallery, reversible trash and capture planner. Configure per-project intervals (nominal 0.5–3600 s), weekdays/time window, unlimited recording or a limit of new photos. Test Photo / Photolapse saves one new original. The planner estimates storage and video duration and learns JPEG size from recent originals. Export has cancellation and a disk preflight.

Implementation/validation record: [WORKSPACE_PROGRESS_2026-10-30.md](docs/WORKSPACE_PROGRESS_2026-10-30.md). These additions do not close the S21 hardware release gate.
