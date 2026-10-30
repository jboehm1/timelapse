# TIMELAPSE — MASTER DEVELOPMENT & NEW-CHAT HANDOFF
## Engineering, product design, reference-app research, release safety, and execution guide
**Prepared:** 30 October 2026 · **Target:** Samsung Galaxy S21 / Android 15 · **Authoritative GitHub repo:** https://github.com/jboehm1/timelapse · **Active PR:** https://github.com/jboehm1/timelapse/pull/2 · **Branch:** feat/android-capture-export-alpha

> **CRITICAL USER-REPORTED FAILURE: PRESSING START/RECORD DOES NOT SAVE ANY PHOTOGRAPHS ON THE USER'S DEVICE. STOP THE LINE.** The Android code builds and opens in an emulator, but its real product purpose is not demonstrated. Do not publish, promote, tag, announce, or distribute an APK as a usable recording app until real JPEG recording passes an end-to-end test on the Samsung S21. A successful build, lint, screenshot or functioning preview is not a substitute. An internal diagnostic APK may be built for investigation and must be clearly labeled unverified.

This is a self-contained handoff to a new, high-capability development agent. **Read it completely, inspect the current GitHub branch/PR, and act.** The deliverable is not another written plan: it is a working, genuinely professional Android timelapse application with recorded-image proof, beautiful UI, reliable storage, and measurable performance. Preserve all previous work; do not create a parallel repository or rewrite functional modules unnecessarily.

---

# 1. First-turn instruction to the next agent

You are the senior Android camera engineer, mobile product designer, QA owner and release engineer for **jboehm1/timelapse**. The user wants a best-in-class Android app inspired functionally by **Velocity Lapse: Time Lapse**, with an additional first-class multi-day construction use case. The existing app looks unpolished and, most importantly, **Start currently saves zero photos on the user's S21**. Fix that before any release. Treat this document, AGENTS.md, PROJECT_SPEC.md, PRO_PARITY.md, MASTER_TODO.csv and TEST_PLAN.md as binding.

**Your mission, in priority order:**
1. Fetch latest branch/PR state and identify the **actual latest commit and CI evidence**. Do not rely solely on this document's SHA; it may have evolved.
2. Reproduce and diagnose the **no-JPEG** bug: trace button → permission → foreground service → project → CameraX bind → capture scheduling → ImageCapture callback → pending file → FrameStore commit → persistent listing → UI. Prove images with validated bytes/dimensions, NOT just a counter or UI state.
3. Build a 100% deterministic emulator recording smoke test and arrange real S21 testing (the user can install an explicitly diagnostic build). Reject any claim of a successful recording until the evidence says so.
4. **Completely overhaul the UI/UX** using modern, premium-quality Android design. Do not perpetuate hand-built cramped LinearLayout/Spinner/EditText screens. Use Jetpack Compose Material 3, lifecycle-aware state and robust PreviewView interop where appropriate. Retain working camera/storage/export logic; refactor safely behind interfaces.
5. Implement truly independent, intuitive, visibly acknowledged AF and AE regions, lock states, optional manual exposure/focus, and capability-dependent resolutions including the highest third-party-accessible sensor JPEG output.
6. Implement honest recording/storage/video estimates, unlimited sessions, frame duration and Motion Blur/Light Trails controls with measurable previews, real JPEG/MP4 exports and conservative disk preflight.
7. Research existing MIT/Apache-2.0 modules before rebuilding. Benchmark CameraX vs Camera2 and Media3 Transformer vs streaming MediaCodec. Make evidence-based selections, not ideological rewrites.
8. Study the **legally obtained Velocity Lapse** app through screenshots, official guide, instrumentation, and, if appropriate/lawful, limited reverse engineering for functional understanding. **Build independently**, do not copy protected APK code, icons, layouts or artwork into the public/commercial project, and do not bypass licensing or purchases.
9. Push source, tests, updated documentation, screenshots and evidence to the GitHub feature branch incrementally; pass CI and inspect actual emulator screenshots. Avoid merging PR #2 to main until first-frame gate and release policy are satisfied.
10. Continue toward all 233 checklist items at **100% backed by reproducible evidence**, with a real device soak campaign for beta/stable. Don't misrepresent an emulator as Samsung hardware.

**Tools/work mode:** Prefer the highest available reasoning effort (GPT-6 High or Extra High where offered) or ChatGPT Work for autonomous multi-step repo/build/browser work, plus the connected GitHub tools. These modes do not magically grant a connected phone, unlimited compute, or hardware access. Use existing GitHub Actions runners (the repository is now public), build artifacts, test logs, emulator, ADB and official dependencies. For any command that cannot actually be executed, explain the missing prerequisite and leave precise runnable automation. **Do not pretend background work continues after the chat ends.**

**Communication:** The user prefers actual commits and a testable artifact over verbose promises. In the final answer, report commits, CI run IDs, demonstrated behaviors, remaining blockers and a link to a valid build **only when the release gate allows it**.

---

# 2. Repository and verified technical snapshot

| Attribute | Verified as of this handoff |
|---|---|
| Repository | https://github.com/jboehm1/timelapse |
| Visibility | PUBLIC (original code remains copyright-reserved; see COPYRIGHT.md) |
| Default branch | main; do not assume it contains current alpha |
| Active development | feat/android-capture-export-alpha, PR #2, OPEN / not merged |
| Prior verified head SHA before preparing this handoff | 32aeacc5d90ee602d31f4c79baab5e26f843fa0e (refresh before work; handoff/docs commits will advance it) |
| Reference test hardware | Samsung Galaxy S21 / Android 15 |
| Main implementation | Native Kotlin; CameraX 1.6.2 + Camera2Interop, Camera foreground LifecycleService, pure Kotlin frame policy, ImageCapture, MediaCodec video exporter |
| Android build | compileSdk 36, targetSdk 35, minSdk 29, JDK 17, Gradle 8.13 |
| Application ID | eu.example.constructionlapse |
| Android CI evidence | Successful Android build/JUnit/lint: run #40978329365 |
| Emulator evidence | Successful app launch/screenshot: run #40978329505; **not a capture test** |
| Pure Kotlin CI | Successful reliability tests: run #40978329429 |
| Binary artifacts | Experimental debug APK in Actions. Earlier in chat provided v0.2.0 UI alpha, but **not validated for image recording**. Do not rebrand as production. |
| User field report | **Start/Record does not write pictures. Highest-priority blocker.** |
| Progress tracker | 233 individual requirements in docs/MASTER_TODO.csv and docs/MASTER_TODO.md, last indicated weighted 17%, **may be stale relative to UI commits** |
| Device capture proof | NONE |
| Overnight resume / 8h / 72h / 7d proof | NONE |
| Code protection | COPYRIGHT.md: no general source-code reuse grant. GPL dependencies cannot silently be embedded into proprietary deliverable. |

**Always cross-check the latest actual CI run from GitHub.** The snapshot above is not permission to assume a newly created PR commit is green.

### 2.1 Source files and ownership

