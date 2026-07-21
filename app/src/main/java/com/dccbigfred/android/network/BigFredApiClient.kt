package com.dccbigfred.android.network

import android.webkit.CookieManager
import com.dccbigfred.android.data.ServerPreferences
import com.dccbigfred.android.data.localvehicles.LocalVehicleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BigFredApiClient(
    private val serverPreferences: ServerPreferences,
    private val client: OkHttpClient = OkHttpClient(),
) {
    sealed interface SyncResult {
        data object Success : SyncResult
        data object Unauthorized : SyncResult
        data class Failure(val code: String) : SyncResult
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

    private fun sessionCookie(baseUrl: String): String? {
        return CookieManager.getInstance().getCookie(baseUrl)
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("bigfred_session=") }
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
    }
}
