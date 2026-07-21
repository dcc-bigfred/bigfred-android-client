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
 * Chart / gauge Y scale: at least [LatencySlo.BAD_MS] so SLO lines fit,
 * otherwise max sample, rounded up to 10 ms.
 */
fun latencyScaleMaxMs(values: List<Long>): Long {
    val maxMs = maxOf(LatencySlo.BAD_MS, values.maxOrNull() ?: LatencySlo.BAD_MS)
    return ((maxMs + 9) / 10) * 10
}
