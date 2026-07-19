package com.dccbigfred.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ServerProbe(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun isReachable(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/"
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Connection", "close")
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
