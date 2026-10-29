# Timelapse — construction camera for Android

Independent, **reliability-first** timelapse application for construction sites, initially targeting **Samsung Galaxy S21 / Android 15**.

> **Source of truth:** [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md). All development, reviews, and test plans MUST respect the numbered requirements there. [AGENTS.md](AGENTS.md) defines the required developer workflow.

## Non-negotiable goals
- Capture original JPEG frames and retain them across exports, with uninterrupted, resumable projects.
- Export **image sequence** or **MP4**, with **None / Motion Blur / Light Trails**, configurable resolution, bitrate and FPS.
- **Tap-to-focus**, independently selectable **tap-to-expose** or **both**; never silently confuse focus with exposure.
- Construction-specific daily schedules (e.g. 07:00–18:00). **Screen physically OFF** during overnight standby whenever validated. A black UI is only an optional fallback, not a requirement.
- Recover safely from camera errors, overheating, storage shortages, process death and delays. Never claim unlimited or guaranteed unattended runtime on stock Android.
- Safety before frame rate: no unbounded queues, no aggressive automatic restarts, no data loss on a failed export, no OS permission bypass.

## Honest status
**Project bootstrap / pre-alpha.** Requirements and validation policy are being established. Android builds, hardware compatibility, night wake-up and multi-day reliability must be measured on real hardware before advertising them as supported.

## Required documents
- **[Product contract / acceptance criteria](docs/PROJECT_SPEC.md)** — authoritative.
- [Architecture and day/night strategy](docs/ARCHITECTURE.md).
- [Reliability and soak-test plan](docs/TEST_PLAN.md).
- [AI/developer working rules](AGENTS.md).
- [Technical source references](docs/REFERENCES.md).

## Reference application
Velocity Lapse is a **feature reference**, not a code source. Do **not** commit its proprietary APK, resources or decompiled code into this public repository. Use user-observed UI and public documentation to independently implement equivalent behavior.

## Builds
Builds will be enabled through the GitHub Actions workflow once the Android scaffold is in place. A green JVM test suite is not proof of Samsung S21 camera reliability. An APK isn't production-ready until the device tests in the spec pass.
