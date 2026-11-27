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
                throw VeniceException(parseErrorBody(resp.code, text))
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

    /**
     * Image-to-image (edit): POST /image/edit
     * Model: seedream-v5-5-edit — varian edit dari Seedream 5.5 yang sama
     * dengan text2image (bukan firered lagi). Keunggulannya: multi-turn
     * editing yang menjaga bagian gambar yang tidak diedit tetap sama.
     * Gambar dikirim sebagai base64 (sudah dikompresi maks 1024px di client).
     * Beda dari /image/generate: responsnya GAMBAR MENTAH (binary),
     * bukan JSON base64 — jadi langsung baca bytes-nya.
     * Rasio & resolusi: endpoint /image/edit mencantumkan `aspect_ratio`
     * dan `resolution` sebagai parameter, dan keluarga Seedream mendukung
     * 8 rasio + tier 1K/1.5K/2K/4K — tapi dukungan per model belum
     * terkonfirmasi (firered-image-edit dulu langsung menolak `resolution`).
     * Kalau API menolak salah satunya, errornya nyebutin nama parameternya.
     */
    suspend fun editImage(
        apiKey: String,
        prompt: String,
        imageBase64: String,
        aspectRatio: String,
        resolution: String
    ): ByteArray = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw VeniceException("API key belum diisi. Buka menu hamburger > Pengaturan, lalu isi API key Venice kamu.")
        }
        if (prompt.isBlank()) {
            throw VeniceException("Prompt-nya kosong, isi dulu! Contoh: ganti langit jadi sunset.")
        }
        if (imageBase64.isBlank()) {
            throw VeniceException("Gambar referensi belum dipilih.")
        }

        val payload = JSONObject().apply {
            put("model", "seedream-v5-5-edit")
            put("image", imageBase64)
            put("prompt", prompt)
            put("aspect_ratio", aspectRatio)
            put("resolution", resolution)
            put("safe_mode", false)
        }

        val request = Request.Builder()
            .url("$BASE_URL/image/edit")
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
            if (!resp.isSuccessful) {
                throw VeniceException(parseError(resp))
            }
            resp.body?.bytes()
                ?.takeIf { it.isNotEmpty() }
                ?: throw VeniceException("Venice nggak ngembaliin gambar")
        }
    }

    /** Baca pesan error dari body JSON; kalau bukan JSON, pakai teks mentah. */
    private fun parseError(resp: okhttp3.Response): String {
        val text = try {
            resp.body?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }
        return parseErrorBody(resp.code, text)
    }

    private fun parseErrorBody(code: Int, text: String): String {
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
        if (code == 402) {
            err = "Saldo credits abis! Top up dulu di venice.ai/settings/api"
        }
        if (code == 401 || code == 403) {
            err = "API key ditolak Venice ($err). Cek lagi key-nya di Pengaturan."
        }
        if (code == 422) {
            err = "Venice nolak gambar/prompt ini ($err). Coba kecilin gambarnya atau ubah prompt."
        }
        return err.ifBlank { "Venice balas error $code" }
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
