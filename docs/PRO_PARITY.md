# Full Velocity Lapse PRO feature contract

**Reference:** https://velocitylapse.com/pro/ and https://guide.velocitylapse.com/export.html . This is an independent recreation of *features*, not copied software. **Status:** required specifications; not evidence of an implemented feature.

**Status key:** SPEC = formally required; IN_PROGRESS = repository code exists but APK/device validation is pending; DEVICE_VERIFIED = proven on target Samsung S21. A row labeled SPEC does not mean ready to use.

| ID | Feature | Priority | Acceptance criterion | Current |
|---|---|---|---|---|
| PRO-001 | Unlimited Timelapse projects | P0 | Create, rename, select, resume and delete 100+ independent projects; no artificial count cap; disk capacity limits explained | SPEC |
| PRO-002 | Unlimited Photolapse projects | P0 | Add images by hand, retain interframe ordering and export same as interval projects; no cap beyond disk/memory policy | SPEC |
| PRO-003 | Unlimited import | P0 | Bulk import large folders via SAF Documents tree, enumerate incrementally (no thousands of URIs through Photos picker); validate JPG/JPEG/PNG; avoid unbounded memory; preserve sequence order, errors and source images | SPEC |
| PRO-004 | Scheduled capture sessions | P0 | Start date/time, end date/time, weekdays; repeat daily, timezone/DST, missed alarm; actual camera start from background subject to Android rules and clear status notification | SPEC |
| PRO-005 | All available camera lenses | P0 | Enumerate every externally exposed camera2 lens ID; front/back/external, lens FOV description; never invent focal length or assume 4 lenses | SPEC |
| PRO-006 | Customized timestamp overlay | P1 | Toggle/date/time pattern, font size, text color, position, padding; apply non-destructively to exports, optional per-frame photos; consistent timezone | SPEC |
| PRO-007 | Video codecs | P0 | Enumerate all supported encoder names & capabilities on device; AVC/H264, HEVC/H265, AV1, APV where exposed; do not claim options not actually encodable | SPEC |
| PRO-008 | 1080p/4K/8K | P0 | Capture sizes and export sizes from measured supported ImageCapture/encoder capabilities, source resolution >= target; proper portrait and landscape handling | SPEC |
| PRO-009 | Bluetooth remote shutter & volume keys | P1 | Single photo, start/stop from headset/remote multimedia events and volume keys while app visible or registered appropriately; duplicate suppression; disconnect tests | SPEC |
| PRO-010 | Motion blur + Light Trails | P0 | None=1 source; Motion blur=weighted neighbor integration; trails=component-wise brighten/max; configurable strength/window; streaming bounded memory, preserve originals | IN_PROGRESS |
| PRO-011 | Save to external storage | P0 | User picks SAF document tree, permission persisted and tested, file atomicity and removable media handling; internal path always available as fallback | SPEC |
| PRO-012 | RAW Bayer DNG | P1 | Capability check REQUEST_AVAILABLE_CAPABILITIES_RAW; Camera2 RAW_SENSOR + DngCreator; simultaneous JPEG+RAW when supported; skip unsupported lenses | SPEC |
| PRO-013 | Image sequence export | P0 | Save individual JPEG/RAW files, optional ZIP; no artificial frame cap; never delete originals on failed export | SPEC |
| PRO-014 | Video bitrate / frame rate | P0 | Auto, Low/Medium/High presets + custom Mbps; 24/25/29.97/30/60 FPS supported by codec; measured output FPS/timing/size | SPEC |
| PRO-015 | Tap to focus | P0+ | Independent modes: AF / AE / both; tap in correct preview coordinates; focus status and optional AF lock | IN_PROGRESS |
| PRO-016 | Background/day-night capture | P0+ | Camera foreground service created while app visible, physical screen OFF, controlled thermal/storage limits, day/night transitions, 72h then 7d device soak | IN_PROGRESS |
| PRO-017 | Multi-day continuity | P0+ | Durable state, stable naming, journal/reconcile, no catch-up bursts, bounded retry and safe thermal cooldown; clear impossible recovery conditions | IN_PROGRESS |
| PRO-018 | Remote Wi-Fi status & controls | P2 | LAN-only paired interface, last image/progress/errors and schedule changes; no Internet requirement | SPEC |

