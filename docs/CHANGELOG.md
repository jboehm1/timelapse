# Changelog

This is an engineering changelog for the feature branch. It does not describe a public release. Commits are linked to roadmap items; acceptance scores remain in [MASTER_TODO.csv](MASTER_TODO.csv).

## Unreleased — diagnostic alpha follow-up

- **`41192228191` — strict independent MP4 verification.** Emulator suite passed 4/4; ffprobe and ffmpeg are now installed and required in CI. The seven-frame synthetic walking-person Normal and Motion Blur outputs fully decoded; 13 emulator-camera JPEGs passed host validation. The refreshed measurements are recorded in `DEVICE_TEST_EVIDENCE.md`. No physical device claims.
- **`6b8885c` — local privacy guard and original export safety.** Android run #41192916101 and pure Kotlin run #41192916287 passed. Added nine permission/network policy tests and a fourth single-compile core suite; a failed original sequence removes only its partial destination. Evidence-backed tracker advances are recorded in MASTER_TODO; G-02/G-08 remain at emulator-test stage because encoder software identity was not hardware-verified.
- **Pending — camera-generated original sequence/ZIP integration.** Extend recording smoke to export emulator-captured JPEGs as individual files and a ZIP, decode every output, and compare source/output hashes. Emulator CI on this addition is pending.
- **Completed on `6b8885c` — local-only permission policy.** Android PR CI ran nine permission/network policy tests and a manifest/source scan successfully; the emulator's synthetic video was independently probed and fully decoded. Run #41192916063 confirms the CI host has the required FFmpeg decoders.
- **Pending — exporter failure recovery and expanded simulation.** Delete only the current partial sequence/ZIP SAF destination after a failure; preserve prior exports and originals. Add captured-camera JPEG sequence/ZIP validation, independent probing/decoding of every emitted MP4 and the two-worker walking-person variant. Expand the catalogue lifecycle test to 105 projects. Fresh Android, Kotlin policy and emulator CI are still required; no score has moved from implementation alone.
- **`5b41a9e` — deterministic walking-person export simulation.** Added seven synthetic left-to-right worker frames, Normal and Motion Blur MP4 comparison, Android frame/metadata assertions, and host hash/probe/decode verification. Related: G-01, G-20–G-22, J-03. Emulator-only; no S21 evidence.
- **`404e4c7` — reconcile originals after journal failure.** Validate file structure with exact reads, reconcile durable files whose journal append failed, and report missing journaled originals. Related: B-10, B-11, F-10, F-11, F-23.
- **`cc8f031` — original sequence/ZIP export.** Added lossless original JPEG/PNG sequence and ZIP code/tests. A test compilation omission was corrected in commit `5aca983`; the current PR source compiles/tests with explicit output callbacks. Related: B-06, B-07, F-12, I-07.
- **`ec6daae` — stabilize export tests and workspace automation.** Stabilized UI action selectors and passed the earlier emulator project journey. Related: B-01, B-02, B-15, H-01, H-12.
- **`741ada4` — bounded export frame reuse.** Reused decoded neighbors within a bounded cache and added measured emulator comparisons. Related: G-20–G-22.

## Diagnostic v0.3.1 — not a release

Feature history includes project workspace, finite capture and Test Photo, planning estimates, daily schedule logic, CameraX foreground capture, lossless originals, MP4 effects and day batches. See [the PR](https://github.com/jboehm1/timelapse/pull/2), [continuation reports](CONTINUATION_2026-10-31.md), and [export measurements](EXPORT_PERFORMANCE_2026-10-30.md). Samsung Galaxy S21 zero-JPEG report remains an absolute release blocker.
