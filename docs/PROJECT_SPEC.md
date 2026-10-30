# PROJECT_SPEC — authoritative product requirements

**Version:** 1.0 · **Date:** 2026-10-29 · **Status:** specification, not a claim of implementation.
**Reference device:** Samsung Galaxy S21, Android 15; adapt by capability detection to other Android phones.

## 0. Precedence and change control
This specification outranks README, issue titles, UI mockups and optimistic prior-chat claims. Every requirement has an ID and acceptance test. Changes must update the spec and tests together. Status labels: SPECIFIED, IMPLEMENTED, COMPILED, DEVICE_VERIFIED, SOAK_VERIFIED; never infer a higher label.

## 1. Product and user journeys
**P0:** A camera fixed on a building captures independently saved frames at a configurable interval over consecutive days. The user can later export multiple variants without reshooting. Enduring reliability, image preservation and safe thermal behavior have priority over continuous shooting.

**P0:** Day schedule example Monday–Friday 07:00–18:00, local time, including weekends option, timezone and daylight-saving-time handling. Option to record continuously or daylight-only. User can stop at any time.

**P1:** Remote view/status/control over local Wi-Fi, optional offline functionality. Network failure must not interrupt capture.

**P2:** Camera automation and anti-flicker, cloud sync, RAW/DNG when supported, optional GPS metadata, advanced codecs, cinematic previews.

## 2. Feature parity — Velocity Lapse functional reference
- **EXP-001 (P0):** Export either MP4 video or original image sequence. Image sequence is individually accessible JPEGs and optional ZIP; never use a ZIP as the only source archive.
- **EXP-002 (P0):** MP4 effects **None**, **Motion Blur**, **Light Trails**. None: each output frame uses one original. Motion Blur: temporal integration with adjustable odd sample window and documented boundary conditions. Trails: light-max composition with documented window. Preserve originals.
- **EXP-003 (P0):** MP4 FPS options 24, 25, 29, 30 and 60 when supported; duration = N/FPS with measured encoder timestamps. Disallow unsupported combinations gracefully.
- **EXP-004 (P0):** Bitrate choices Auto, Low, Medium, High; display actual target Mbps and estimated file size. Prevent false claims: verify device codec capability and measured output metadata.
- **EXP-005 (P0):** 1080p, 4K and optional 8K only if input pixel dimensions, crop/orientation and hardware encoder support permit it. Default 1080p until 4K passes device tests. Prefer H.264 broadly compatible, HEVC optional.
- **EXP-006 (P0):** Atomic export: no partially published “complete” MP4; failed export must not remove originals. Cancel/progress; cleanup incomplete output.
- **EXP-007 (P1):** Project gallery, scrub/preview, select/delete with confirmation, import existing images, resume/add to project, reverse at export.
- **EXP-008 (P1):** Export under low storage: preflight estimated temp size with reserve, cleanup on failures.

- **CAM-001 (P0):** Capture JPEG originals at configurable interval (initial 0.5–3600 s, capability-dependent), timestamps and monotonic frame IDs. Sequence must survive app restart and resume without duplicate names.
- **CAM-002 (P0):** **Tap-to-focus** independent from exposure; user selects Focus, Exposure, Both. CameraX AF/AE metering point uses preview coordinate transform. Show focus confirmation or failure; never report AE-only as AF success.
- **CAM-003 (P1):** Focus lock / manual distance if supported; focus must not hunt unnecessarily through a static construction scene. AE lock/unlock, exposure compensation, ISO/shutter and white balance where camera supports them.
- **CAM-004 (P0):** Camera interruption must never corrupt previously saved photographs. Expose recoverable vs fatal error states and bounded recovery.
- **CAM-005 (P1):** Preserve capture geometry: lens selection, aspect, orientation, crop, consistent composition across days. Capability probe on actual S21.
- **CAM-006 (P1):** Detect suspiciously black/blurred frames and exposure flicker; tag frames and alert, but do not delete originals automatically.

