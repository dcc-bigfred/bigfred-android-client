package com.dccbigfred.android.ui.webview

import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleHardwareKeysScriptTest {
    @Test
    fun throttleHardwareKeysJavascript_includesDirectionAndGlobalHandler() {
        val up = throttleHardwareKeysJavascript(1)
        assertTrue(up.contains("__bigfredThrottleHardwareKeys"))
        assertTrue(up.contains("(1);"))

        val down = throttleHardwareKeysJavascript(-1)
        assertTrue(down.contains("(-1);"))
    }
}
