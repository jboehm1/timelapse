package eu.example.constructionlapse

/** Monotonic-clock hysteresis; severe heat pauses immediately, recovery needs sustained NONE/LIGHT. */
class ThermalRecoveryGate(private val recoveryMs: Long = 180_000) {
    init { require(recoveryMs > 0) }
    private var cooling = false
    private var lowSince: Long? = null
    fun paused(nowMs: Long, thermalStatus: Int): Boolean {
        require(nowMs >= 0 && thermalStatus in 0..6)
        if (thermalStatus >= 3) { cooling = true; lowSince = null; return true }
        if (!cooling) return false
        if (thermalStatus > 1) { lowSince = null; return true }
        val start = lowSince
        if (start == null || nowMs < start) lowSince = nowMs
        else if (nowMs - start >= recoveryMs) { cooling = false; lowSince = null }
        return cooling
    }
}