- app/src/main/java/eu/example/constructionlapse/MainActivity.kt — UI, camera preview binding, project selection, Start/Stop, camera controls, folders, export, current improvised redesignUi() with programmatic widgets. Approximately 50k characters; **major UX debt**.
- CaptureService.kt — start/preview actions, FGS CAMERA type, CameraX provider/binding, capture scheduler/limits, JPEG save, Camera2Interop AF/AE/manual values, wake handling and status. **Primary suspect area for zero saved files**.
- MeteringOverlay.kt — green focus and yellow exposure ROI drawing, taps and requested/applied/failed indication; needs real confirmed sensor-state feedback, good transforms, accessible UX.
- PhotoResolutionCatalog.kt — queries SCALER_STREAM_CONFIGURATION_MAP normal and high-resolution JPEG sizes; sorts by pixels; high-resolution entry does **not prove exact ImageCapture output without EXIF/file measurement**.
- VideoExporter.kt — MediaCodec/MediaMuxer export with None, Motion Blur, Light Trails, video codecs and timestamp burn-in; performance/output accuracy not validated.
- ScheduleReceiver.kt — best-effort morning alarm into existing service; Android 14/15 restrictions mean no promise of arbitrary cold restart.
- core/CapturePolicy.kt — scheduling/safety policy, tested as pure JVM.
- core/FrameStore.kt — atomic staged-file publication, originals preserved, recovery/quarantine and SHA256 journal, but **not yet proven with real CameraX saved JPEGs**.
- core/TimelapseLogic.kt — deadline skipping and effect-window logic.
- app/src/test/java/.../CoreReliabilityTest.kt — deterministic JVM tests; tests logic, not real camera.
- tools/emulator-smoke.sh and .github/workflows/emulator-smoke.yml — launches APK, screenshots home/settings, dumps UI/logs; must be expanded to assert **actual saved JPEGs**.
- .github/workflows/android.yml — compile, test, lint, diagnostic APK artifact.
- tools/progress.py — keep CSV and Markdown dashboard synchronized.
- docs/PROJECT_SPEC.md, PRO_PARITY.md, ARCHITECTURE.md, CAMERA_CONTROL_UX.md, TEST_PLAN.md, OPEN_SOURCE_REUSE.md, UI_REFERENCE_AUDIT.md, BUILD_REPORT_2026-10-30.md — contract and history.
- AGENTS.md — binding agent workflow, including strict first-photo release gate.
- COPYRIGHT.md — reserved rights for original project code; third-party licenses remain independent.

### 2.2 Baseline screenshots

The last inspected emulator screenshot **app-home.png** has a solid dark header, text-heavy photo-max banner, blocky pixel-art emulator camera feed, very large Focus/Exposure overlay, cramped three-button bottom row and no real project/capture completion feedback. It is **a valid emulator screenshot, but visually not premium**.

The inspected **app-settings.png** has native-looking text fields/dropdowns, a plain white/gray surface, extremely sparse unused vertical space, three giant tabs, no visual hierarchy of camera, schedule, expected frames or storage, and no refined spacing/typography. The user explicitly dislikes it.

Screenshot artifacts from run #40978329505 include app-home.png, app-settings.png and app-settings-scroll.png. A GitHub workflow has been updated to commit these three PNGs under docs/screenshots/emulator/ in the feature branch. Verify they are committed after the workflow completes; if not, rerun/fix and do not falsely claim images are in Git.

**Do not treat low-resolution colorful emulator preview blocks as photographic output.**

---

# 3. The P0 bug: Start saves zero photos — mandatory repair contract

**A visually polished recorder that saves zero frames is broken. Work on this FIRST.**

### 3.1 Actual suspected code path (requires reproducible proof)

MainActivity.startCapture() requests CAMERA if missing, creates a project if none selected, creates ACTION_START for CaptureService, attaches project/interval/lens/resolution/schedule extras, and calls ContextCompat.startForegroundService().

CaptureService handles ACTION_START: upgrade preview-only session, start camera FGS, find project directory, recover FrameStore on cameraExecutor, set deadline, bind CameraX ImageCapture/Preview, evaluate DayWindow/SafetyPolicy, call takePhoto(), write a .pending file, decode bounds, commit through FrameStore as .jpg, increment count.

**Potential failure points to instrument and discriminate, not assume:**
- User presses Start before permission/selected project state has fully settled; service start fails, or Start only upgrades preview state and never schedules first tick.
- ACTION_START may be suppressed by running/previews state, or onStartCommand/FGS emits a recoverable error without clear user-visible feedback.
- Camera ID from Camera2 hardware list not bindable as independent CameraX CameraSelector (physical/logical lens IDs differ); CameraFilter returns empty, cameraBound false, retry loop.
- Day-only policy outside 07:00–18:00, thermal/storage/battery gate denies CAPTURE, or wrong state displayed.
- Capture scheduler calls takePhoto before imageCapture/cameraBound initialized.
- CameraX ImageCapture callback fails/hangs, Timeout/Retry never results in durable JPEG.
- Staged file is written but fails FrameStore validation (JPEG SOI/EOI, size, wrong extension), journaling/fsync or rename; exception swallowed by runCatching; UI only shows count/status that isn't reconciled to folder.
- File was saved to app-specific external root but gallery does not show it: inspect actual project originals directory rather than treating absent Android gallery thumbnail as proven failure.
- Screen lifecycle / PreviewView detach / repeated unbindAll cancels capture use-case.
- OEM camera resolution combination unavailable, CameraX fallback changes resolution/binding; separate high-res selection from preview.

**Specific code lines to revisit (verify against latest SHA):**
- MainActivity.kt: startCapture(), requestCameraPreview(), selectedProject(), selectProjects(), refreshStatus().
- CaptureService.kt: ACTION_START and ACTION_PREVIEW, bindCamera(), connectUseCases(), captureWhenAllowed(), takePhoto(), onImageSaved(), cameraFailure(), nextTick, FrameStore.commit().
- FrameStore.kt: filename regex, SOI/EOI validity, fsync, journal, recover.
- PhotoResolutionCatalog.kt: getOutputSizes / getHighResolutionOutputSizes; ImageCapture ResolutionSelector.

### 3.2 Required trace / state machine

Replace ambiguous status text with explicit transitions:

IDLE → PERMISSION_CHECK → PROJECT_READY → STARTING_FOREGROUND_SERVICE → BINDING_CAMERA → READY → **FIRST_CAPTURE_REQUESTED** → **FIRST_JPEG_WRITTEN_AND_VALIDATED** → RECORDING → PAUSED_NIGHT / PAUSED_THERMAL / STORAGE_FULL / RECOVERING / ERROR / STOPPED.

- Mark Start as **Starting...**, not **Recording**, while zero valid JPEGs exist.
- Show red **Recording ●** ONLY when an actual durable image has been successfully committed and current session is armed; show frame count, last timestamp, interval, drop count, errors and storage.
- If no first JPEG within bounded timeout (e.g., 15–30 s plus actual camera latency), show a blocking error with meaningful event codes and a log export action. Do not spin indefinitely or lie.
- Each path must log monotonic elapsed time, wall time, project ID, camera ID, resolution, file path, file bytes, JPEG dimensions, action and failure detail. Sanitize metadata before any public log upload.
- A one-shot **Test Capture** button can verify quality and file-saving before arming a multi-day schedule.
- Camera and activity state must survive orientation/resume; avoid recreating the camera session unnecessarily.

### 3.3 Automated emulator gate (mandatory CI)

