package com.dccbigfred.android.network

import android.net.Uri
import android.webkit.CookieManager
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.data.localvehicles.LocalVehicleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BigFredApiClient(
    private val serverPreferences: ServerPreferences,
    private val client: OkHttpClient = defaultClient(),
) {
    sealed interface SyncResult {
        data object Success : SyncResult
        data object Unauthorized : SyncResult
        data class Failure(val code: String) : SyncResult
    }

    data class ServerVersion(
        val version: String,
        val tagCommit: String,
        val buildCommit: String,
        val buildTime: String,
    )

    sealed interface VersionResult {
        data class Success(val info: ServerVersion) : VersionResult
        data object NoServer : VersionResult
        data class Failure(val code: String) : VersionResult
    }

    /**
     * GET /api/v1/version — public, no session cookie required.
     * Reads the currently configured server base URL from preferences.
     */
    suspend fun getVersion(): VersionResult = withContext(Dispatchers.IO) {
        val baseUrl = serverPreferences.serverBaseUrl.first()?.trimEnd('/')
            ?: return@withContext VersionResult.NoServer

        try {
            val httpUrl = versionRequestUrl(baseUrl)
                ?: return@withContext VersionResult.Failure("invalid_server_url")
            val req = Request.Builder()
                .url(httpUrl)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext VersionResult.Failure("http_${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                VersionResult.Success(parseVersionBody(body))
            }
        } catch (_: Exception) {
            VersionResult.Failure("network_error")
        }
    }

    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = serverPreferences.serverBaseUrl.first() ?: return@withContext false
        sessionCookie(baseUrl) != null
    }

    suspend fun upsertVehicle(v: LocalVehicleEntity): SyncResult = withContext(Dispatchers.IO) {
        val baseUrl = serverPreferences.serverBaseUrl.first()
            ?: return@withContext SyncResult.Failure("no_server")
        val cookie = sessionCookie(baseUrl)
            ?: return@withContext SyncResult.Unauthorized

        val body = JSONObject().apply {
            put("name", v.name)
            put("kind", v.kind)
            put("number", v.number)
            put("dccAddress", v.dccAddress ?: JSONObject.NULL)
            put("carrier", v.carrier)
            put("assignment", v.assignment)
            put("revisionDate", v.revisionDate ?: JSONObject.NULL)
            put("epoch", v.epoch)
            // iconPath is intentionally omitted — local-only.
        }.toString().toRequestBody(JSON_MEDIA)

        val req = Request.Builder()
            .url("$baseUrl/api/v1/vehicles/by-external-id/${v.uuid}")
            .header("Cookie", cookie)
            .put(body)
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                when {
                    resp.isSuccessful -> SyncResult.Success
                    resp.code == 401 -> SyncResult.Unauthorized
                    else -> SyncResult.Failure(parseErrorCode(resp.body?.string(), resp.code))
                }
            }
        } catch (_: Exception) {
            SyncResult.Failure("network_error")
        }
    }

    /**
     * POST /api/v1/auth/logout (best-effort), apply any Set-Cookie from the response
     * into the WebView jar, then expire `bigfred_session` locally.
     * Idempotent when already logged out or offline.
     */
    suspend fun logout(baseUrl: String? = null) = withContext(Dispatchers.IO) {
        val url = (baseUrl ?: serverPreferences.serverBaseUrl.first())
            ?.trimEnd('/')
            ?: return@withContext

        val cm = CookieManager.getInstance()
        val cookie = sessionCookie(url)
        if (cookie != null) {
            val req = Request.Builder()
                .url("$url/api/v1/auth/logout")
                .header("Cookie", cookie)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    // OkHttp does not feed CookieManager — mirror Set-Cookie into WebView.
                    for (i in 0 until resp.headers.size) {
                        if (resp.headers.name(i).equals("Set-Cookie", ignoreCase = true)) {
                            cm.setCookie(url, resp.headers.value(i))
                        }
                    }
                }
            } catch (_: Exception) {
                // Still clear local cookies below.
            }
        }
        clearSessionCookie(url)
    }

    private fun sessionCookie(baseUrl: String): String? {
        return CookieManager.getInstance().getCookie(baseUrl)
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("bigfred_session=") }
    }

    private fun clearSessionCookie(baseUrl: String) {
        val cm = CookieManager.getInstance()
        val uri = Uri.parse(baseUrl)
        val host = uri.host ?: return
        val scheme = uri.scheme ?: "http"
        val secure = scheme.equals("https", ignoreCase = true)
        val secureAttr = if (secure) "; Secure" else ""
        val expires = "bigfred_session=; Max-Age=0; Path=/$secureAttr"
        val expiresWithDomain = "bigfred_session=; Max-Age=0; Path=/; Domain=$host$secureAttr"
        // Cover the URL forms WebView commonly stores for this origin.
        cm.setCookie(baseUrl, expires)
        cm.setCookie("$baseUrl/", expires)
        cm.setCookie("$scheme://$host", expires)
        cm.setCookie("$scheme://$host/", expires)
        cm.setCookie(baseUrl, expiresWithDomain)
        cm.setCookie("$scheme://$host/", expiresWithDomain)
        cm.flush()
    }

    private fun parseErrorCode(body: String?, httpCode: Int): String {
        if (body.isNullOrBlank()) return "http_$httpCode"
        return try {
            JSONObject(body).optString("error").takeIf { it.isNotBlank() } ?: "http_$httpCode"
        } catch (_: Exception) {
            "http_$httpCode"
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        fun versionRequestUrl(baseUrl: String): okhttp3.HttpUrl? {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            return "$trimmed/api/v1/version".toHttpUrlOrNull()
        }

        fun parseVersionBody(body: String): ServerVersion {
            val json = JSONObject(body)
            return ServerVersion(
                version = json.optString("version"),
                tagCommit = json.optString("tagCommit"),
                buildCommit = json.optString("buildCommit"),
                buildTime = json.optString("buildTime"),
            )
        }
    }
}
