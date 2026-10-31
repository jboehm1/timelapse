# Architecture decision records

This log records choices that affect reliability, data compatibility, licensing, or release gates. It complements the implementation notes in [ARCHITECTURE.md](ARCHITECTURE.md). Status describes the decision, not proof that the feature works on every device.

## ADR-001 — Keep original frames as the durable source

- **Status:** Accepted.
- **Decision:** Save each source image as an independently named original, validate it, publish it atomically, then append an integrity journal. Generate video and image exports from copies/streams; never modify or delete originals to recover from an export failure.
- **Reason:** A failed encoder or destination must not destroy irreplaceable construction evidence.
- **Validation:** `FrameStoreTest`, `CoreReliabilityTest`, and `OriginalPhotoExporterTest`; see [TEST_PLAN.md](TEST_PLAN.md) and the continuation report. Physical storage-removal and power-loss tests remain outstanding.

## ADR-002 — Start camera foreground service from a visible Activity

- **Status:** Accepted with a device-validation gate.
- **Decision:** The user starts capture while the app is visible and grants Android camera permission. The lifecycle-owned camera foreground service may continue after the Activity backgrounds. Do not start a camera service silently from boot or bypass Android while-in-use restrictions.
- **Reason:** Android 14+ camera foreground service rules constrain background starts, and Android 15 further limits boot-triggered camera services.
- **Validation:** Emulator recording/background/reopen test exists. Screen physically OFF, night camera rebind and morning continuation remain unverified on Galaxy S21 Android 15.

## ADR-003 — Treat missing first frame as a failed start

- **Status:** Accepted; physical S21 issue remains open.
- **Decision:** Keep the UI in STARTING until a new JPEG has been decoded, committed, and listed. After a bounded first-frame timeout, stop claiming recording and surface a recovery action and diagnostics.
- **Reason:** A red RECORDING indicator without a saved source frame is misleading and hides the reported zero-JPEG failure.
- **Validation:** Emulator success/failure scenarios are regression tests only. CAPTURE-P0-001 has not passed on S21.

## ADR-004 — Preserve capture cadence and originals over catch-up speed

- **Status:** Accepted.
- **Decision:** Keep only one capture in flight; skip missed deadlines rather than queueing a burst. Increase intervals or pause after safety limits. Export uses bounded image caches and leaves originals untouched.
- **Reason:** Backlogs increase heat and memory pressure and can make a timelapse appear to have captured evenly when it did not.
- **Validation:** JVM deadline, thermal, safety-policy and export-cache tests; long S21 runs remain required.

## ADR-005 — Keep CameraX and the current streaming encoder for now

- **Status:** Accepted for the diagnostic alpha; re-evaluate with measurements.
- **Decision:** Use CameraX for capture and MediaCodec/EGL for streaming video composition. Do not add Media3 or another camera backend solely on theoretical performance grounds.
- **Reason:** The current code supports still originals and temporal effects with bounded memory. Any replacement must pass comparison tests for long sequences, effects, cancellation, output compatibility, and device performance.
- **Validation:** Emulator MP4 decode and cache measurements are documented in [EXPORT_PERFORMANCE_2026-10-30.md](EXPORT_PERFORMANCE_2026-10-30.md). The Samsung encoder and 4K/8K thermal cost are not measured.

## ADR-006 — Local storage is the default; network control is future work

- **Status:** Accepted.
- **Decision:** Capture and export operate locally. Do not add upload, remote control, or analytics to the capture path. Any future LAN control must be opt-in, authenticated, local-network scoped, and documented before implementation.
- **Reason:** A site camera must continue recording without Internet and must not expose construction images by default.
- **Validation:** Current workflows exercise local project files. A LAN-control service has not been implemented.

## ADR-007 — No release until the S21 capture blocker is closed

- **Status:** Accepted, blocking.
- **Decision:** Keep PR #2 unmerged and do not publish or promote the diagnostic APK until the complete S21 acceptance run, including restart, physical screen-off, and lens/resolution checks, passes.
- **Reason:** The owner reported Start produced zero JPEGs on a Galaxy S21.
- **Validation:** See issue [#3](https://github.com/jboehm1/timelapse/issues/3) and the device protocol in [TEST_PLAN.md](TEST_PLAN.md).
