package com.dccbigfred.android.ui.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class LatencySloTest {
    @Test
    fun band_boundaries() {
        assertEquals(LatencySlo.Band.GOOD, LatencySlo.band(0))
        assertEquals(LatencySlo.Band.GOOD, LatencySlo.band(49))
        assertEquals(LatencySlo.Band.WARN, LatencySlo.band(50))
        assertEquals(LatencySlo.Band.WARN, LatencySlo.band(199))
        assertEquals(LatencySlo.Band.DEGRADED, LatencySlo.band(200))
        assertEquals(LatencySlo.Band.DEGRADED, LatencySlo.band(299))
        assertEquals(LatencySlo.Band.BAD, LatencySlo.band(300))
        assertEquals(LatencySlo.Band.BAD, LatencySlo.band(999))
    }
}
