package eu.example.constructionlapse

/** Bounded forward-window cache. A miss never evicts a still-useful frame just to cache another miss. */
class ExportFrameWindow<T>(private val budgetBytes: Long, private val weight: (T) -> Long,
                           private val dispose: (T) -> Unit) : AutoCloseable {
    private val entries = linkedMapOf<Int, Pair<T, Long>>()
    var bytes = 0L; private set
    var peakBytes = 0L; private set
    var loads = 0; private set
    var hits = 0; private set
    init { require(budgetBytes >= 0) }
    fun retain(indices: IntRange) {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in indices) {
                bytes -= entry.value.second
                dispose(entry.value.first)
                iterator.remove()
            }
        }
    }
    fun use(index: Int, load: () -> T, consume: (T) -> Unit) {
        entries[index]?.let { hits++; consume(it.first); return }
        val value = load()
        loads++
        val size = try { weight(value) } catch (e: Throwable) { dispose(value); throw e }
        val cached = size >= 0 && size <= budgetBytes - bytes
        if (cached) {
            entries[index] = value to size
            bytes += size
            peakBytes = maxOf(peakBytes, bytes)
        }
        try { consume(value) } finally { if (!cached) dispose(value) }
    }
    override fun close() {
        entries.values.forEach { dispose(it.first) }
        entries.clear(); bytes = 0
    }
}
