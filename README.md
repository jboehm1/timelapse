# Timelapse — construction camera for Android

Independent, **reliability-first** timelapse application for construction sites, initially targeting **Samsung Galaxy S21 / Android 15**.

> **Source of truth:** [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md). All development, reviews, and test plans MUST respect the numbered requirements there. [AGENTS.md](AGENTS.md) defines the required developer workflow.

## Roadmap exhaustive et progression vérifiable

- **[MASTER_TODO.md : 233 tâches individuelles avec % de progression, priorités, critères de réussite et score global](docs/MASTER_TODO.md)**
- [MASTER_TODO.csv : liste canonique modifiable](docs/MASTER_TODO.csv)
- Mise à jour du tableau de bord : `python tools/progress.py --write`, validation `python tools/progress.py --check`.
- **Score de référence au 2026-10-29 : 14 % pondéré, 10/233 éléments à 100 % (documents uniquement)**. Ce score n'est pas une déclaration de conformité fonctionnelle : **APK Android non compilé / tests S21 non réalisés**.

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
**Pre-alpha (uncompiled Android prototype).** The feature branch contains Android CameraX capture/service/UI and export source, but **NO BUILT OR DEVICE-VALIDATED APK**. Pure-JVM policies, frame-integrity code and temporal blur-window logic have passed local Kotlin smoke tests. The GitHub Actions jobs in this private repository fail **before a runner starts**; those failures are not a successful Android build. Screen-off recording, automatic morning wake-up and multi-day endurance are unverified and must not be advertised as supported.

## Required documents
- **[Product contract / acceptance criteria](docs/PROJECT_SPEC.md)** — authoritative.
- **[Full Velocity Lapse PRO feature-parity matrix](docs/PRO_PARITY.md)** — mandatory, implemented/verified status tracked.
- [Architecture and day/night strategy](docs/ARCHITECTURE.md).
- [Reliability and soak-test plan](docs/TEST_PLAN.md).
- [AI/developer working rules](AGENTS.md).
- [Technical source references](docs/REFERENCES.md).

## Reference application
Velocity Lapse is a **feature reference**, not a code source. Do **not** commit its proprietary APK, resources or decompiled code into this public repository. Use user-observed UI and public documentation to independently implement equivalent behavior.

## Builds
Pure JVM policy checks run via `bash tools/test-policy.sh` (requires Kotlin CLI and JDK 17), and GitHub Actions runs them on pushes/PRs. The Android application/build pipeline has **not** been migrated into this new repository. A green JVM suite is not proof of Samsung S21 camera reliability. An APK isn't production-ready until the device tests in the spec pass.


## Repository visibility and build availability (2026-10-29)
The repository is currently **private**. Recent GitHub Actions runs failed before starting any runner steps, so those failures provide **no proof** about Kotlin/Android compilation; they are consistent with runner allocation or account billing/usage restrictions. Do not claim the debug APK was built. For proprietary/commercial ambitions, keep this repository private and use a developer machine, self-hosted runner or paid hosted minutes; public runners are only free when the **repository is public**.


### Build an APK on Windows without GitHub Actions
On an Internet-connected Windows PC, check out branch `feat/android-capture-export-alpha` and run:

```powershell
powershell -ExecutionPolicy Bypass -File tools/build-windows.ps1
```

The script downloads official Android command-line tools, Gradle and Java if absent, asks for SDK licence acceptance, runs the local Android test+build tasks and, **only upon success**, copies `app-debug.apk` to `dist/Timelapse-debug.apk`. It does not require hosted GitHub Actions. The current chat runtime has no Android SDK or downloadable external build dependencies; a compiled APK has **not** been produced or validated here. If the build fails, keep its Gradle output for troubleshooting rather than claiming APK success.

### Linux build (without GitHub-hosted runners)

On a Linux PC with outbound HTTPS and Java already installed:

```bash
bash tools/build-linux.sh
```

The script verifies checksums of the official Android command-line SDK and Gradle, requests SDK licence acceptance and builds the debug APK to dist/Timelapse-debug.apk. The current chat container cannot reach download servers; this **has not been built here**. Pure Kotlin logic can be tested locally with bash tools/test-policy.sh even without Android SDK.
