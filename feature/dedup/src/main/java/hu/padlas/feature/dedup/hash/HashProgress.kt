package hu.padlas.feature.dedup.hash

/** The three passes of spec 2.4, in the order they run. */
enum class HashPhase { IDLE, QUICK, FULL, PERCEPTUAL, DONE }

/**
 * Live counts for the screen. The totals are exact because each phase reads its whole
 * queue up front, so the strip never stops at 99%.
 */
data class HashProgress(
    val phase: HashPhase = HashPhase.IDLE,
    val done: Int = 0,
    val total: Int = 0,
    val unreadable: Int = 0,
) {
    val running: Boolean get() = phase != HashPhase.IDLE && phase != HashPhase.DONE

    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}
