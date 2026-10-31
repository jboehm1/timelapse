# Changelog

- The merged APK permission gate now allows only the exact non-Internet CameraX/AndroidX permissions observed in CI; `INTERNET` remains rejected.
- Emulator export validation passed four camera-original MP4 rates (24/25/30/60 fps) with independent ffprobe and full decode; G-12 reaches the automated-test stage only.
- Compiled APK permission verification passed after narrowly allowing CameraX/AndroidX's non-Internet merged permissions; I-02/I-03 acceptance evidence is complete for the current code.
- Refreshed the test evidence register with source/checkpoint SHAs, emulator metrics, diagnostic APK hash, workflow links and hardware scope limits (K-16).
- Added an invalid-camera instrumentation assertion for distinct visible saved/error counts (C-16); awaiting emulator CI before scoring.

This is an engineering changelog for the feature branch. It does not describe a public release. Commits are linked to roadmap items; acceptance scores remain in [MASTER_TODO.csv](MASTER_TODO.csv).

## Unreleased — diagnostic alpha follow-up

- **`8971522` — storage/export adversarial checks.** Android build/unit/lint #41194560591 and cached pure Kotlin suites #41194560596 passed, including traversal-name rejection, original preservation, atomic recovery and partial-file quarantine. The strict 10-video emulator evidence run #41194560639 is still pending. Tracker moves B-11/F-23/I-07 to 100% and B-10/F-11 to 90%; S21 remains unverified.
- On those same runs, A-16 lint/static analysis, B-13 measured-size storage forecasting and E-04 timezone/DST policy met their test criteria and moved to 100%.
- Android JUnit's 105-project create/reopen/rename/archive/restore run and the successful emulator workspace journey also complete B-01 and H-12 at 100%; device-specific capture stays gated.
- **Emulator run #41194560639 passed** (source `89715227e7ed71cba1c8abcaa140d3eefeabf605`; artifact #13040670699). All four instrumentation tests, camera JPEG/sequence/ZIP hashes, and independent ffprobe/full-decode checks for all ten H.264 clips passed. Two synthetic workers Normal vs Motion Blur metrics and explicit emulator-only scope are recorded in `DEVICE_TEST_EVIDENCE.md`; B-02 and G-21 now meet their acceptance criteria.
- The same UI run kept the product clearly marked diagnostic and stated the S21 limits (H-03). No release or hardware-capture claim is made.
- Pending verification: the walking-person regression now checks that all seven decoded Normal frames correspond in order to their source JPEGs by worker centroid, in addition to the existing visible Normal/Blur comparison.
- Pending verification: capture UI/diagnostics now report the largest monotonic gap between successful originals; pure Kotlin fault tests and emulator evidence validation were added for this F-24 reliability work.
- Pending verification: FrameStore recovery now separates a torn journal tail from its reconstructed row; synthetic fault tests cover restart and repeat audit for B-10/F-11.
- Pending verification: Android CI now checks permissions merged into the built APK, not only the source manifest, and tests added Internet permission rejection at the compiled artifact boundary.
- Pending verification: four output frame rates (24/25/30/60 fps) now have an end-to-end camera-original MP4 export/probe matrix for G-12.
- Pending verification: moving-worker export tests now compare every Normal frame to its source, exercise Blur windows 1/3/5/7/9 and count prior bright silhouettes in Light Trails; host FFmpeg checks these outputs for G-03/G-05/G-06.

- **`41192228191` — strict independent MP4 verification.** Emulator suite passed 4/4; ffprobe and ffmpeg are now installed and required in CI. The seven-frame synthetic walking-person Normal and Motion Blur outputs fully decoded; 13 emulator-camera JPEGs passed host validation. The refreshed measurements are recorded in `DEVICE_TEST_EVIDENCE.md`. No physical device claims.
- **`6b8885c` — local privacy guard and original export safety.** Android run #41192916101 and pure Kotlin run #41192916287 passed. Added nine permission/network policy tests and a fourth single-compile core suite; a failed original sequence removes only its partial destination. Evidence-backed tracker advances are recorded in MASTER_TODO; G-02/G-08 remain at emulator-test stage because encoder software identity was not hardware-verified.
- **`1243160` — lossless exports and two-worker walking simulation.** Android/JUnit/lint #41193799976, pure Kotlin policy #41193799922 and emulator #41193799927 passed. The emulator exported its captured JPEGs individually and as a ZIP with exact host-verified hashes; a two-worker Normal/Motion Blur comparison and all five effect/person MP4 probes passed. The evidence-based tracker is 33% (35/233 complete); details and limits are in `DEVICE_TEST_EVIDENCE.md` and the continuation report.
- **Completed on `6b8885c` — local-only permission policy.** Android PR CI ran nine permission/network policy tests and a manifest/source scan successfully; the emulator's synthetic video was independently probed and fully decoded. Run #41192916063 confirms the CI host has the required FFmpeg decoders.
- **Completed on `1243160` — project lifecycle scale.** The Android unit suite passed creation/reopen, rename of 105 independent projects, reversible archive of 100, another reopen, restoration and final count checks. No permanent-delete feature is claimed.
- **Pending — adversarial export names and complete MP4 suite.** Reject a literal backslash traversal filename before opening a sequence destination; request ZIP destination cleanup on invalid names. Require ffprobe/full decode for cache benchmarks, one-frame and both day-batch MP4s in addition to effect/person clips. These checks and their new score evidence await the next CI cycle.
- **`5b41a9e` — deterministic walking-person export simulation.** Added seven synthetic left-to-right worker frames, Normal and Motion Blur MP4 comparison, Android frame/metadata assertions, and host hash/probe/decode verification. Related: G-01, G-20–G-22, J-03. Emulator-only; no S21 evidence.
- **`404e4c7` — reconcile originals after journal failure.** Validate file structure with exact reads, reconcile durable files whose journal append failed, and report missing journaled originals. Related: B-10, B-11, F-10, F-11, F-23.
- **`cc8f031` — original sequence/ZIP export.** Added lossless original JPEG/PNG sequence and ZIP code/tests. A test compilation omission was corrected in commit `5aca983`; the current PR source compiles/tests with explicit output callbacks. Related: B-06, B-07, F-12, I-07.
- **`ec6daae` — stabilize export tests and workspace automation.** Stabilized UI action selectors and passed the earlier emulator project journey. Related: B-01, B-02, B-15, H-01, H-12.
- **`741ada4` — bounded export frame reuse.** Reused decoded neighbors within a bounded cache and added measured emulator comparisons. Related: G-20–G-22.

## Diagnostic v0.3.1 — not a release

Feature history includes project workspace, finite capture and Test Photo, planning estimates, daily schedule logic, CameraX foreground capture, lossless originals, MP4 effects and day batches. See [the PR](https://github.com/jboehm1/timelapse/pull/2), [continuation reports](CONTINUATION_2026-10-31.md), and [export measurements](EXPORT_PERFORMANCE_2026-10-30.md). Samsung Galaxy S21 zero-JPEG report remains an absolute release blocker.