Extend tools/emulator-smoke.sh and/or add connected Android instrumentation tests:
1. Install clean debug APK; grant necessary runtime permissions.
2. Create a project through the real app UI; configure a short 2–5 s interval, daylight-only disabled.
3. Press actual Start button through UIAutomator/UI test.
4. Poll a debug-only, non-network-exposed test API or app-private storage for **at least ten newly created, independently decodable JPEG files**. Do not trust only UI counters or a dummy fixture.
5. Check each starts with valid JPEG bytes, decodes to width > 0/height > 0, has unique monotonic IDs, nonzero size and plausible timestamps; verify no unexpectedly corrupted .pending files are reported as complete.
6. Stop; kill/reopen Activity; assert files persist and the project count agrees with on-disk inventory; compare SHA256 before/after MP4 export.
7. Save screenshots of IDLE, STARTING, first JPEG, RECORDING ≥10 and final project gallery; save logs and JSON metrics; fail workflow if no real JPEG captured.
8. Run conditions both with PreviewView bound and detached (simulate screen OFF / background where supported). Deliberately test bad camera ID, unsupported high resolution and blocked permissions.

**Caution:** An emulator-successful JPEG test still does not prove S21 photo quality/long-session survival. But a test that only launches MainActivity proves almost nothing about recording.

### 3.4 Required Samsung Galaxy S21 gate (user-assisted)

User starts diagnostic APK, selects one real accessible lens, creates project, chooses 2–5 seconds, **without daily time restriction**, saves ≥10 JPEGs; compares count, dimensions, hashes and project persistence after restart. Repeat second resolution, different lens and screen OFF. Collect ADB logcat, dumpsys camera/thermal/power, and anonymized app diagnostics. Do not require the user to send private family/home photos; use a neutral wall/scene or synthetic test object.

**CAPTURE-P0-001 is PASS only when recorded JPEG files were inspected, with app revision, phone/OS, file list, sample EXIF, actual dimensions, and logs.**

### 3.5 Hard release policy

- **No public Release, Play Store upload, beta/stable announcement or APK presented as working until CAPTURE-P0-001 passes.** CI may keep internal/diagnostic APK artifacts, labeled UNVERIFIED, solely for fault investigation.
- No merge of feature PR into main because of compiler green or pretty screenshots.
- Recording reliability > remaining visual features. Track failure explicitly on dashboard.
- After CAPTURE-P0-001, alpha additionally requires sustained ≥8 h recording and valid MP4 export, beta ≥72 h day/night including thermal/storage faults, stable ≥two 7-day runs with documented limits.
- Do not promise perfect unattended wake after force-stop/reboot under Android 15.

---

# 4. Target product: professional UI/UX redesign

The existing programmatic createUi() + redesignUi() assembles large native controls into a half-screen form and then reparents them. **Replace the visual layer, not the validated capture engine**, using Kotlin Jetpack Compose + Material 3 with consistent design tokens, navigation, edge-to-edge and accessibility.

### 4.1 Required information architecture

**A. Projects home**
- Modern, beautiful gallery of projects with large thumbnails, Timelapse vs Photolapse tabs, project name, active/paused status, frame count, last frame timestamp, actual max photo resolution, folder size and last export indicators.
- Floating/create button with presets (Construction Daylight, Continuous, Photolapse, Astro, Custom).
- Search/sort; rename, duplicate preset, import and delete with confirmation; maintain originals and provenance.
- Distinguish **no project** from **no photos yet**; never show permanent “NO PROJECT” while a project exists.

**B. Main capture screen**
- Camera image fills primary area; strong composition with minimal overlays, correct crop/rotation, show 3×3 or 4×4 grid if chosen.
- Slim upper status row: project / selected lens / actual megapixels / storage remaining / battery + thermal badge.
- Thumbnail of last REAL saved photo (not just preview), tap opens editor; live verified count and last capture time.
- Large accessible Capture CTA with states: Start → Starting → Capturing (actual JPEG) → Stop; pause/stop protection.
- Dedicated but unobtrusive AF and AE zone controls, compact buttons near preview edge, not giant opaque rectangles over scene.
- Overlay feedback for focus/AE requests as described in section 7.
- Prominent time-to-next-frame and total session estimates; do not clutter preview with raw settings.

**C. Bottom settings sheet**
- Clean Material3 ModalBottomSheet, rounded surfaces, consistent 8dp spacing system, legible typography, restrained accent colors and elevated cards. Three sections at minimum: Capture & Schedule; Camera & Image; Export & Video. Optionally dedicated Advanced and Storage.
- Replace raw EditTexts/Spinners with segmented controls, sliders, number pickers, chip selections, validated text fields, time/date pickers and disabled-state explanations.
- Always show unit next to numbers; avoid wide multi-column rows that clip at 360dp or large font scale.
- Present "auto" vs "manual" modes as distinct explicit state; show camera capability range and applied value.
- Screen rotation, Android 15 system insets, foldables/tablets and accessibility size 200% must work.

**D. Project editor**
- Frame timeline/thumbnails, scrubbing, original image details, preview of Motion Blur/Light Trails before full export, reverse, selection range and multi-select (safe delete with confirmation).
- Distinct "Export MP4" and "Export original images folder / ZIP" flows, export progress, cancellation and history.
- File size calculation, available disk and actual codec/resolution constraints.

**E. System & diagnostics**
- Clear permissions and background capture explanations; last error, retry log, thermal/charging/screen-off status, app version/commit, export diagnostic bundle.
- Non-technical user copy in FR/EN/DE eventually; code strings in resources rather than hard-coded multiple-language app code.

### 4.2 Visual bar for acceptance

- Typography: coherent font family/scale; compact 14–16sp labels, 20–26sp screen headings, 11–12sp secondary metadata; do not use random font sizes.
- Touch targets **≥48dp**, semantic labels for TalkBack, meaningful contrast, no all-caps everywhere, functional controls in focus order.
- Dark capture canvas with restrained accent; warm/neutral settings surfaces; project cards with thumbnail. A premium design should look visually coherent, **not like stock Android widgets laid in columns**.
- Inspect *actual screenshots* on emulator in portrait and landscape, small phone (360dp), S21-ish device, larger display, long translations and font scale 1.5–2.0.
- Prevent content hidden behind system bars/navigation, keyboard or sheets; prevent ghost views after rotating.
- Screenshot-diff regression and UI hierarchy assertions; ideally Android Compose UI tests for key states. Print/run/evaluate each screen before marking UX done.
- User explicitly reported the prior UI as **very bad, old-school, not polished**. Treat this as a design failure to correct end-to-end.

### 4.3 Suggested architecture, no unnecessary rewrite

- Single MainActivity with Navigation Compose, Material3 theme and discrete screens.
- PreviewView AndroidView + Compose Canvas overlay; Activity merely binds preview to the long-lived foreground service.
- ViewModel + StateFlow / immutable UI state; repository backed by Room (after migration tests). Avoid get-child-at reparenting and huge MainActivity.
- Camera service owns capture scheduling; UI observes asynchronous confirmed state via service binding plus persistent state store. ViewModel should not own the camera.
- Compose Preview screenshots can test empty/error/loading/recording states using injected fake services, **but cannot replace real JPEG tests**.
- Create a design system/tokens document (spacing, color, typography, iconography, surfaces) and reviewed screenshot references.

---

# 5. Velocity Lapse — benchmark/reference app

### 5.1 Exact reference app and provenance

