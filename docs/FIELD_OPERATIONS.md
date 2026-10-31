# Multi-day construction-site operation guide

This guide is a conservative setup checklist, not a runtime guarantee. The diagnostic app has not passed the S21 long-session or physical-screen-off gates.

## Site setup

- Choose a rigid mount with a fixed view of the construction area. Mark the mount and lens position; avoid touching or digitally zooming between days.
- Protect the phone from rain, dust, direct sun and vibration without insulating it from heat dissipation. Keep vents and the camera area clear.
- Use a reputable, strain-relieved power supply rated for continuous operation. Route cables away from workers, water and moving equipment. Confirm the phone remains powered without relying on an assumed charging state.
- Estimate required storage from measured JPEG sizes and the planned interval. Keep a separate reserve for the system and final exports. Check available storage before each run.
- Keep a second, independently verified copy of important completed work. An in-app export is not a backup until its destination files or ZIP hashes are checked.

## Start-of-day checklist

1. Inspect mount, lens, glass, power connection, device temperature, battery and free storage.
2. Confirm the project, interval, schedule/timezone, lens and resolution. Check that the preview contains the intended fixed frame.
3. Start while the app is visible. Wait for the first actual original JPEG and verify the project count changes only after a commit.
4. Record the source SHA and a start note. For acceptance runs, also record Android build, camera ID, actual JPEG dimensions and diagnostics as described in [TEST_PLAN.md](TEST_PLAN.md).

## During operation

- Check visible service state and the last successful frame when practical. A paused/error state requires investigation; do not assume it will restart silently.
- Watch temperature and storage, especially in direct sun. Stop safely if the phone is hot, storage approaches reserve, power is unstable, framing shifts, or the app reports no new JPEGs.
- Do not use another camera app while capture owns the camera. Avoid OS updates, app data clearing, uninstall, force-stop, reboot or lens changes during an unverified session.
- A schedule, foreground service, charging cable or black screen does not guarantee unattended multi-day operation. Android/OEM policy, thermal shutdown, power loss and user force-stop can interrupt it.

## End-of-day and recovery

1. Verify saved originals and timestamps before making an export. Check expected frames against active schedule windows and note gaps.
2. Copy or export the originals to an independent destination. Compare counts and hashes. Keep the source project unchanged until the backup is verified.
3. Before resuming, verify the same view, schedule, storage reserve, temperature and power. If a service recovery is requested, keep diagnostic logs and original/staging files.
4. For a multi-week project, periodically retrieve a verified backup instead of waiting until the end of the job.

Screen-off operation, morning wake, thermal behavior, battery runtime and day-long reliability require real-device evidence. See the S21 matrix in [TEST_PLAN.md](TEST_PLAN.md).
