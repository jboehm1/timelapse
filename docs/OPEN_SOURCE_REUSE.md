# OPEN_SOURCE_REUSE — assembly strategy (2026-10-29)

**Goal:** Commercial-capable, independently developed Android construction timelapse. Prefer proven libraries over custom infrastructure. Licenses verified against upstream source or published library metadata. Upstream references are not automatically endorsed, production-grade or tested on Samsung S21.

## Open-source components to evaluate

| Source | Components to reuse or evaluate | License | Decision / caveats |
|---|---|---|---|
| https://github.com/LibreStatic/opencinecam | Camera2 manual ISO/shutter/focus, RAW DNG, lens identification, advanced monitoring, thermal safeguards, timelapse | Apache-2.0, upstream LICENSE inspected | **Top priority.** Compare camera backend and selective adaptation with original notices, not copy the entire UI. No verified daily multi-day scheduling. |
| https://github.com/talgatv/Simple-Motion-Cam | Foreground camera service with screen OFF, diagnostics, bounded watchdog, timestamp, authenticated LAN UI, headless control | MIT, upstream LICENSE inspected | **Top priority.** Select isolated modules/concepts with attribution. **Never copy its loop-overwrite policy**: we must preserve original images. Autostart rules require Android 15 hardware tests. |
| https://github.com/android/camera-samples | Official Camera2 controller, AF tap and preview coordinate mapping, manual ISO/shutter/diopter, DngCreator, viewfinder/MediaStore utilities | Apache-2.0 header verified in BaseCamera2Controller.kt | **Top priority:** selectively reuse AOSP scaffolding under original copyright headers, or follow examples via CameraX/Camera2 dependencies. |
| https://developer.android.com/jetpack/androidx/releases/camera | CameraX 1.6.2 JPEG, Camera2 interop, image controls and lifecycle | Apache-2.0 | **Already integrated**, but requires full build and S21 device qualification. |
| https://developer.android.com/media/media3/transformer/transformations | Media3 Transformer 1.11.1, image input, video codecs and GL effects | Apache-2.0 | **Adopt/benchmark as secondary export backend** for short images sequences. For 10k+ JPEG projects, compare against existing streaming MediaCodec architecture to avoid unbounded clip lists. |
| https://developer.android.com/jetpack/androidx/releases/room | Room 2.8.5 durable project/frame job indexing, schema upgrades | Apache-2.0 | **Adopt next**: database is index; originals remain immutable filesystem assets. |
| https://developer.android.com/jetpack/androidx/releases/datastore | Typed transactional settings and camera presets | Apache-2.0 | Adopt to replace SharedPreferences when tests cover migration. |
| https://developer.android.com/develop/background-work/background-tasks/persistent | WorkManager for exports, imports, thumbnail and background deferred jobs | Apache-2.0 | Adopt for non-camera work. It is **not an exact-timing camera scheduler** nor an Android 15 permission bypass. |
| https://github.com/Goooler/android-gpuimage | GPU effects and preview shaders; Maven library io.github.goooler.gpuimage:gpuimage:2.2.1 | Apache-2.0, published POM | Optional benchmark. Our temporal light-trails/blur is custom compositing across stills; filters are not equivalent. |
| https://github.com/woheller69/TimeLapseCamera | Screen-off JPEG/MP4 timelapse, date scheduling and low disk/battery stopping | GPL-3.0 only | **Black-box behavioral reference only** if preserving closed-source distribution. Direct derived use carries source-sharing requirements. |
| https://sourceforge.net/projects/opencamera/ | RAW, focus peaking, ISO, shutters, image metering, histogram | GPL-3.0 or later | **Black-box behavior reference only** for proprietary distribution. |

## Feature integration architecture