## 3. Long-running capture — hard nonfunctional constraints
- **REL-001:** Run with **physical display OFF** after user authorizes capture where platform allows; do not require OLED black pixels for normal mode. Black screen is a UI fallback only.
- **REL-002:** Use user-started camera foreground service with ongoing notification and correct Android foreground-service type/permissions; do not bind capture lifetime to an Activity or PreviewView. Must handle screen lock/unlock and Activity recreation.
- **REL-003:** A day/night schedule has clear states: IDLE, CAPTURING, NIGHT_STANDBY, THERMAL_COOLDOWN, STORAGE_PAUSED, RECOVERING, NEEDS_USER_ACTION, STOPPED. Persist and audit transitions with causes.
- **REL-004:** For unattended morning resume: **do not stop the user-started camera service at night by default**. Enter low-duty standby (stop photographing, release camera where supported, keep service/notification active) and test reacquisition at dawn while screen OFF. If reacquisition fails on device, choose an evidence-backed fallback or require user interaction. Do not promise guaranteed automatic camera service starts from background/BOOT_COMPLETED.
- **REL-005:** Scheduling uses wall-clock + timezone for day boundaries, monotonic clock for spacing. DST, manual clock change and missed wakeup tests required. After a late wake, skip missed frames; never queue a burst.
- **REL-006:** Watchdog with explicit progress timestamp and upper bound on single capture latency. Retry with exponential backoff and cap; enter NEEDS_USER_ACTION on repeated failures. Save reason to audit log and notification.
- **REL-007:** On device thermal WARNING, progressively lower workload (resolution/quality, capture rate, preview/export). On SEVERE/CRITICAL, suspend capture safely until recovery; never bypass Android thermal shutdown. Use PowerManager thermal status APIs where supported.
- **REL-008:** Detect free-space floor before each capture and export; stop safely before disk exhaustion; after storage recovers, resume only under explicit safe policy. Originals and manifest must remain consistent.
- **REL-009:** Low battery, power-loss risk, overheating while charging, charger reconnect/disconnect, Doze, Samsung battery policy and unexpected process death must be reported and tested.
- **REL-010:** Atomic frame save (temp + sync/rename if possible), manifest/index reconciliation on startup, validation of JPEG decode/length. File sequence and project manifest are recoverable after partial write or kill.
- **REL-011:** No unbounded image buffers/threads/retry queues. Backpressure must skip delayed frames rather than building a backlog. Camera and encoder use independent bounded executors.
- **REL-012:** Event log includes: timestamp, state, frame ID, expected/saved/missed count, average delay, storage free, battery %, charging, thermal status, camera errors and recovery actions. Logs rotate and do not contain sensitive personal data by default.
- **REL-013:** Process killed/force-stop/phone reboot: document which recovery is possible on stock Android 15; “needs user action” state and notification acceptable for OS-prohibited unattended restarts. No root or permission bypass.
- **REL-014:** Long export cannot block capture or cause runaway memory/heat; defer export during scheduled capture unless resource budget proves otherwise.
- **REL-015:** Tests must measure time-to-next-frame, JPEG correctness and memory/storage over >=8h, 72h and 7d. Keep evidence and device OS/firmware version.

## 4. Thermal and storage default policy (initial values to validate)
- Thermal NONE/LIGHT: continue nominal; MODERATE: reduce preview/display work and optionally lengthen capture period >=2x; SEVERE: suspend camera capture, log and show notification; CRITICAL/EMERGENCY: stay suspended, no automatic hot retry.
- Hysteresis: recovery only after several minutes of lower status; exponential camera retry ceiling (max 5 in 30 min).
- Storage reserve: keep at least max(512 MiB, 5% of volume) free where volume information supports it. If insufficient: halt new capture, **never delete originals**.
- Frame in progress timeout (initial): 2x expected duration with minimum 15 s, bounded retry only; distinguish slow camera vs deadlock.
- Battery: configurable floor (default 20% when unplugged), enter power-saving pause and warn; charging is not exemption from thermal safeguards.
- All numeric thresholds above are initial engineering defaults, not calibrated limits or universal device guarantees.

## 5. Day/night policy and screen behavior
See [ARCHITECTURE.md](ARCHITECTURE.md) for the state machine. **Preferred:** user starts long-lived camera foreground service once, turn physical display OFF, daytime capture, night camera closed (if supported), same service alive for wake. No full-screen black UI as default and no KEEP_SCREEN_ON outside setup/preview. Morning camera reopening is a **device-test gate**. Android may terminate the service despite user settings; unknown/force-stopped app cannot reliably self-recover without permitted interaction. Overnight fully stopped service is not accepted as reliable without proof of legal platform-supported relaunch.

## 6. Security/legal/privacy
- Never commit proprietary third-party APK/decompiled code/resources or trademarks to public Git. Use reference app documentation, screenshot of user-observed UI and black-box validation without shipping third-party binaries.
- Camera data stays on-device unless user opts in to transfer.
- Lock local Wi-Fi control behind pairing/token, LAN-only default; never expose unauthenticated HTTP control to internet.
- No analytics, microphone, or arbitrary network permissions by default.
- Signing credentials only in secure secret storage. Build artifacts must clearly identify debug/release signing.

## 7. Formal acceptance gates
| Gate | Evidence required |
|---|---|
| G0 Build | CI APK + compiled unit tests; no unsupported runtime claims |
| G1 Capture | S21: 1h/8h capture, no application crash, file count/accounting, tap AF/AE visually verified |
| G2 Export | S21: JPG sequence, MP4 None/Blur/Trails at 1080p and any supported 4K, metadata FPS/bitrate/duration tested, interrupted export recovery |
| G3 Overnight | S21 display physically OFF, 07:00–18:00 for 3 days, nighttime non-capture, autonomous wake *demonstrated* or limitation clearly shown |
| G4 Faults | Thermals, full storage, power cut, camera occupied, kill/restore, clock jump, network loss; no valid JPEG lost |
| G5 Production candidate | S21 >=7-day soak with documented frame-loss rate, thermal measurements, error logs, success criteria agreed |

