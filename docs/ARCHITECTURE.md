# Architecture — long-duration Android timelapse

## 1. Capture and export separation

```text
UI / controls / persistent projects
     │ user-initiated Start
     ▼
Camera foreground service ──► persistent state journal
     │                          ├─ timestamps / IDs / recoveries
     │                          └─ latest battery/thermal/storage
     ├─ schedule engine (wall time / timezone + monotonic frame deadlines)
     ├─ safety gate (thermal / battery / disk / camera health)
     ├─ CameraX initially, Camera2 interop for finer control
     └─ write JPEG via temp → validate → rename → journal
                                  │
                             originals (immutable)
                                  │
                                  ▼
                          export worker/queue
                     ├── sequence / ZIP
                     ├── MP4 None
                     ├── MP4 Motion Blur
                     └── MP4 Light Trails
```

Export reads immutable originals. No camera, UI or network task depends on exporting.

## 2. Explicit state machine

```text
STOPPED ──user Start visible──► CAPTURING
                                    │ schedule end
                                    ▼
                                NIGHT_STANDBY
                               (no captures; display OFF)
                                    │ allowed day transition
                                    ▼
                                 CAPTURING
                                    │  ▲
       thermal severe ───────────────┘  │ thermal recovered + hysteresis
                ▼                       │
         THERMAL_COOLDOWN ───────────────┘

CAPTURING ─ low storage ─► STORAGE_PAUSED ─ recovered ─► CAPTURING
CAPTURING ─ camera fault ─► RECOVERING ─ success ─► CAPTURING
RECOVERING ─ retries exhausted ─► NEEDS_USER_ACTION
any state ─ user Stop ─► STOPPED
```

Never spin on startup/recovery. Log cause of each transition. User sees current state in ongoing notification.

## 3. What happens overnight?

| Strategy | Battery/heat | Autonomous morning restart | Recommendation |
|---|---|---|---|
| Leave **screen ON showing black** | OLED pixels low energy, screen electronics/preview/service still use resources | App remains resident; fragile if OS kills it | Fallback only |
| **Screen truly OFF**, keep user-started **camera FGS** active, no photos at night; release camera if reopening is proven | Best promising balance; small ongoing process cost | **Must validate** on Android 15/Samsung | Preferred candidate |
| **Stop FGS** at 18:00, alarm restarts at 07:00 from app in background | Low night resource use | Restricted by Android camera while-in-use permissions; exact alarm alone does not guarantee camera FGS start | Not a dependable default |
| Device-owner / privileged kiosk (dedicated managed device) | Could provide stronger lifecycle control | More freedom but deployment/ownership complexity; no universal immunity to shutdown | Optional advanced deployment |

**Important:** A foreground service is not the same as the phone display: after a service was started from the visible Activity with CAMERA consent, the screen can be physically OFF while the service continues. It is nevertheless subject to OS/vendor policy. The crucial test is whether same service can close/reopen the camera at 07:00 without user interaction while locked.

A screenshot-like “black screen” can be merely a dark UI and still leave display hardware powered. Test with `adb shell dumpsys power` / actual screen power and energy.

## 4. Android platform restrictions
- On Android 14+, background launch of a new camera FGS is restricted because CAMERA is while-in-use. Android 15 further prohibits BOOT_COMPLETED receiver launching a camera FGS. An exact alarm can wake code but does **not** generally remove camera permission restrictions.
- Camera FGS is **created while Activity is visible**. App then sends the screen to sleep while remaining a foreground service with notification. No fake overlays or undocumented permission bypasses.
- If service is killed or device rebooted: persist intent/state, record interruption and request user re-activation when required. Don't claim fully unattended restart.
- AlarmManager exact-alarm special access is not granted by default to most new Android 14+ installs; fallback to inexact schedule with visible warning and measured wake delay.
- If device/OEM behavior makes camera reopening impossible in night idle, compare alternatives: keep existing camera capture session open with no photos, or deploy as managed device; both have cost/thermal implications.

## 5. Reliability engineering
- Long-lived service owns the camera, not Activity lifecycle.
- Safety gate checks before every frame; thermal status listener plus periodic polling, StatFs/available bytes, BatteryManager, charger/battery intents.
- Bounded capture timeout and retry budget (max 5 per 30 min), no backlog. Retry with jitter-free exponential delay capped at 5 min; log recovery outcome.
- Staging of frames and journal; directory scan/reconcile after process restart. Clock jump/DST cannot alter monotonically increasing IDs.
- Work asynchronously and off main thread. One frame in flight. Soft power policy may increase interval when hot.
- Stop encoding at severe thermal state. Always delete partial exported video on failure, never original JPEGs.
- Build logs include device/OS/version and requirements satisfied; soak tests on *actual* S21 firmware.

## 6. Design decisions / unknowns
- **ADR-001** capture originals first, encode later: accepted.
- **ADR-002** normal overnight user-started FGS + physical display OFF: proposed, device validation pending.
- **ADR-003** protect phone temperature over missed captures: accepted.
- **ADR-004** no proprietary APK in repo: accepted.
- **Open question:** Can Camera2 reopen while app remains in previously user-started FGS, locked on S21 Android 15? Must test.
