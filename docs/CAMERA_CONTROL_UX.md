# Camera controls: independent Focus and Exposure regions

Binding UX requirement. Reference: CameraX FocusMeteringAction and Camera2 capture controls.

**Focus**
- AUTO continuous, ZONE autofocus, LOCK at chosen zone, or MANUAL focus distance (diopters when supported).
- User selects Focus editing mode then taps a point in the preview; show a persistent GREEN ring.
- Report REQUESTED / FOCUSED AND LOCKED / FAILED / UNSUPPORTED, and lens focus distance when available.
- A lock must not automatically expire after five seconds. Restore intent after camera rebind.

**Exposure**
- AUTO full-frame, ZONE auto metering, ZONE AND LOCK, or MANUAL ISO/shutter supported by the selected lens.
- User independently selects Exposure editing mode then taps the preview; show an ORANGE ring.
- Exposure and focus may use completely different points. Changing exposure point never silently moves focus.
- For outdoor construction, favor auto exposure from the selected zone without exposure lock, to follow day/night brightness changes.
- Show exposure state, actual ISO / shutter when observable and support/unsupported feedback.

**White balance**
- AUTO, LOCK, optional MANUAL temperature/gains when supported. Explicitly indicate support.

**Selection controls**
- Editable mode: Focus / Exposure / Both. Show BOTH green and orange markers at once.
- Adjustable region size with clearly drawn rectangle/circle; normalized x/y and radius saved per-project and per-lens.
- Convert overlay coordinates through PreviewView.meteringPointFactory after considering sensor rotation/crop.
- Independent focus and exposure locks. CameraX 1.4.2 lacks the newer setLockingMode API: use disableAutoCancel for AF and Camera2 interop for AE lock until upgrade tested.
- Manual values must use actual Camera2 capability ranges, never dummy sliders.

**Acceptance**: distinct simultaneous AF/AE zones; focus lock remains while AE auto changes; project/lens persistence; hardware feedback; correct preview->sensor mapping; proper unsupported behavior; morning rebind restores state. All must be checked on the Samsung S21 before calling the feature verified.

This is specified behavior; code/UI implementation and on-device tests are separate states.