1. **Camera:** Keep CameraX for capture/preview initially, introduce a camera backend interface. Adapt official Camera2/OpenCineCam components only if they improve verified AF/AE independent metering, full manual and DNG support.
2. **Durability:** Keep JPEG/DNG originals. Adopt Room for index/migrations; retain the separate pure-JVM FrameStore filesystem layer for atomic frame commit and recovery.
3. **Export:** Implement a pluggable export backend. Evaluate Media3 Transformer for small (<120 images) video and current MediaCodec streaming for 10k/50k photos. Both should support None; temporal Motion Blur and Light Trails need custom shader/CPU blending.
4. **Long session:** Reuse MIT service health and watchdog designs, but never rolling deletes. Camera service is user-started. Physical screen OFF and reopen after night remain S21 acceptance tests, NOT guaranteed by WorkManager or AlarmManager.
5. **Wi-Fi UI:** Consider reusing MIT LAN diagnostics, only with pairing and authenticated access, default-off, no unprotected public network endpoint.
6. **UI:** Build independent controls for two separate focus/exposure regions with independent lock/manual settings. No copied proprietary Velocity Lapse assets.

## Explicit upstream adoption checklist

- Before copying any source, record upstream URL, exact commit hash, filename, license, copyright and modifications in upstream attribution ledger.
- Preserve Apache/MIT notices when incorporating upstream code.
- Do not copy GPL camera/timelapse code into a commercial proprietary app unless the distribution terms are deliberately accepted and complied with.
- Do not commit proprietary third-party APKs, credentials, source dumps or downloaded artifacts to the repo.
- Validate adapted components on S21 with measurements and evidence before advancing checklist completion above 25%.
- This research PR currently **contains no copied third-party application source**; only independent original implementation, library references and test infrastructure.

## Ordered tasks to implement

| Task | Source inspiration | Acceptance |
|---|---|---|
| REUSE-001 | OpenCineCam + Android camera-samples | AF/AE independent point and manual/RAW lens coverage demonstrated on S21 |
| REUSE-002 | Room + FrameStore | 10k images recover consistently after induced crash; no valid originals lost |
| REUSE-003 | Media3 Transformer | Short 120-frame export valid; compare performance, bitrate and motion effects |
| REUSE-004 | Simple-Motion-Cam | 72h screen-off thermal/battery/SDK telemetry and bounded watchdog |
| REUSE-005 | DataStore | Per-project/per-lens camera presets restored after process restart |
| REUSE-006 | WorkManager | Cancel/retry export and thumbnails without interrupting capture |
| REUSE-007 | TimeLapseCam (black-box) | Compare daily scheduling and battery runtime without GPL code reuse |

## Environment limitation

Current ChatGPT sandbox has JDK and Kotlin CLI, but no Gradle/Android SDK and no outbound DNS to official Android/Gradle servers. Attempts to fetch toolchain failed. The existing private GitHub Actions jobs end before any runner starts. Hence **no installable APK was compiled here**. On a network-enabled computer the prepared scripts tools/build-linux.sh or tools/build-windows.ps1 can install the official SDK and compile the APK; both need real execution and error review.

## Diagnostic v0.3 components actually adopted

- Jetpack Compose BOM **2025.04.01**, Material 3, Activity Compose **1.10.1**, Kotlin Compose compiler **2.1.20**: standard AndroidX Apache-2.0 dependencies. Only Material icons core is included; the extended icon set is intentionally excluded to limit size. No copied third-party application UI/assets.
- Coil Compose **3.2.0**, Apache-2.0 verified in the resolved official Maven POM: async, size-constrained local image loading and EXIF-aware thumbnails. No Coil network module and no new app Internet/storage permissions.
- Existing Java NIO/Properties APIs provide a small atomic project-settings file, stable legacy project IDs and reversible trash flags. FrameStore remains the original durability layer. Room adoption is deferred until an indexed frame database is needed; no second competing capture store introduced.
- Existing MediaCodec/EGL encoder is retained and now tested with real emulator JPEGs. Media3 is still a future benchmark, not claimed integrated. Atomic MediaStore publication now receives finalization failures; cancellation and disk reserve are explicit.

Current execution environment now has a local JDK 17, Gradle 8.13 and SDK 36; the old environment limitation paragraph is historical. Device S21 validation remains outstanding.


Export follow-up: retain the existing MediaCodec/EGL backend while benchmarking bounded neighbour caching and texture/buffer reuse. Prefer capability-checked hardware encoding. Calendar-day batches use Android MediaStore pending publication and AtomicFile manifests; no new dependency or imported source. Media3 replacement is not assumed faster without measurement. See EXPORT_PERFORMANCE_2026-10-30.md.
