package eu.example.constructionlapse

/** Bounded forward-window cache. Never evict a neighbour before it is consumed in this output frame. */
class ExportFrameWindow<T>(private val budgetBytes: Long, private val weight: (T) -> Long,
                           private val dispose: (T) -> Unit) : AutoCloseable {
    private val entries = linkedMapOf<Int, Pair<T, Long>>()
    private val consumed = mutableSetOf<Int>()
    var bytes = 0L; private set
    var peakBytes = 0L; private set
    var loads = 0; private set
    var hits = 0; private set
    init { require(budgetBytes >= 0) }
    fun retain(indices: IntRange) {
        consumed.clear()
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
        entries[index]?.let { hits++; consume(it.first); consumed.add(index); return }
        val value = load()
        loads++
        val size = try { weight(value) } catch (e: Throwable) { dispose(value); throw e }
        if (size > 0 && size <= budgetBytes) {
            while (size > budgetBytes - bytes) {
                // Earlier neighbours are already composited; retain newer frames for following outputs.
                val oldest = entries.entries.firstOrNull { it.key in consumed && it.key < index } ?: break
                entries.remove(oldest.key); consumed.remove(oldest.key)
                bytes -= oldest.value.second
                dispose(oldest.value.first)
            }
        }
        val cached = size > 0 && size <= budgetBytes - bytes
        if (cached) {
            entries[index] = value to size
            bytes += size
            peakBytes = maxOf(peakBytes, bytes)
        }
        try { consume(value); if (cached) consumed.add(index) } finally { if (!cached) dispose(value) }
    }
    override fun close() {
        entries.values.forEach { dispose(it.first) }
        entries.clear(); consumed.clear(); bytes = 0
    }
}