- **Name:** Velocity Lapse: Time Lapse.
- **Play package ID:** com.velocitylapse.velocitylapse.
- **Developer:** Noah Rahm.
- **Official Play listing:** https://play.google.com/store/apps/details?id=com.velocitylapse.velocitylapse
- **Official guide:** https://guide.velocitylapse.com/
- **Timelapse capture:** https://guide.velocitylapse.com/capture_modes/timelapse_mode.html
- **Photolapse capture:** https://guide.velocitylapse.com/capture_modes/photolapse_mode.html
- **Projects:** https://guide.velocitylapse.com/projects/managing_projects.html
- **Editor:** https://guide.velocitylapse.com/editor.html
- **Export:** https://guide.velocitylapse.com/export.html
- **Settings:** https://guide.velocitylapse.com/settings.html
- **Developer FAQ:** https://velocitylapse.com/faq/
- The Play listing consulted for this handoff reports an update on **2026-07-05**. App and documentation can change; verify again when testing.
- Official public source code for the full commercial app was **not located**; public guide source is not the app source and does not confer an app-code license.

**Benchmark feature model:** Timelapse and Photolapse; multiple projects; automatic interval/manual individual capture; optional frame limit/unlimited, delay and schedules; AF/AE focus/lock/ISO/shutter/WB/zoom/lens switching; max third-party JPEG and RAW DNG if supported; display grid and previous-photo alignment; timeline and preview; video export up to supported 8K/4K with FPS/bitrate/codec; Motion Blur and Light Trails; JPEG/DNG image sequence; import, SD folder selection, timestamp, Bluetooth/volume shortcuts. Distinguish free and PRO entitlements.

### 5.2 Should we reverse engineer the APK?

**Recommendation:** Start with **behavioral reverse specification** because the desired value is UX and compatible features, not possession of its implementation. The new agent should evaluate legal context and perform **limited static analysis** only if useful for interoperability and lawfully obtained assets, not wholesale code extraction and reuse. Acquisition must be legitimate through Google Play or authorized distributions; PRO mode should be legitimately purchased if its behavior is evaluated. Do not bypass PRO licensing, DRM/signatures/paid controls, upload an extracted commercial APK to our public repo, remove rights notices, or reuse UI resources. This project may be commercial; preserve an independent design and implementation.

Three research tiers:
1. **Official behavior/reference (preferred):** read published guide; inspect every screen on emulator/owned Android device; record screen video, screenshots, UI hierarchy with UIAutomator, button behavior, settings ranges, disabled states, timeline, export, prompts, errors, project/permission flows. Build a behavioral matrix with reproduction steps and observed outputs.
2. **Black-box experiments:** capture identical scenes with both apps under matched exposure/focus/interval/resolution, compare frame counts, actual JPEG metadata, alignment, color/exposure transitions, video durations/effects and startup/overnight behavior. Never assume output algorithm from screenshots alone. Record device model, app version, PRO entitlement and settings.
3. **Limited binary analysis, only where lawful and justified:** use package-manager metadata, AndroidManifest, permissions and dependency catalog; if allowed, decompile lawfully acquired APK/splits with JADX or Apktool to understand architecture/API behavior and locate compatibility issues, **without transplanting proprietary implementation**, copying icons/layouts or bypassing payment checks. Keep any extracted originals outside public Git and treat as confidential third-party material. Consider a clean-room split between observer/spec author and independent implementer to reduce license/IP risk. Verify local laws and contractual constraints before disassembly.

