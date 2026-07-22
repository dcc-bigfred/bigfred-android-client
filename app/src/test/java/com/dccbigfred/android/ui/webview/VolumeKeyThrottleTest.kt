package com.dccbigfred.android.ui.webview

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyThrottleTest {
    @Test
    fun handleVolumeKey_mapsUpAndDownOnActionDown() {
        val directions = mutableListOf<Int>()

        assertTrue(
            handleVolumeKey(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN) {
                directions.add(it)
            },
        )
        assertEquals(listOf(1), directions)

        assertTrue(
            handleVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN) {
                directions.add(it)
            },
        )
        assertEquals(listOf(1, -1), directions)
    }

    @Test
    fun handleVolumeKey_consumesActionUpWithoutStep() {
        val directions = mutableListOf<Int>()

        assertTrue(
            handleVolumeKey(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP) {
                directions.add(it)
            },
        )
        assertTrue(directions.isEmpty())
    }

    @Test
    fun handleVolumeKey_ignoresOtherKeys() {
        val directions = mutableListOf<Int>()

        assertFalse(
            handleVolumeKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN) {
                directions.add(it)
            },
        )
        assertTrue(directions.isEmpty())
    }
}
