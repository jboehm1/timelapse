# Diagnostic and release checklist

This checklist is a gate, not a declaration that the current app is releasable. Current version `0.3.1-diagnostic-unverified` is internal diagnostic output.

## Before any public or usable-app release

- [ ] CAPTURE-P0-001 passed on the physical Samsung Galaxy S21: ten consecutive valid JPEGs, actual dimensions and EXIF/timestamps, unique IDs/hashes, project listing, persistence after stop/reopen, physical display OFF, lens and resolution changes.
- [ ] Source SHA, signed artifact SHA-256, device build fingerprint, camera IDs/capabilities, logs and metrics attached to the evidence register.
- [ ] No false RECORDING state, unbounded retries/allocations, critical data-loss issue, or unresolved P0 crash remains.
- [ ] Storage failure, camera timeout/busy, process/activity interruption, thermal state, export destination failure, cancellation and recovery have automated fault-injection coverage.
- [ ] 8-hour daylight alpha gate passed. Before beta: 72-hour scheduled screen-off/device fault run. Before stable: two repeatable seven-day runs with measured losses and explicit Android/OEM limitations.
- [ ] Original images remain unchanged after sequence, ZIP, MP4, cancellation and failed-destination cases; exported MP4s are independently decoded and measured.
- [ ] Camera, notification, media and network permissions reviewed against actual features. No credentials, proprietary APKs, private site images or signing keys are in Git.
- [ ] Direct and transitive dependencies, versions, notices and licenses audited; unresolved licensing choices documented.
- [ ] Release keystore managed outside the repository; debug/release application IDs and signatures separated; update and rollback path tested without deleting originals.
- [ ] Accessibility, translation, device installation/update, privacy and support documents reviewed for the exact release build.
- [ ] CI and tracker pass on the exact release source SHA. Debug CI APKs remain clearly labelled diagnostic and unverified.
- [ ] PR reviews and product/release approval are complete. Keep PR #2 unmerged until its physical and release gates pass.

## Current blockers

CAPTURE-P0-001 is open. No S21 results, physical screen-off evidence, long-session soak, commercial signing, or public release artifact is recorded. See [DEVICE_TEST_EVIDENCE.md](DEVICE_TEST_EVIDENCE.md), [RISK_REGISTER.md](RISK_REGISTER.md) and [TEST_PLAN.md](TEST_PLAN.md).
