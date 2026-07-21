package com.dccbigfred.android.network

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class CompanionServices(
    val osManagementUrl: String? = null,
    val monitoringUrl: String? = null,
) {
    val anyAvailable: Boolean get() = osManagementUrl != null || monitoringUrl != null
}

/**
 * Detects companion UIs on the BigFred host: OS management (:8090) and Monitoring (:3333).
 */
class CompanionServiceProbe(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun probe(serverBaseUrl: String): CompanionServices = coroutineScope {
        val osUrl = rewritePort(serverBaseUrl, OS_MANAGEMENT_PORT)
        val monUrl = rewritePort(serverBaseUrl, MONITORING_PORT)
        val os = async { if (isReachable(osUrl)) osUrl else null }
        val mon = async { if (isReachable(monUrl)) monUrl else null }
        CompanionServices(osManagementUrl = os.await(), monitoringUrl = mon.await())
    }

    private suspend fun isReachable(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/"
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Connection", "close")
                .build()
            client.newCall(request).execute().use { response ->
                // Any HTTP response means the port is open (auth pages often return 401/302).
                response.code in 200..499
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val OS_MANAGEMENT_PORT = 8090
        const val MONITORING_PORT = 3333

        fun rewritePort(baseUrl: String, port: Int): String {
            val uri = Uri.parse(baseUrl.trim())
            val host = uri.host ?: return baseUrl.trimEnd('/')
            val scheme = uri.scheme ?: "http"
            return "$scheme://$host:$port"
        }

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
    }
}
