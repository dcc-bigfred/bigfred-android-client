package com.dccbigfred.android.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BigFredApiClientVersionTest {
    @Test
    fun versionRequestUrl_buildsFromBase() {
        val url = BigFredApiClient.versionRequestUrl("http://192.168.0.10:8080")
        assertNotNull(url)
        assertEquals("http://192.168.0.10:8080/api/v1/version", url.toString())
    }

    @Test
    fun versionRequestUrl_rejectsMalformed() {
        assertNull(BigFredApiClient.versionRequestUrl("http://[bad"))
        assertNull(BigFredApiClient.versionRequestUrl(""))
        assertNull(BigFredApiClient.versionRequestUrl("   "))
    }

    @Test
    fun parseVersionBody_readsFields() {
        val info = BigFredApiClient.parseVersionBody(
            """{"version":"v1.2.3","tagCommit":"abc1234","buildCommit":"def5678","buildTime":"2026-07-30T08:00:00Z"}""",
        )
        assertEquals("v1.2.3", info.version)
        assertEquals("abc1234", info.tagCommit)
        assertEquals("def5678", info.buildCommit)
        assertEquals("2026-07-30T08:00:00Z", info.buildTime)
    }

    @Test
    fun parseVersionBody_missingFieldsAreEmpty() {
        val info = BigFredApiClient.parseVersionBody("""{"version":"dev"}""")
        assertEquals("dev", info.version)
        assertEquals("", info.tagCommit)
        assertEquals("", info.buildCommit)
        assertEquals("", info.buildTime)
    }
}
