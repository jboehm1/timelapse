# UI / app reference audit — capture-first redesign (2026-10-30)

**Source of functional behavior:** screenshots supplied by user and the published [Velocity Lapse Timelapse Mode guide](https://guide.velocitylapse.com/capture_modes/timelapse_mode.html), [Photolapse Mode guide](https://guide.velocitylapse.com/capture_modes/photolapse_mode.html), [project-management guide](https://guide.velocitylapse.com/projects/managing_projects.html) and [capture-resolutions FAQ](https://velocitylapse.com/faq/). We do **not** copy artwork, code, resources or protected APK assets.

## Reference features to replicate independently

- Camera capture screen is **preview-first**: controls in bottom toolbar, optional settings drawers, no enormous always-expanded form occupying the preview.
- Timelapse and Photolapse are **different project types**. Both need a project home list with cover, frame count, resolution and last edited. Editor shows timeline, import/export, progress.
- Capture settings include interval, capture resolution, scheduling, grid, light and camera settings. Photos at `Max Available` are the highest JPEG outputs **exposed by the Android third-party Camera2 API**. Manufacturer apps may offer privileged 50MP/64MP/108MP modes unavailable to any third-party application.
- Metering must offer independent on-image AF (green region) and AE (yellow region), distinct lock states and manual override. Document actual hardware application; a painted marker alone is not evidence of focus.
- Full export menu should expose still sequence or MP4, video FPS, bitrate, codec, effect None/Motion Blur/Light Trails. File format DNG if supported, no pretended DNG if unsupported.
- Preserve original photos; never delete on failed export.

## Implemented first UI iteration in this PR

- Reorganized previous cramped one-screen controls into a **live preview-dominant screen** with dark header, project creation, in-image maximum photo indicator, optional thirds grid, quick AF/AE buttons, live status, large Start / Stop / Settings.
- Existing settings and PRO experimentation remain available in an 86%-height scrollable bottom dialog; no controls removed from the app.
- Added **PhotoResolutionCatalog** enumerating `CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(JPEG)` **plus** `getHighResolutionOutputSizes(JPEG)`, sorted by pixel count for every camera ID.
- Selected sensor-exposed size is sent to CameraX ImageCapture using `ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE` and a fallback strategy. Therefore if CameraX cannot bind the exact highest size together with Preview (common OEM problem), it can fall back gracefully. Show actual first JPEG dimensions; never label high-resolution output as verified without checking bytes/EXIF.
- Preview starts automatically from user-visible Activity using already-permitted foreground camera service, so AF/AE points can be set **before recording**.
- Added an automated emulator workflow that installs an actual debug APK, opens activity, captures screenshot, view hierarchy and logcat. An emulator is **not** a replacement for Samsung S21 physical-camera validation.

## Next UX milestones

1. **Emulator screenshot review:** detect clipping, button accessibility, layout. Repeat and document before claiming pixel-accurate UI.
2. **Project gallery** using RecyclerView/Compose lazy list, large thumbnail caching and Timelapse/Photolapse category switch.
3. **Camera control sheet** separate from general capture/export settings: ISO, shutter, exposure compensation, focus distance, white balance, chosen AF/AE metering region. Add persistent button feedback and markers.
4. **Photo picker** supports sensor-native max and orientation-safe quick presets 4:3, 16:9; display requested size vs actual saved size.
5. **Editor** media timeline with thumbnail scrubber, frame list, MP4 preview, effects preview, project import, storage.
6. **Accessibility:** dynamic font, landscape orientation, touch targets ≥48dp, high contrast.
7. **Night mode/standby** working with display physically OFF, no fake black-screen UI.
8. Run hardware camera across S21 3-4 available lenses and compare output sizes and night behavior.

### Evidence and limits
The screenshots/official docs are **functional design references**, not permission to decompile and republish someone else's resources. We may perform black-box installation and behavior comparison of legitimately obtained app binaries on an emulator, without copying its UI image assets or protected code. A particular commercial APK has **not** been independently installed in this PR, and full PRO feature parity remains outstanding.
