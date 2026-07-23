package com.dccbigfred.android.ui.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatencySummaryTest {
    @Test
    fun from_empty_returnsNull() {
        assertNull(LatencySummary.from(emptyList()))
    }

    @Test
    fun from_singleValue_allEqual() {
        val summary = LatencySummary.from(listOf(42L))!!
        assertEquals(42L, summary.minMs)
        assertEquals(42L, summary.p50Ms)
        assertEquals(42L, summary.p90Ms)
        assertEquals(42L, summary.p99Ms)
    }

    @Test
    fun from_knownSet_computesMinAndPercentiles() {
        // 1..100 → min=1, p50→51, p90→90, p99→99 (linear rank)
        val values = (1L..100L).toList()
        val summary = LatencySummary.from(values)!!
        assertEquals(1L, summary.minMs)
        assertEquals(51L, summary.p50Ms)
        assertEquals(90L, summary.p90Ms)
        assertEquals(99L, summary.p99Ms)
    }

    @Test
    fun latencyScaleMaxMs_defaultIs300_growsThenShrinksBack() {
        assertEquals(300L, latencyScaleMaxMs(emptyList()))
        assertEquals(300L, latencyScaleMaxMs(listOf(12L, 100L, 299L)))
        assertEquals(310L, latencyScaleMaxMs(listOf(12L, 301L)))
        assertEquals(400L, latencyScaleMaxMs(listOf(391L, 400L)))
        // Once the outlier leaves the window, scale returns to default.
        assertEquals(300L, latencyScaleMaxMs(listOf(12L, 100L)))
    }
}
