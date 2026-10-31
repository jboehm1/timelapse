# Samsung Galaxy S21 diagnostic installation and first-capture procedure

This is a **diagnostic-only** procedure for the S21 target. The current build is not device-validated and must not be treated as a usable released camera app. Do not close CAPTURE-P0-001 unless the run below passes.

## Before installation

1. Back up the existing project originals. Record model, Android version/security patch, build fingerprint, current app version, and available storage.
2. Record the source commit and SHA-256 of the APK. Install only a CI artifact explicitly labelled diagnostic/unverified.
3. Do not uninstall the existing app or clear app data to get around a signature mismatch. Either action may erase private originals. Stop and preserve the old installation if Android refuses an in-place update.
4. Use a stable mount, clean lens, reliable external power for extended tests, and enough free storage for the planned session. Keep construction-site images on-device unless the owner separately approves sharing.

## Install and permissions

1. Install with `adb install -r <diagnostic.apk>` when the signature matches. Otherwise install only on a test device with no valuable originals and record that the fresh-install path was used.
2. Launch the app visibly. Grant Camera and notifications only when prompted. Do not grant unrelated file, location, microphone or background-start access unless a documented feature requires it.
3. Create a uniquely named test project. Confirm the project is shown in the library and that no existing project was overwritten.
4. Record the exact camera/lens ID and offered resolution. Select a 2-second interval, disable day-only scheduling for the first capture test, and set a finite session limit if available.

## First capture gate

1. Tap **Start** while the Activity is visible. Confirm the UI stays **STARTING** until a new JPEG is committed. If it shows RECORDING without a new file, stop and mark the run failed.
2. Wait for 10 new images. Verify distinct file IDs, nonzero lengths, JPEG decoding, dimensions, timestamps/EXIF and SHA-256. Record the actual project directory and capture latency.
3. Stop, leave the app, reopen the Activity and project, then re-check the same 10 hashes. Do not substitute an app counter, preview, screenshot, or notification for file verification.
4. Repeat with the physical display OFF. Verify the display state from Android diagnostics; a black app view is not screen-off evidence.
5. If those passes succeed, repeat with each offered rear lens and a different supported resolution, recording requested and actual IDs/dimensions. Unsupported choices must fail visibly rather than silently changing mode.

## Evidence and failure handling

- Required result fields and commands are in [TEST_PLAN.md](TEST_PLAN.md); save `logcat -v time`, power/thermal/service dumps, filenames, dimensions, hashes, source SHA and APK SHA.
- Keep raw site photos private. Attach an anonymized manifest and relevant logs to issue #3 only after review.
- On any timeout, zero-frame result, invalid image, camera bind error or mismatch: stop; preserve staging/original files and logs; record the exact step; do not retry by deleting the project or app data.

The expected screen states are illustrated by emulator evidence in [screenshots/emulator](screenshots/emulator/README.md). Those screenshots are not S21 results.