## 8. Current evidence
Spec and architecture are written. No confirmed 72h/7d on-device test. Avoid saying anything is production-ready or that “works with screen off” is proven until G3 succeeds.

## 9. Traceability policy
Every PR shall state requirement IDs addressed, tests run, tests missing, failure modes changed, and whether documentation has been updated.

## 10. Mandatory full PRO functionality
Every item in [PRO_PARITY.md](PRO_PARITY.md), **PRO-001 through PRO-018**, is a product requirement. PRO_PARITY is normative, and status is explicitly SPEC/IMPLEMENTED/DEVICE_VERIFIED; nothing there is optional merely because the app is alpha. Features are released only after the corresponding measurable acceptance criteria pass. Any external references are for functional comparison, not copying proprietary source or binaries.


## 11. Independent camera setting regions and manual controls

The exact binding interaction contract is [CAMERA_CONTROL_UX.md](CAMERA_CONTROL_UX.md). It is required for this product. Summary:

- **CAM-007 (P0):** The preview offers independent persistent focus and exposure editing targets, showing two separate markers, chosen region size, and explicit hardware feedback. Selecting one target must not silently shift or reset the other.
- **CAM-008 (P0):** Focus accepts continuous autofocus, user-selected AF zone with optional long-lived lock, and manual diopter focus if supported by the lens. The camera capability range must be respected, and focus success is not inferred from a tap alone.
- **CAM-009 (P0):** Exposure accepts matrix auto, point-based adaptive auto, optional exposure lock, and manual ISO/shutter on lenses supporting those controls. AE locking is NOT the default for changing outdoor daylight. Focus must not be modified by an AE-only tap.
- **CAM-010 (P1):** Independent white balance mode (auto, lock, and manual only if supported). Surface actual supported values, no pretend sliders.
- **CAM-011 (P0):** Focus/exposure regions and lock intentions persist per project and per lens, with restore/verification on camera rebinding and app restart. A persisted UI marker is not proof hardware metering was successfully restored.
- **CAM-012 (P0):** The UI distinguishes planned, user-requested, hardware-applied and visually verified states; unsupported controls and unstable firmware behavior must be displayed clearly.

**Device quality gates:** cameraX focus/metering state, Camera2 AF/AE/AWB region counts, true AE lock support, physical focus range in diopters, accurate preview-to-sensor coordinate transform, start/stop and scheduled rebind behavior on S21.

## 12. Commercialisation and repo visibility

As verified on GitHub on 2026-10-29, the repository is **private**. GitHub-hosted CI minutes in private repositories count toward the owner's plan allowance; do not assume public-repository free CI. If commercial proprietary development is intended, favor private storage plus local/self-hosted build runners. A separate public open-core component may be created deliberately if desired. Keep proprietary credentials, signing keys and third-party APKs out of Git. Any code already exposed publicly may have been copied or archived even if future commits move to a private repo. A later closed-source product should segregate confidential software and assets **before** adding them to public history. Licensing/commercial strategy is a separate owner decision. Public GitHub hosting does not imply permission for others to reuse copyright-protected code in the absence of a license, but visibility increases practical copy risk.


## 13. Avancement global et strict suivi 100 %

**[MASTER_TODO.md](MASTER_TODO.md)** est le tableau exhaustif de 233 sous-tâches (fonctionnalités, fiabilité, sécurité, tests, documentation, packaging) avec scores, priorités et critères de réussite. **MASTER_TODO.csv** est la source canonique modifiable ; exécuter `python tools/progress.py --write` après tout changement et `--check` lors de la validation. Les pourcentages de code non compilé ne dépassent pas 25 %, la certification longue sur S21 exige les preuves des tests réels. **Ne pas confondre la moyenne d'avancement avec la qualité de production.** Les exigences PRO-001..018 ainsi que CAM/EXP/REL sont des prérequis absolus pour 100 %.

## 14. Diagnostic v0.3 additions — 2026-10-30

- Requested interval supports 0.5–3600 seconds with exact millisecond parsing. This is nominal cadence; slow camera calls skip slots, never queue a burst. 0.5 seconds is not an S21 throughput claim.
- A finite session stops after N newly committed frames; existing project originals do not count toward N. A Test Photo and Photolapse action each save exactly one new original, subject to thermal/storage/battery safety.
- Trash is reversible metadata, with stable original paths. It hides a project and frees no disk space; active projects cannot be archived. No permanent deletion has been added.
- Planner days mean active shooting days. Fixed-frame sessions compute totals from N; unlimited sessions show per-day and active-day projections. Photo-size medians and storage reserve are explicit estimates, not capacity guarantees.
