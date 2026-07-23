package com.dccbigfred.android.ui.connection

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    data class Palette(
        val good: Color,
        val warn: Color,
        val degraded: Color,
        val bad: Color,
    ) {
        fun colorFor(ms: Long): Color = when (LatencySlo.band(ms)) {
            Band.GOOD -> good
            Band.WARN -> warn
            Band.DEGRADED -> degraded
            Band.BAD -> bad
        }

        /** Color of the threshold line that marks the start of the worse band. */
        fun colorForThreshold(thresholdMs: Long): Color = when (thresholdMs) {
            GOOD_MS -> warn
            WARN_MS -> degraded
            BAD_MS -> bad
            else -> warn
        }
    }
}

@Composable
fun rememberLatencySloPalette(): LatencySlo.Palette {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (dark) {
            LatencySlo.Palette(
                good = Color(0xFF81C784),
                warn = Color(0xFFFFD54F),
                degraded = Color(0xFFFFB74D),
                bad = Color(0xFFE57373),
            )
        } else {
            LatencySlo.Palette(
                good = Color(0xFF2E7D32),
                warn = Color(0xFFF9A825),
                degraded = Color(0xFFEF6C00),
                bad = Color(0xFFC62828),
            )
        }
    }
}
