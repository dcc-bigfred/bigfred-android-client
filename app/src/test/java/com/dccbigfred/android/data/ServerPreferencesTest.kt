package com.dccbigfred.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerPreferencesTest {
    @Test
    fun defaultVolumeKeysThrottleEnabled_isTrue() {
        assertEquals(true, ServerPreferences.DEFAULT_VOLUME_KEYS_THROTTLE_ENABLED)
    }

    @Test
    fun normalizeBaseUrl_addsHttpAndStripsTrailingSlash() {
        assertEquals(
            "http://192.168.0.120:8080",
            ServerPreferences.normalizeBaseUrl("192.168.0.120:8080/"),
        )
    }

    @Test
    fun normalizeBaseUrl_keepsExistingScheme() {
        assertEquals(
            "http://bigfred.local:8080",
            ServerPreferences.normalizeBaseUrl("http://bigfred.local:8080"),
        )
    }
}
