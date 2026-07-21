package com.dccbigfred.android.ui.connection

import kotlin.math.roundToLong

data class LatencySummary(
    val minMs: Long,
    val p50Ms: Long,
    val p99Ms: Long,
) {
    companion object {
        fun from(values: List<Long>): LatencySummary? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            return LatencySummary(
                minMs = sorted.first(),
                p50Ms = percentile(sorted, 0.50),
                p99Ms = percentile(sorted, 0.99),
            )
        }
    }
}

/** Linear interpolation between ranks on a non-empty ascending list. */
internal fun percentile(sortedAscending: List<Long>, p: Double): Long {
    require(sortedAscending.isNotEmpty()) { "sortedAscending must not be empty" }
    require(p in 0.0..1.0) { "p must be in [0, 1]" }
    if (sortedAscending.size == 1) return sortedAscending[0]
    val rank = p * (sortedAscending.size - 1)
    val lo = rank.toInt()
    val hi = (lo + 1).coerceAtMost(sortedAscending.lastIndex)
    val frac = rank - lo
    return (sortedAscending[lo] * (1.0 - frac) + sortedAscending[hi] * frac).roundToLong()
}

/**
 * Y-axis maximum for the latency chart and gauges.
 *
 * Default is [LatencySlo.DEFAULT_SCALE_MAX_MS] (0–300 ms). Grows when any
 * sample exceeds that; shrinks back to the default once those samples leave
 * the window. Rounded up to the next 10 ms when expanded.
 */
fun latencyScaleMaxMs(values: List<Long>): Long {
    val peak = values.maxOrNull() ?: 0L
    val maxMs = maxOf(LatencySlo.DEFAULT_SCALE_MAX_MS, peak)
    return if (maxMs <= LatencySlo.DEFAULT_SCALE_MAX_MS) {
        LatencySlo.DEFAULT_SCALE_MAX_MS
    } else {
        ((maxMs + 9) / 10) * 10
    }
}
