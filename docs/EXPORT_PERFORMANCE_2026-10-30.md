# Export performance and calendar-day batches

Status: implemented prototype; Android compilation and runtime checks pending for this change. The preceding v0.3 workspace/capture milestone remains verified at source `6616bffc`, with a conservative global tracker of 23%.

## User decision

On 30 October, the user requested faster, more efficient export and at least separation by day. The existing streaming MediaCodec/EGL backend is retained rather than introducing an unmeasured replacement. Media3 remains the documented secondary backend benchmark; no new dependency or third-party source is added.

## Implemented changes

- Prefer a hardware surface encoder when available, while still capability-checking resolution, rate and surface input. Software fallback stays explicit in recorded metrics.
- One reusable composition bitmap and GPU texture. Upload subsequent frames with `texSubImage2D`; avoid allocating texture storage and a second full output copy for every frame.
- Bounded cache of scaled neighbouring frames for Motion Blur/Light Trails: at most 64 MiB or one eighth of the app heap. Evict frames only when they leave the temporal window; when the budget is small, temporary misses do not evict still-useful neighbours. All bitmaps are released on success, failure or cancellation. Very large output sizes are rejected when the reusable bitmap alone exceeds one quarter of the app heap.
- Prepare the original-file list, date grouping and storage calculations off the UI thread. Snapshot all export controls before entering the worker.
- One MP4 per local calendar day by default, with optional inclusive From/Through dates; uncheck for one whole-project video. Captured filenames provide epoch timestamps; imported photos fall back to modification time. The timezone is displayed and DST boundaries are respected. Preserve source order within each day; effects restart at day boundaries.
- Encode days sequentially, with an independent MediaStore pending row for each. Publish only after muxer finalization. Failure/cancellation removes only the current pending row, preserving already published days and every original. Progress identifies the day, part and frame. Re-export a remaining date range to resume manually; automatic skip/resume is not claimed.
- Atomic `last-video-export.json` records configuration, output URIs, completed days, codec, decoder/cache counts, peak cache bytes, elapsed time and final state. Process-death recovery and orphaned pending-row cleanup are not implemented.

## Acceptance checks added

- JVM: calendar-day routing across both DST transitions, inclusive date range, invalid range, imported timestamps, bounded-cache behaviour and cleanup after renderer failure.
- Actual camera JPEG benchmark: 30 frames, three-frame Motion Blur, uncached vs cached. Expected decoding work is 88 vs 30 calls with 58 cache hits; record both wall-clock durations without asserting a flaky timing ratio.
- Android: one-frame MP4 is readable; two days each produce two samples; cancelling inside the second day preserves the first completed day and removes the incomplete row; originals retain their hashes.
- Host: benchmark/batch JSON, cache bounds and actual MP4 bytes are required. Existing capture, three effect exports and complete workspace tests remain mandatory.

No comparison against the proprietary reference app, S21 speedup, 4K/8K throughput, 10,000-frame stress or hardware thermal endurance is claimed until measured. Day splitting provides boundaries and independent completion; it does not by itself reduce the total frames to encode. These changes map to G-20/G-21/G-22; G-22 is only prototype stage until checks run.


## First execution and follow-up

Source `09b2a763` passes Android build, 29 JUnit tests and lint ([41108138696](https://github.com/jboehm1/timelapse/actions/runs/41108138696)). Emulator [41108138908](https://github.com/jboehm1/timelapse/actions/runs/41108138908) reaches and passes the 30-frame cache work comparison (88 uncached decodes versus 30 cached, 58 hits), then fails the new one-frame duration check. The workspace case also hits a late keyboard overlay before Save preset. The overall gate is FAILED; no complete v0.3.1 validation is claimed yet.

Follow-up uses Android's documented empty EOS sample with an explicit end timestamp to finalize the last frame's duration. See the [AOSP MediaMuxer contract](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/media/java/android/media/MediaMuxer.java). The workspace test explicitly clears focus/hides the keyboard before buttons. Batch auditing now requests pending rows explicitly on API 30+, making cancellation cleanup observable. Export files use the project title, and the final status includes real elapsed seconds. Runtime confirmation remains pending for this follow-up.
