# Focus and exposure field guide

This guide describes intended controls. Exact support is lens/firmware dependent; a displayed request is not proof the sensor applied it. The current app remains unverified on Galaxy S21.

## Focus

- **Continuous AF:** let the camera refocus as the scene changes. Before a long run, focus on the fixed building surface, then use **Lock AF** if the lens supports it.
- **Focus zone:** choose Focus mode and tap the target point. The green marker represents the requested region. Wait for a focus result before locking.
- **Manual focus:** use only a value exposed by Camera2 for the selected lens. Diopters describe reciprocal focus distance; `0 D` generally corresponds to infinity on a lens that reports this range. Change in small steps and inspect a captured original at full resolution. Do not assume another lens uses the same range.
- If AF/manual focus is unsupported or fails, preserve the previous state and display the failure. Do not show LOCKED after a touch alone.

## Exposure

- **Auto exposure:** recommended for outdoor work where daylight changes. It lets the camera adjust brightness through clouds and day/night transitions.
- **Exposure zone:** choose Exposure mode and tap the brightness reference area. The orange marker is independent from the green focus marker.
- **AE lock:** lock only when scene brightness should remain fixed. It may cause later frames to become too dark or too bright as daylight changes.
- **Manual ISO/shutter:** use only camera-reported supported ranges. Long shutter times may exceed the capture interval and reduce motion sharpness. Keep exposure time below the interval with margin for sensor readout and file commit.
- Auto white balance is the safest default across changing daylight; a locked/manual value can create color shifts if conditions change.

## Before a long run

1. Confirm the selected lens and actual image dimensions in saved JPEGs.
2. Set focus on a stable object at the construction plane; inspect the image, then lock only if supported.
3. Keep AE adaptive unless a controlled lighting condition justifies a lock/manual value.
4. Verify after camera rebind, app restart and any schedule transition. Persisted markers do not prove hardware state was restored.

The canonical control/state contract is [CAMERA_CONTROL_UX.md](CAMERA_CONTROL_UX.md). Accurate region mapping, independent AF/AE behavior, physical focus and manual exposure ranges still require S21 validation.