## Correspondance avec la checklist % actuelle

Chaque option PRO reste obligatoire et possède un ou plusieurs items vérifiables dans **[MASTER_TODO.md](MASTER_TODO.md)**. Les pourcentages sont des estimations à date de la checklist ; le score réel est maintenu dans `MASTER_TODO.csv`. Une option comprenant plusieurs sous-fonctions n'est terminée que si **toutes** atteignent 100 %.

| Option PRO | Checklist liée | Progrès actuel des sous-fonctions |
|---|---|---|
| PRO-001 Unlimited Timelapse projects | B-01 | 25 % |
| PRO-002 Unlimited Photolapse projects | B-02 | 10 % |
| PRO-003 Unlimited Import | B-03 / B-04 / B-14 | 25 / 10 / 0 % |
| PRO-004 Capture Scheduling | E-01 / E-02 / E-03 / E-09 | 25 / 10 / 10 / 0 % |
| PRO-005 All Camera Lenses | C-05 / C-06 | 25 / 10 % |
| PRO-006 Customized Timestamps | G-18 | 25 % |
| PRO-007 Video Codecs | G-16 / G-17 | 25 / 0 % |
| PRO-008 4K / 8K | G-10 / G-11 | 25 / 25 % (non validé) |
| PRO-009 Remote Shutter Bluetooth/volume | H-04 / H-05 / H-06 | 25 / 10 / 0 % |
| PRO-010 Motion Blur/Light Trails | G-04 / G-05 / G-06 | 25 / 25 / 10 % |
| PRO-011 External SD Storage | B-09 / F-18 | 10 / 0 % |
| PRO-012 RAW DNG | C-08 / C-09 | 0 / 0 % |
| PRO-013 Image Sequence | B-06 / B-07 | 25 / 25 % |
| PRO-014 Bitrate and FPS | G-12 / G-13 / G-14 / G-15 | 25 / 10 / 25 / 0 % |
| PRO-015 Tap to Focus | D-01 / D-02 / D-06 / D-07 | 25 / 25 / 25 / 25 % |
| PRO-016 Background Scheduling | E-05 / E-07 / E-09 / J-11 | 25 / 25 / 0 / 0 % |
| PRO-017 Multi-Day Continuity | B-10 / F-10 / J-12 | 10 / 10 / 0 % |
| PRO-018 Wi-Fi Remote Control | H-07 / H-08 / H-09 | 0 / 0 / 0 % |

## Implement in this order
1. Ensure an Android debug APK is built and tests run.
2. Camera foreground service + durable JPEG projects + tap AF/AE + safety transitions; verify with device display physically OFF.
3. Export true JPEG sequence and MP4 codecs with None / Blur / Trails, then import and project browser.
4. Scheduled multi-day behavior (device proof), external storage, timestamp overlays, camera enumeration and remote keys.
5. RAW DNG on supported lens(es), advanced codec matrix, 4K/8K validation and 7-day endurance.

## Reference effect semantics
Velocity Lapse calls its output effects Motion Blur and Light Trails, but does **not** publicly specify its exact source algorithm. We do not claim binary- or pixel-perfect parity. Independently implement temporal blending and light-max composition; compare visually with reference samples, record kernel/opacity and quality trade-offs. Morphological/optical-flow processing is optional, not a prerequisite.

## Platform requirements
Read [PROJECT_SPEC.md](PROJECT_SPEC.md), [ARCHITECTURE.md](ARCHITECTURE.md), [TEST_PLAN.md](TEST_PLAN.md), and [AGENTS.md](../AGENTS.md). Restricted background camera start on Android 14/15 and Samsung S21 behavior require real hardware tests. Android camera permissions do not guarantee unattended restart after process force-stop or reboot.
