package com.dccbigfred.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ServerProbe(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun isReachable(baseUrl: String): Boolean =
        measureLatencyMs(baseUrl) != null

    /**
     * HTTP round-trip to `GET {baseUrl}/`. Returns latency in milliseconds, or null on failure.
     */
    suspend fun measureLatencyMs(baseUrl: String): Long? = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/"
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Connection", "close")
                .build()
            val started = System.nanoTime()
            client.newCall(request).execute().use { response ->
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                if (response.isSuccessful) elapsedMs else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
