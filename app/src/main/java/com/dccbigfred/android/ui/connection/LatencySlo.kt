package com.dccbigfred.android.ui.connection

import androidx.compose.ui.graphics.Color

/**
 * Latency SLO bands for connection status.
 *
 * - green: &lt; 50 ms
 * - yellow: 50–199 ms
 * - orange: 200–299 ms
 * - red: ≥ 300 ms
 */
object LatencySlo {
    const val GOOD_MS = 50L
    const val WARN_MS = 200L
    const val BAD_MS = 300L

    /** Default chart / gauge Y maximum (0 … [DEFAULT_SCALE_MAX_MS] ms). */
    const val DEFAULT_SCALE_MAX_MS = BAD_MS

    /** Horizontal reference lines drawn on the latency chart. */
    val thresholdLinesMs: List<Long> = listOf(GOOD_MS, WARN_MS, BAD_MS)

    val colorGood = Color(0xFF2E7D32)
    val colorWarn = Color(0xFFF9A825)
    val colorDegraded = Color(0xFFEF6C00)
    val colorBad = Color(0xFFC62828)

    enum class Band {
        GOOD,
        WARN,
        DEGRADED,
        BAD,
    }

    fun band(ms: Long): Band = when {
        ms < GOOD_MS -> Band.GOOD
        ms < WARN_MS -> Band.WARN
        ms < BAD_MS -> Band.DEGRADED
        else -> Band.BAD
    }

    fun colorFor(ms: Long): Color = when (band(ms)) {
        Band.GOOD -> colorGood
        Band.WARN -> colorWarn
        Band.DEGRADED -> colorDegraded
        Band.BAD -> colorBad
    }

    /** Color of the threshold line that marks the start of the worse band. */
    fun colorForThreshold(thresholdMs: Long): Color = when (thresholdMs) {
        GOOD_MS -> colorWarn
        WARN_MS -> colorDegraded
        BAD_MS -> colorBad
        else -> colorWarn
    }
}
