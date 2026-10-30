# Diagnostic v0.3 — projects, planning and actual export validation

Baseline: `db0e482`, PR #2. All changes remain on `feat/android-capture-export-alpha`; no release/merge.

## Implemented and compiled locally

- Compose Material 3 projects workspace entered by tapping the project name under the camera. Search and Timelapse/Photolapse/Trash filters, create/rename, copy presets without copying originals, reversible trash/restore. Existing project IDs and original paths stay unchanged. Corrupt metadata falls back with a warning, without hiding originals.
- Coil local-file thumbnails and original preview, lazy/paged gallery, project counts and stored bytes. No network module or new app permission.
- Pure Kotlin planner: nominal intervals down to 500 ms, active-day counts, photo/video storage and playback duration, actual JPEG median, disk runway with 512 MiB/5% reserve and 20% JPEG-size allowance. Fixed sessions use exact new-frame limit; unlimited sessions have explicit projections.
- Per-project interval/daily window/weekdays/frame-limit persistence, reused by existing camera UI/service. No OS background camera permission bypass.
- Test Photo and Photolapse: exactly one new original. Finite recording stops at N new commits. Notification Stop action.
- MP4 cancellation, disk preflight, preserved originals and propagated muxer-finalization errors. Visible diagnostic export buttons corrected (old vertical-parent button width was zero).

## Local evidence

`testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` PASS. **15 JUnit tests**: 5 existing reliability tests and 10 planner/library/export-budget tests. Coverage includes 105 projects, stable original bytes/path through rename/trash/restore, legacy compatibility, damaged metadata, path confinement, independent preset duplication, precise 0.5/2-second daily counts, fixed frame totals and disk reserves.

## Expanded emulator gate (execution pending at this checkpoint)

Three instrumentation cases:
1. Real UI capture + background/persistence + host JPEG hashes. In addition, encode four actual camera originals with None/Motion Blur/Light Trails, check AVC dimensions/duration/sample timestamps and decode an MP4 frame with Android MediaMetadataRetriever. Cancel a separate export before encoder startup and compare original hashes afterward.
2. Unavailable-camera failure without false Recording.
3. Compose UI create → planner → 500 ms preset → stop at 3 new photos → test photo adds exactly 1 → gallery → rename → empty preset copy → trash → restore. Compare all four original hashes. Screenshot every key screen.

Host verifier requires actual JPEG and MP4 archives plus project evidence; missing bytes/incorrect counts or hashes fail CI. S21, physical display OFF and 8h/72h/7d remain unverified.

## Scope still unfinished

Full Compose camera controls, RAW, external SD persistence, advanced color/monitoring, full timeline/editor, real hardware export quality/4K/8K, unattended morning wake, long soaks, localization/accessibility certification and commercial signing remain incomplete. Reversible trash does not permanently delete or reclaim storage. Metadata is filesystem-based; Room frame indexing is not implemented.
