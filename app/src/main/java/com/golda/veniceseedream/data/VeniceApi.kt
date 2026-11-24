package com.golda.veniceseedream.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Port langsung dari server.py:
 * POST https://api.venice.ai/api/v1/image/generate
 * Header: Authorization: Bearer <apiKey>
 * Body: model, prompt, resolution, aspect_ratio, format, safe_mode
 */
object VeniceApi {

    private const val BASE_URL = "https://api.venice.ai/api/v1"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    class VeniceException(message: String) : Exception(message)

    suspend fun generate(
        apiKey: String,
        prompt: String,
        resolution: String,
        aspectRatio: String
    ): ByteArray = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw VeniceException("API key belum diisi. Buka menu hamburger > Pengaturan, lalu isi API key Venice kamu.")
        }
        if (prompt.isBlank()) {
            throw VeniceException("Prompt-nya kosong, isi dulu!")
        }

        val payload = JSONObject().apply {
            put("model", "seedream-v5-5")
            put("prompt", prompt)
            put("resolution", resolution)
            put("aspect_ratio", aspectRatio)
            put("format", "png")
            put("safe_mode", false)
        }

        val request = Request.Builder()
            .url("$BASE_URL/image/generate")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw VeniceException("Gagal nyambung ke Venice: ${e.message}")
        }

        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                var err = text
                try {
                    val json = JSONObject(text)
                    err = json.optString("error", text)
                    // Kadang error berupa objek
                    if (err.isBlank() || err == text) {
                        val errObj = json.optJSONObject("error")
                        if (errObj != null) err = errObj.optString("message", text)
                    }
                } catch (_: Exception) { /* pakai teks mentah */ }
                if (resp.code == 402) {
                    err = "Saldo credits abis! Top up dulu di venice.ai/settings/api"
                }
                if (resp.code == 401 || resp.code == 403) {
                    err = "API key ditolak Venice ($err). Cek lagi key-nya di Pengaturan."
                }
                throw VeniceException(err.ifBlank { "Venice balas error ${resp.code}" })
            }

            val data = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw VeniceException("Response Venice nggak bisa dibaca: ${e.message}")
            }

            val b64 = extractBase64(data)
                ?: throw VeniceException("Gambar nggak ketemu di response Venice")

            try {
                Base64.decode(b64, Base64.DEFAULT)
            } catch (e: Exception) {
                throw VeniceException("Data gambar dari Venice rusak: ${e.message}")
            }
        }
    }

    /** Sama kayak server.py: utamakan "images"[0], fallback "data"[0].b64_json */
    private fun extractBase64(data: JSONObject): String? {
        val images: JSONArray? = data.optJSONArray("images")
        if (images != null && images.length() > 0) {
            var b64 = images.optString(0)
            if (b64.contains(",")) b64 = b64.substringAfter(",", b64)
            if (b64.isNotBlank()) return b64.trim()
        }
        val arr: JSONArray? = data.optJSONArray("data")
        if (arr != null && arr.length() > 0) {
            val b64 = arr.optJSONObject(0)?.optString("b64_json").orEmpty()
            if (b64.isNotBlank()) return b64.trim()
        }
        return null
    }
}
