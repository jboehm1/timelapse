# Source references and rights

Updated 2026-10-29. URLs are for documentation, not third-party code redistribution.

## Velocity Lapse (public features)
- Export options, MP4/Image Sequence, codecs, resolution, bitrate, FPS, effects None/Motion Blur/Light Trails: https://guide.velocitylapse.com/export.html
- Editor/projects/images: https://guide.velocitylapse.com/editor.html
- Camera exposure lock behavior and camera manufacturer limits: https://velocitylapse.com/faq/
- User-provided screenshot 2026-10-29: MP4 video / Image sequence; 1080p/4K/8K (Pro options); Auto/Low/Medium/High bitrate; 24/25/29/30/60 FPS; None / Motion Blur / Light Trails (Pro option); video preview of frame count / FPS. Screenshot used for observed feature parity only.

## Android restrictions and monitoring
- Foreground service background start and camera while-in-use permissions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android 15 restrictions including BOOT_COMPLETED camera FGS: https://developer.android.com/about/versions/15/behavior-changes-15
- Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Exact alarm permissions and fallback: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Thermal status APIs: https://developer.android.com/reference/android/os/PowerManager
- Android Open Source thermal mitigation: https://source.android.com/docs/core/power/thermal-mitigation

## Proprietary reference APK
Do not download third-party binaries from untrusted mirrors. No APK/code/assets from Velocity Lapse should be uploaded to this public Git repository. Black-box UI tests and clean-room independent implementation are preferred. A local lawfully obtained copy may be studied only within applicable copyright/license constraints and must remain out of the public repository.
