# Changelog

This is an engineering changelog for the feature branch. It does not describe a public release. Commits are linked to roadmap items; acceptance scores remain in [MASTER_TODO.csv](MASTER_TODO.csv).

## Unreleased — diagnostic alpha follow-up

- **`5b41a9e` — deterministic walking-person export simulation.** Added seven synthetic left-to-right worker frames, Normal and Motion Blur MP4 comparison, Android frame/metadata assertions, and host hash/probe/decode verification. Related: G-01, G-20–G-22, J-03. Emulator-only; no S21 evidence.
- **`404e4c7` — reconcile originals after journal failure.** Validate file structure with exact reads, reconcile durable files whose journal append failed, and report missing journaled originals. Related: B-10, B-11, F-10, F-11, F-23.
- **`cc8f031` — original sequence/ZIP export.** Added lossless original JPEG/PNG sequence and ZIP code/tests. A test compilation omission was corrected in commit `5aca983`; the current PR source compiles/tests with explicit output callbacks. Related: B-06, B-07, F-12, I-07.
- **`ec6daae` — stabilize export tests and workspace automation.** Stabilized UI action selectors and passed the earlier emulator project journey. Related: B-01, B-02, B-15, H-01, H-12.
- **`741ada4` — bounded export frame reuse.** Reused decoded neighbors within a bounded cache and added measured emulator comparisons. Related: G-20–G-22.

## Diagnostic v0.3.1 — not a release

Feature history includes project workspace, finite capture and Test Photo, planning estimates, daily schedule logic, CameraX foreground capture, lossless originals, MP4 effects and day batches. See [the PR](https://github.com/jboehm1/timelapse/pull/2), [continuation reports](CONTINUATION_2026-10-31.md), and [export measurements](EXPORT_PERFORMANCE_2026-10-30.md). Samsung Galaxy S21 zero-JPEG report remains an absolute release blocker.