Possible tooling on a device **with user authorization**:
- Google Play install for package com.velocitylapse.velocitylapse; use Android ADB, UIAutomator, accessibility hierarchy and screenrecord to document UI, not private data.
- Collect base/split package paths with: adb shell pm path com.velocitylapse.velocitylapse. Merely accessing files is **not** permission to redistribute code.
- jadx-gui / JADX CLI (https://github.com/skylot/jadx) for permitted analysis of DEX, Apktool (https://apktool.org/) for resource inspection where authorized. Do not package extracted resources into this project.
- Use consistent scripts and evidence metadata for each screen: reference_app_version, device_build, screenshot, actions, actual output, observed limitation, independent_app_equivalent, issue ID.

**Design guidance:** Functionally match/improve Velocity Lapse but **do not create a pixel-identical trade-dress clone**. Learn from its clear projects, editors and capture flow while pursuing an original professional design.

### 5.3 Required side-by-side comparison matrix

Create a living document and screenshot set for **every** screen/state:
- Home: no projects, one project, 100 projects, Timelapse/Photolapse switch.
- Timelapse capture: before Start; countdown; after one confirmed image; recording; stop; night standby; error.
- Camera: lens switch, 3A lock, AF/AE region touches, manual ISO/shutter/focus, WB, maximum JPEG.
- Settings: interval, frame limit infinite/fixed, start delay, daily schedule and weekdays, destination and storage capacity.
- Editor: media timeline, previous-frame overlay, playback, import/export, reverse, multi-selection.
- Export: image sequence, video resolution, FPS, codec, bitrate, Motion Blur amount, Light Trails, timestamp and output-size estimate.
- Accessibility: text size/portrait/landscape and disabled-camera restrictions.
- Hardware support: stock S21 camera max vs third-party Camera2 API max; don't claim inaccessible vendor modes.

For every comparison, save **actual observed behavior, differences, proposed independent UX, and test evidence**. A powerful agent should spend time inspecting real screenshots and iterating visual layouts, not merely editing strings.

---

# 6. CameraX versus Camera2: engineering decision

**Not a binary quality competition.** CameraX builds on Camera2 and adds lifecycle, multi-device compatibility, Preview/ImageCapture resolution negotiation and focus-metering abstraction. Camera2 exposes lower-level capture requests, results and session controls. Camera2 is not inherently sharper or higher-quality; sensor, ISP, image configuration, timing and OEM behavior dominate.

**Current recommendation: retain CameraX as reliable outer capture/lifecycle layer, with Camera2Interop for advanced 3A/manual controls; prototype a separate Camera2 backend only if RAW/DNG or precise region/range/sensor controls cannot be achieved.** Avoid two simultaneous competing camera sessions.

| Requirement | CameraX / Interop | Direct Camera2 | Selection test |
|---|---|---|---|
| JPEG and Preview | Mature high-level lifecycle | Requires detailed sessions/Surface/3A state | 10 verified actual JPEGs, latency and crop |
| AF tap and metering point | CameraControl.startFocusAndMetering; PreviewView coordinate factory | CONTROL_AF_REGIONS + AF_TRIGGER | Confirm hardware active/locked, photos visually sharp |
| Separate AE region | MeteringPoint FLAG_AE if supported | CONTROL_AE_REGIONS | Confirm requested vs reported region and measured brightness |
| AF/AE lock state | FocusMeteringAction locking modes in recent CameraX, experimental interop as needed | CONTROL_AF_STATE, CONTROL_AE_LOCK | Lock persists across timed captures/rebind |
| Manual ISO/shutter | Camera2Interop requests if supported | SENSOR_SENSITIVITY, SENSOR_EXPOSURE_TIME | Compare actual CaptureResult vs requested |
| Manual focus | Camera2Interop lens distance | CONTROL_AF_MODE_OFF, LENS_FOCUS_DISTANCE | Diopters range and photo evidence |
| WB | Camera2Interop | CONTROL_AWB_MODE, COLOR_CORRECTION_GAINS etc | Result and color sample |
| RAW DNG | CameraX support may be limited by version/hardware | ImageReader RAW_SENSOR + DngCreator | Valid DNG and paired JPEG |
| High-res | ResolutionSelector high resolution with negotiated fallback | Output size and session composition control | Actual saved pixel dimensions and capture stall time |
| Compatibility | Higher-level workarounds | Greater responsibility for quirks | S21 day/night tests and portability matrix |

**Important newer CameraX detail:** official FocusMeteringAction documentation describes configurable AF/AE/AWB locking mode; study exact API for **CameraX 1.6.2**, do not blindly reuse older 1.4.x assumptions or deprecated workaround. The async result's isFocusSuccessful can be **true if AF is unsupported**; don't represent it as proof of optical sharpness. CaptureResult AF state and independent peaking metrics matter.

Sources:
- https://developer.android.com/media/camera/choose-camera-library
- https://developer.android.com/reference/androidx/camera/core/FocusMeteringAction
- https://developer.android.com/reference/androidx/camera/core/FocusMeteringResult
- https://source.android.com/docs/core/camera/camera3_3Amodes
- https://developer.android.com/reference/androidx/camera/core/resolutionselector/ResolutionSelector

**Evaluation plan:** Run an A/B benchmark using the same S21 physical lens, AF target, exposure range, JPEG image quality/size, dark daylight, warm/cold conditions, 100 captures and memory/thermal power. Choose by measured missed frames, supported controls, complexity and image results. Do **not** throw away working CameraX just because direct Camera2 offers additional features.

---

# 7. Premium visual AF / AE selection and genuine feedback

### 7.1 Interaction contract

1. User chooses **Focus**, **Exposure**, or **Both**, then taps a preview location. AF and AE have **independent centers and adjustable radii**. Selecting a new AE point must not move or unlock AF; likewise for AF.
2. Show semi-transparent ROI on preview: **green focus rectangle/ring** and **amber exposure rectangle/ring**, accurately mapped from visible crop/rotation/mirroring to sensor coordinates. Subject marker should remain visible until user hides it.
3. Distinguish **REQUESTED** (UI event), **APPLIED** (capture request accepted/region mapped), **FOCUSING** (AF_ACTIVE_SCAN), **FOCUSED/LOCKED** (AF_FOCUSED_LOCKED), **NOT FOCUSED** (AF_NOT_FOCUSED_LOCKED), **UNSUPPORTED** (camera characteristics), **MANUAL** (user-set lens distance), and **ERROR** (callback failed). Never green-check a request merely because its future completed.
4. AE: show meter target and AE state (searching/converged/locked/flash required where reported), actual ISO, shutter milliseconds and EV from capture results when available; use sensor data or measured luminance to show that selected zone affects exposure.
5. AF/AE/AWB lock switches must be separate. For outdoor construction typical preset: lock/hold distant building AF, **AE adapts** to changing clouds/daylight. User can sample exposure from sky or wall independently.
6. Allow **manual focus distance** with a practical infinity marker and diopters ⇄ approximate meters explanation; support optional focus-peaking overlay with proper performance budget.
7. **Optional tracking:** if subject tracking exists, use CameraX ImageAnalysis with object tracker ROI, but distinguish a tracked subject box from actual sensor AF region. Tracking ROI cannot by itself prove the sensor moved focus. Feature-gate for thermal/CPU.
8. After lens change, rotation, sleep/rebind, face/scene transitions: restore intent, check hardware support, reapply regions and update markers only after observed state.
9. Accessible controls: high-contrast states with icon/text in addition to green/orange color, TalkBack descriptions and a Tap to verify focus action.

### 7.2 Actual hardware telemetry

For Camera2Interop and/or direct Camera2, monitor:
- CONTROL_AF_STATE, CONTROL_AE_STATE, CONTROL_AWB_STATE.
- CONTROL_AF_REGIONS and CONTROL_AE_REGIONS, and their max counts from camera characteristics.
- LENS_FOCUS_DISTANCE, SENSOR_EXPOSURE_TIME, SENSOR_SENSITIVITY, CONTROL_AE_EXPOSURE_COMPENSATION.
- LENS_STATE where available; frame timestamps to associate state with a specific saved JPEG.
- AF region may be ignored if max regions = 0; manual fixed-focus lenses may never autofocus. Indicate unsupported honestly.
- A useful extra is **focus peaking / contrast edge strength** computed from preview frames; label it "estimated local image sharpness" rather than proof of actual 3D focus plane.

**Validation:** Focus on a near object, expose on a bright window, check near object sharpness and controlled brightness; repeat invert; lock focus while deliberately changing exposure target; ensure markers and reported states match. Capture A/B images and sensor telemetry for each case.

---

# 8. Maximum photo format, JPEG/RAW and truthful capability display

For every exposed logical/physical camera ID, query:
- CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(JPEG), getHighResolutionOutputSizes(JPEG), and available 10-bit/RAW formats if relevant.
- Minimum frame duration/stall duration; preview + ImageCapture combination, sensor orientation, logical/physical lens characteristics.
- CameraX ResolutionSelector with high-resolution allowed mode. Beware that the requested size is negotiated/fallback and can be reduced by the bound Preview/ImageCapture use-case combination and OEM restrictions.
- Store requestedWidth×height **and actual saved JPEG width×height** per frame and compare; do not label requested "MAX" as verified until a real full-resolution file exists.
- Provide choice **Maximum available**, 4:3, 16:9, 12MP/other true sizes, 4K-equivalent video output when source meets dimensions, and real measured storage per frame.
- JPEG quality 80/90/95/100 if controllable, sample actual byte sizes. Pixel dimensions alone do not determine JPEG storage. 50/64/108MP modes in Samsung's stock app may not be accessible to standard third-party Camera2 APIs.
- If RAW_SENSOR capability supported, use separate capability-gated Camera2 RAW/DNG pathway; do not fake DNG with renamed JPEGs. Consider simultaneous JPEG+DNG only if stream combinations and timing permit.
- For Motion Blur preview/MP4, do not unintentionally downscale original stills or overwrite them. Video export resolution independent from capture resolution.

Source: https://developer.android.com/reference/androidx/camera/core/resolutionselector/ResolutionSelector and Velocity Lapse FAQ https://velocitylapse.com/faq/

---

# 9. Capacity calculator: required feature, not optional polish

The capture setup must show instant, constantly updated, **clearly labelled estimates** before Start and during recording. Use **device calibration** from first 10–30 actual saved JPEGs and adjust estimates with median, P10/P90/EMA (avoid outlier optimism). Before first photo, use a provisional assumption labelled **ESTIMATE, NOT MEASURED** based on resolution/quality/codec and show uncertainty. Base storage on remaining disk minus explicit reserve, not total device storage.

### 9.1 Variables and equations

- I = interval seconds (>0).
- S = active scheduled seconds, computed by intersecting user start/end dates, selected weekdays, daily time windows and time zone including DST.
- N_expected = count of capture deadlines inside active periods. For a single continuous window starting at time zero, N = floor((S - epsilon) / I) + 1; define endpoint inclusion in tests. Missing/late frames are counted separately; no catch-up bursts.
- FPS_export = requested playback frames per second.
- T_video_seconds = N_actual / FPS_export; give estimated N_expected / FPS for forecast.
- JPG_bytes = measured average/median bytes of selected lens/resolution/quality over representative saved frames.
- Originals_bytes ≈ N_expected × JPG_bytes; PNG/DNG use independently measured sizes, NEVER same JPEG estimate.
- Video_bytes ≈ video_bitrate_Mbps × 1,000,000 × T_video_seconds / 8, plus MP4 container overhead; codec quality is NOT a fixed compression percentage.
- Export risk: free bytes must cover output + temporary encoder files + safety reserve, while original JPEG assets remain untouched.
- Available recording runway ≈ (free_bytes - reserved_bytes - other_project_growth) / (frames_per_active_day × estimated_frame_bytes); report days and active hours, not just GB.

### 9.2 Mandatory unlimited-duration behavior

If user chooses **Unlimited / No end date**, there is NO finite total photograph count or total GB. Show:
- **Photos per hour**, **photos per active day**, and **1/7/30-day projections** based on configured weekdays/time windows.
- Estimated free-storage runway in days/hours with low/high file-size scenarios and safety threshold.
- Current total frames and measured average size; live "latest estimate" updated after every suitable sample batch.
- Expose a projected stop date from **disk-full threshold**, not from a fictitious infinite duration.
- Warn when changing sensor resolution or choosing RAW substantially reduces runtime.

### 9.3 Illustrative baseline (not a camera measurement)

11 active hours per day (07:00–18:00), one JPEG every 20 s:
- 39,600 active seconds / 20 = approximately **1,980 photos/day**.
- If average JPEG = 4 MB **assumed**, ~**7.92 GB originals per day**, ~55.4 GB in seven active days.
- At 30 export FPS, 1,980 / 30 = **66 s playback per active day**.
- At video target 20 Mbps, 66 s × 20 Mb/s / 8 ≈ **165 MB/day of MP4**, plus overhead.
- Original photos and exported MP4 both occupy disk if retained, so **total consumed ≥ originals + exported video**, plus backups, multiple exports and reserved temp space.
- Actual JPEG sizes vary with scene noise/ISO/detail; present ranges and calibrate from real first frames.

### 9.4 Estimate UI detail

At capture: a card "2,000 photos/day · ~8 GB/day · 66 s video/day · ~6.3 days until safe storage limit" with assumptions and low/high bar; tooltip link explains estimate. For fixed frame count/time, show **estimated completion, video runtime, original size, MP4 size and both combined**. For unlimited schedule, display rates and capacity runway. For exported image folder, account for duplicate copies on export; ZIP may be poor compression for JPEG and needs overhead. Raw DNG must be estimated separately.

### 9.5 Tests

Deterministic tests for frame interval endpoints, daily time crossing midnight, holidays/weekdays, DST March/October Berlin, no end date, short/long sessions, 0 free space, reserve safety, variable JPEG sizes, 4K export and 8K unavailable, N/FPS rounding, bits versus bytes, and no false certainty.

---

# 10. Export, effects and project editing

**Supported output families:**
- **Originals:** image files as separately accessible JPG/JPEG/PNG/DNG where supported, export to user-selected folder and optional ZIP; no forced video.
- **Video:** MP4 (H.264 baseline/default, HEVC and AV1 only if encoder support and playback verification), presets 720p/1080p/4K/8K conditionally, FPS 24/25/29.97/30/60, bitrate Mbps, aspect/crop/orientation and timestamp overlay.
- **Effects:** None, Motion Blur and Light Trails with truly selectable intensity/window; not just a checkbox. Default windows e.g. 1/3/5/7/9/15/21 frames, allow odd values within bounded memory/latency constraints; show actual sample count and previews.

**Motion Blur:** temporal averaging/weighted convolution of adjacent still frames for flow-like smoothing. Test center/trailing modes, moving edges, boundary behavior and input-size memory. **Light Trails:** temporal per-channel max/lighten (or luminance-select maximum) with scientifically clear semantics and visible strength control; test static scene, bright moving light, and day-to-night discontinuities. Do not claim exact Velocity Lapse algorithm without evidence.

**Streaming correctness:** preserve high-res original files, bounded image cache, avoid keeping thousands of full bitmaps, use MediaCodec/MediaMuxer or Media3 Transformer after real benchmarks. If a 4K/8K encoder unsupported, disable or downscale with explicit warning, do not silently label output 8K. Validate MP4 via Android MediaExtractor and ffprobe, pixel sizes, frame count, FPS, duration, sample timestamps, pixel comparisons and playback in Android gallery/Windows. Test with 10, 120, 10k+ input files.

**Editor:** recent frame thumbnail and timeline, scrubbing, reverse export, previous-photo overlay transparency (Photolapse), skip/delete carefully, imported project metadata, original export and video exports, progress/cancel and no overwritten original. Abort failing MP4 safely without deleting originals. A corrupt/partial export must be clearly marked as such.

**Critical:** export MP4 file size depends principally on bitrate, FPS and content/codec, not merely original JPEG cumulative size. Show separate estimates.

---

# 11. Scheduling, power, thermal and recovery

- Camera FGS must start while user interaction grants foreground camera permission; Android 14/15 while-in-use restrictions constrain background cold launch. An alarm alone does not guarantee waking the camera and starting a new service.
- Prefer a retained user-started service with physical screen OFF; test whether releasing/rebinding the camera in night standby works. Measure power and heat against leaving camera idle; choose measured solution. Clearly explain OEM restrictions, force-stop/reboot behavior.
- Daily schedule per weekday, date range, optional never-ending schedule, timezone and DST; no captures outside user window. Show next capture and next morning wake in UI.
- Battery, thermal (severe/hot), charging state, storage threshold and camera error have bounded retry state; no unbounded loop; accessible alerts and diagnostic logs. Never discard an original to free space automatically unless user explicitly opted into a separately specified retention mode (default OFF and not construction-friendly).
- Atomic file write→validate→fsync→publish→journal; recover staged files, verify hashes, safe ID increment; reconcile project index after crash. UI counts must derive from actual committed asset inventory, not attempted callbacks.
- Investigate sudden camera permission loss, lens occupied, user Stop, app killed by OS, charger disconnected, SD removed, Doze scheduling and high-res long save latency.
- Capture quality test: if badly blurred/black/overexposed, annotate and warn but do NOT delete original by default.
- Real 8h, 72h and 7d Soak Reports: actual saved fraction, missed deadlines, maximum gap, temperature, battery power, RAM, exceptions and source+APK SHA.

---

# 12. Reuse existing open-source libraries; legal due diligence

Look at docs/OPEN_SOURCE_REUSE.md first, then inspect current upstream code and license:

| Existing project/library | Target reuse / benefit | Known license/approach |
|---|---|---|
| https://github.com/LibreStatic/opencinecam | Camera2 advanced manual controls, DNG, monitoring, lens capabilities, UI ideas | Apache-2.0; inspect exact commit and license before copying |
| https://github.com/talgatv/Simple-Motion-Cam | Screen-off camera service diagnostics, watchdog, LAN remote, thermal policy | MIT; **do not adopt loop-overwrite retention** |
| https://github.com/android/camera-samples | Official Camera2 AF/AE/manual, viewfinder coordinate mapping, DNG and tests | Reviewed Apache-2.0 file headers; preserve attribution |
| https://developer.android.com/jetpack/androidx/releases/camera | CameraX and Camera2Interop | Jetpack Apache-2.0; already used |
| https://developer.android.com/media/media3/transformer/transformations | Codec/video transformations, GPU effects and image-sequence input | Apache-2.0; benchmark vs streaming MediaCodec |
| https://developer.android.com/jetpack/androidx/releases/room | Project/frame metadata, migrations, crash-resilient index | Apache-2.0; filesystem originals still authoritative |
| https://developer.android.com/jetpack/androidx/releases/datastore | Per-project/per-camera settings state | Apache-2.0 |
| https://developer.android.com/develop/background-work/background-tasks/persistent | Deferred export/thumb/import jobs | Apache-2.0; **not precise camera scheduler** |
| https://github.com/Goooler/android-gpuimage | GPU filters, preview effects if needed | Apache-2.0; benchmark need before adding |
| https://github.com/woheller69/TimeLapseCamera | Black-box multi-day/screen-off reference | GPL-3.0: no silent proprietary integration |
| https://sourceforge.net/projects/opencamera/ | Black-box controls/RAW reference | GPL: behavior reference only unless license obligations accepted |

**Integrate only proven narrow modules; avoid a speculative entire-backend rewrite.** Record upstream exact SHA, file path, attribution/NOTICE, modifications, licenses, compatibility test and why it beats existing code. Keep network optional/offline-first and do not expose a remote control endpoint without auth/pairing.

---

# 13. Test matrix and evidence gate

| Gate | Test | Required evidence | Allows |
|---|---|---|---|
| G0 | Compile/JVM/Lint | Green GitHub run + APK SHA | **Internal debug only** |
| G1 | Emulator end-to-end image capture | 10 REAL JPEG files, decode/exif/unique IDs after restart, screen UI | Continue camera/UI work; still no public release |
| G2 | S21 actual capture | 10 saved JPEGs, two resolutions/lenses, AF/AE, image metadata, logcat, re-open | May begin user-directed alpha validation, with explicit warning |
| G3 | Image & video feature tests | Both original folder export and valid MP4 None/Blur/Trails, output ffprobe and original SHA match | Internal alpha feature completeness |
| G4 | 8-hour real capture with screen OFF | 8h metrics, drops accounting, no corruption, thermal stable | Eligible for explicitly labelled alpha release after owner review |
| G5 | 72h scheduled capture & faults | Day/night/rebind, restart limits, battery/storage/thermal/Doze, diagnostics | Beta candidate |
| G6 | Two independent 7-day soaks | Reproducible logs and no critical data loss within bounded conditions | Stable candidate, with caveats |

**Strict new release rule:** On this user's current report, G1/G2 have FAILED or are unproven. No valid marketed APK release should be emitted, even if G0 and screenshot smoke pass. A debug artifact may exist in Actions as **UNVERIFIED/DIAGNOSTIC** for diagnosis, but do not claim usable recording.

**CI pipeline additions:** deterministic time/estimator tests, CameraX emulated image saved check, UI screenshot snapshots, codec/MP4 validity test, build reports and artifact hashes. Run lint after APK compilation but never equate "artifact uploaded" with functional release success.

**Issue tracking:** Create a blocking issue for “Start/Record saves zero JPEG on S21”, link back to PR #2, note reproducer, acceptance and logs. Block merge/release until G1 and G2 evidence.

---

# 14. Prioritized implementation work packages

**W0 — Take ownership and freeze release claims (P0).** Fetch GitHub state/PR diff, list current open bugs; create explicit blocker; update AGENTS/test docs and release gates. Commit policy changes immediately.

**W1 — First photo on hardware (P0).** Trace all steps, add event instrumentation, error states, bound camera checks and one-shot diagnostic capture; fix specific error; write fake camera and emulator E2E tests; obtain S21 first-10 evidence. Keep full-res JPG originals; never discard on error. Required: screenshot of latest confirmed JPEG and 10 actual files.

**W2 — UI architecture and design system (P0).** Replace old-school huge programmatic layout with professional Compose UI; home/gallery, camera preview, status, bottom-sheet sections, camera capability UI, inline help. Save screenshot snapshots to Git and visually critique 3 iterations, not just compile. No blocked capability shown as active.

**W3 — Manual camera and focus metering (P0).** Distinct AF/AE ROIs, autofocus confirmation from hardware, AE state, lock/manual sliders and ranges; peaking optional. Correct lens and crop mapping on S21; save focus presets by project/lens. Device test results required.

**W4 — Maximum photo and honest planner (P0).** Enumerate available sensor JPEG sizes, request/resolution/fallback distinction, actual first-frame EXIF, photo/MP4 storage estimates, unlimited-mode daily projections and disk runway, 4:3 and 16:9 presets. Unit tests and S21 corroboration.

**W5 — Export and media editor (P0).** Original-folder export, ZIP, JPEG/DNG handling, valid H264/HEVC/AV1 where supported, effects amount and previews, 4K/8K gated, timeline and scrub. Verify 10k-image bounds.

**W6 — Night schedule/power/resilience (P0).** Real physical screen OFF, overnight wake and retries; thermal hysteresis, storage prevention, OEM battery constraints; 8h/72h/7d runs.

**W7 — PRO parity / productization (P1).** Unlimited Photolapse projects, import bulk, external SD, RAW, timestamps, BLE/volume, local Wi-Fi opt-in, accessibility, localization FR/EN/DE, documentation/permissions/security, commercial signing and release rules.

### Per-PR delivery contract

Every PR iteration must include:
- Files/commit SHA and links.
- User-visible change plus before/after emulator screenshot(s).
- Tests executed (actual CI run ID; include genuine outputs), failing tests remaining and hardware caveats.
- Measured photo/MP4 file examples if a capture/export feature changed.
- Updated docs/MASTER_TODO.csv and regenerated docs/MASTER_TODO.md using tools/progress.py --write / --check.
- Source and dependency license implications.
- No silent functional regressions, false AF/AE feedback, original image deletion or unsupported capability claims.

---

# 15. Detailed UX acceptance checklist

## Project selection / home
- [ ] New project created and visible immediately with correct original folder; names/rename/presets persisted.
- [ ] Last frame thumbnail real and saved, not a preview capture.
- [ ] Photos and videos clearly separated; Timelapse and Photolapse both first-class.
- [ ] Frame count and storage derived from originals; correct after crash and restart.

## Capture
- [ ] Start only changes UI to Recording after first committed JPEG.
- [ ] One-shot Test Photo success/error; Stop idempotent.
- [ ] 10 valid JPEGs at 2–5 s on emulator and S21.
- [ ] Camera still works when settings sheet opens/closes and device rotates.
- [ ] Real selected lens and resolution reported after first JPEG.
- [ ] No giant opaque widgets cover the scene; focused/exposed ROIs visible without clutter.
- [ ] Status shows last capture, next capture, errors and storage runway.

## Camera controls
- [ ] AF and AE points independent; ROI size + lock mode separate.
- [ ] AF state SEARCHING→LOCKED/FAILED reflected from sensor, no fake lock.
- [ ] Exposure zone affects luminance as expected; metadata/EV shown where supported.
- [ ] Capability ranges and unsupported states clear.
- [ ] Focus/AE markers persist after activity restart and camera rebind.
- [ ] All exposed JPEG sizes listed; largest actually recorded if supported, otherwise explicit fallback.

## Schedule/capacity
- [ ] Unlimited gives photos/hr/day, 1/7/30 day projections and storage runway, not a fake finite total.
- [ ] Fixed duration/frame count gives accurate photo/GB/video-runtime estimates.
- [ ] Estimates refine from measured JPEG sizes and free disk.
- [ ] Weekdays/DST/overnight and battery/thermal protections are user-visible.

## Export/media
- [ ] Originals accessible individually, optional ZIP, never destroyed by encoder.
- [ ] MP4 None, Motion Blur and Light Trails; strength/window adjustable with preview.
- [ ] 1080p, 4K/8K only when supported; codec/FPS/bitrate actual values and sizes.
- [ ] Progress, cancel, reverse, timestamp overlay and video playback verified.

## Visual QA
- [ ] Polished screens at 360dp/412dp/tablet, portrait/landscape, dark/light, 200% font.
- [ ] 48dp touch targets, TalkBack semantics, proper insets/keyboard.
- [ ] Real screenshots checked against reference-app behavior and independent design spec.
- [ ] No native Spinner-heavy form, unexpected white void, clipped labels or false recording state.

---

# 16. Operational commands and GitHub working pattern

Read current GitHub PR #2 and branch before doing anything. Use the connected GitHub tool to fetch/update files and workflow results. The repo is public and GitHub-hosted Actions jobs now run; avoid assumptions based on the earlier private-runner problem.

**Build/test (connected Linux runner with Android SDK 36):**

    gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --stacktrace

**Pure Kotlin/roadmap checks:**

    bash tools/test-policy.sh
    python3 tools/progress.py --write
    python3 tools/progress.py --check

**Emulator test baseline:**

    bash tools/emulator-smoke.sh

The baseline emulator script currently **only opens activity and takes screenshots**; it must be upgraded to actually Start and verify captured image files.

**S21 diagnostic commands (with user permission, no private data posted publicly):**

    adb devices
    adb shell dumpsys power
    adb shell dumpsys thermalservice
    adb shell dumpsys activity services
    adb logcat -c
    adb logcat -v time > timelapse-s21-logcat.txt

Photos live under the application's app-specific Pictures/projects location; verify the actual resolved path. If ordinary adb shell cannot read it, use debug build application diagnostics or app-authorized test export. Do not falsely conclude “no photos” because images do not automatically appear in the public Gallery.

**GitHub evidence:**
- Android build successful: https://github.com/jboehm1/timelapse/actions/runs/40978329365
- Emulator UI smoke successful: https://github.com/jboehm1/timelapse/actions/runs/40978329505
- Pure Kotlin: https://github.com/jboehm1/timelapse/actions/runs/40978329429
- Active PR: https://github.com/jboehm1/timelapse/pull/2
- Screenshots expected in feature branch: docs/screenshots/emulator/app-home.png, app-settings.png, app-settings-scroll.png (verify the commit).
- Main handoff (this document): docs/HANDOFF_MASTER_2026-10-30.md.

**DO NOT merge PR #2 to main just to make docs visible.** A source-visible GitHub feature branch with committed handoff and screenshot evidence satisfies "pushed to Git"; main must remain protected from known broken capture functionality.

---

# 17. Risks, limitations, legal/product decisions

1. **Critical current product defect:** no JPEG photos reported on user hardware. Unknown cause; root-cause investigation and device proof first.
2. **Unattended restart not universally guaranteed:** Android 15/FGS restrictions, Doze and OEM kill policies. Respect OS/security, document exact supported modes.
3. **Screen OFF ≠ black preview:** verify physical screen power with dumpsys and energy measurement.
4. **High-res and physical camera access differ from stock Samsung Camera:** third-party API caps are real; no vendor privilege assumptions.
5. **CameraX AF success is not proof of optical sharpness:** use sensor state and optional peaking; improve honest feedback.
6. **FrameStore correctness not yet proven on real JPEG camera callback:** validate atomic file and journal from live capture; no file loss assumptions.
7. **Heavy video exports can overheat or exhaust RAM/storage:** bounded decode cache, thermal constraints and high-volume tests.
8. **Copyright and commercialization:** our public code original rights are reserved, but it is visible/copyable technically; third-party licenses Apache/MIT must be respected, GPL code not silently reused; independent product brand/UI needed.
9. **Velocity Lapse is a closed third-party benchmark, not an open-source base.** APK acquisition must be legal, PRO features legitimately obtained, no redistribution/decompiled code copied.
10. **Privacy:** construction footage may contain property, people, addresses or GPS/EXIF. Keep local by default, redact diagnostics/screenshots, no uploaded customer images without consent.
11. **A higher-capacity model does not remove dependencies on GitHub runner, Android emulator, network or access to a physical S21.** Work mode/computer use may handle longer chains, but be honest about actual execution.
12. **CI green may still permit runtime failure:** release gates must be end-to-end and hardware-aware.

---

# 18. Completion definition and handover back to user

**"Finished" means:** create/select a project; preview and independently pick AF/AE zones; show a sensor-confirmed target and manual controls where supported; capture ten/ten real JPEGs reliably; enable verified maximum resolution; show accurate schedule/space estimates; run with screen off in documented conditions; edit/scrub frames; export intact JPG sequences and valid MP4 None/Motion Blur/Light Trails with adjustable effects; demonstrate graceful failure and persistence; modern UI passes screenshot design reviews; code/doc/tests/screenshot evidence pushed to Git; 233/233 acceptance items completed and provenance documented, with 8h, 72h and repeat 7d tests appropriate to the claimed release stage.

A new chat should **start by investigating CAPTURE-P0-001, not by asking the user to restate these requirements or immediately drawing another superficial mockup**. Read all linked docs/code, use GitHub tools and existing workflow, iterate with verifiable commits, and record remaining failures precisely.

---

# 19. Research and official documentation sources

These sources were actually consulted during handoff preparation, but their current publication dates/APIs should be checked during implementation:
- Velocity Lapse Google Play, current app description and capabilities: https://play.google.com/store/apps/details?id=com.velocitylapse.velocitylapse
- Velocity Lapse official manual: https://guide.velocitylapse.com/
- Velocity Lapse Timelapse mode: https://guide.velocitylapse.com/capture_modes/timelapse_mode.html
- Velocity Lapse Photolapse mode: https://guide.velocitylapse.com/capture_modes/photolapse_mode.html
- Velocity Lapse editor: https://guide.velocitylapse.com/editor.html
- Velocity Lapse export/size information: https://guide.velocitylapse.com/export.html
- Velocity Lapse FAQ (Samsung/third-party camera limitations): https://velocitylapse.com/faq/
- Google CameraX FocusMeteringAction API: https://developer.android.com/reference/androidx/camera/core/FocusMeteringAction
- CameraX focus metering return semantics: https://developer.android.com/reference/androidx/camera/core/FocusMeteringResult
- CameraX ResolutionSelector: https://developer.android.com/reference/androidx/camera/core/resolutionselector/ResolutionSelector
- Android Camera2 3A AF/AE state: https://source.android.com/docs/core/camera/camera3_3Amodes
- Android camera choice guide: https://developer.android.com/media/camera/choose-camera-library
- Android Camera2/CameraX samples: https://github.com/android/camera-samples
- Apache-2.0 advanced reference: https://github.com/LibreStatic/opencinecam
- MIT Android camera reference: https://github.com/talgatv/Simple-Motion-Cam
- Google Media3 Transformer: https://developer.android.com/media/media3/transformer/transformations
- JADX: https://github.com/skylot/jadx
- Apktool: https://apktool.org/
- Project technical document index: https://github.com/jboehm1/timelapse/tree/feat/android-capture-export-alpha/docs

**End of handoff.** The new engineering agent should report concrete changes and evidence, not promotional completion claims.
