package com.golda.veniceseedream.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Kompresi gambar referensi di sisi client sebelum dikirim ke Venice:
 * sisi terpanjang dikecilkan ke maksimal 1024px (kalau sudah lebih kecil,
 * dibiarkan apa adanya), lalu disimpan sebagai JPEG kualitas 85.
 * Hasilnya jauh lebih ringan buat di-upload, dan tetap cukup detail
 * buat model edit (firered-image-edit).
 */
object ImageCompressor {

    const val MAX_SIDE_PX = 1024

    data class CompressedImage(
        /** Bytes JPEG hasil kompresi — dipakai buat preview di layar. */
        val jpegBytes: ByteArray,
        /** Base64 dari jpegBytes — yang dikirim ke API. */
        val base64: String,
        val width: Int,
        val height: Int
    ) {
        /** Ukuran file hasil kompresi, misal "123 KB". */
        val sizeText: String
            get() = if (jpegBytes.size >= 1024 * 1024) {
                "%.1f MB".format(jpegBytes.size / 1024f / 1024f)
            } else {
                "%d KB".format(kotlin.math.max(1, jpegBytes.size / 1024))
            }

        val dimensionsText: String get() = "${width}×${height}"
    }

    class CompressException(message: String) : Exception(message)

    suspend fun compress(context: Context, uri: Uri): CompressedImage =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            // 1. Baca ukuran asli dulu tanpa load bitmap penuh (hemat memori).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
            } catch (e: Exception) {
                throw CompressException("Gagal baca gambar: ${e.message}")
            }
            val origW = bounds.outWidth
            val origH = bounds.outHeight
            if (origW <= 0 || origH <= 0) {
                throw CompressException("File ini bukan gambar yang bisa dibaca.")
            }

            // 2. Hitung skala biar sisi terpanjang jadi <= 1024px.
            val longest = maxOf(origW, origH)
            val sampleSize = if (longest <= MAX_SIDE_PX) {
                1
            } else {
                var s = 1
                while (longest / s > MAX_SIDE_PX) s *= 2
                s
            }

            // 3. Decode (dengan sampleSize) lalu skala tepat ke ukuran target.
            val targetW: Int
            val targetH: Int
            if (longest <= MAX_SIDE_PX) {
                targetW = origW
                targetH = origH
            } else {
                val scale = MAX_SIDE_PX.toFloat() / longest
                targetW = (origW * scale).toInt().coerceAtLeast(1)
                targetH = (origH * scale).toInt().coerceAtLeast(1)
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = try {
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) {
                throw CompressException("Gagal baca gambar: ${e.message}")
            } ?: throw CompressException("File ini bukan gambar yang bisa dibaca.")

            val bitmap = if (decoded.width != targetW || decoded.height != targetH) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            // 4. Kompres ke JPEG kualitas 85.
            val out = ByteArrayOutputStream()
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                    throw CompressException("Gagal mengompres gambar.")
                }
            } finally {
                bitmap.recycle()
            }
            val bytes = out.toByteArray()

            CompressedImage(
                jpegBytes = bytes,
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                width = targetW,
                height = targetH
            )
        }
}
