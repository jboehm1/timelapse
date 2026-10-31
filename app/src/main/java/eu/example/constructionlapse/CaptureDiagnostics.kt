package eu.example.constructionlapse

import android.content.Context
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Diagnostics never block camera/UI threads and cannot build an unbounded queue. */
object CaptureDiagnostics {
    private val dropped = AtomicLong()
    private val executor = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(64), { task -> Thread(task, "capture-diagnostics").apply { isDaemon = true } },
        { _, _ -> dropped.incrementAndGet() })
    @Volatile private var log: CaptureEventLog? = null

    @Synchronized fun initialize(context: Context) {
        if (log == null) runCatching {
            log = CaptureEventLog(File(context.applicationContext.filesDir, "diagnostics"))
        }.onFailure { dropped.incrementAndGet() }
    }

    fun record(event: String) {
        executor.execute { runCatching { log?.append(event) }.onFailure { dropped.incrementAndGet() } }
    }

    /** Called on an export worker, ordered behind all events accepted before export. */
    fun snapshot(): String = executor.submit<String> {
        "Timelapse diagnostic build — UNVERIFIED on S21\nDropped diagnostic events: ${dropped.get()}\n" +
            (log?.snapshot() ?: "No capture events yet.")
    }.get(5, TimeUnit.SECONDS)
}
