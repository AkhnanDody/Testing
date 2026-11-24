package com.golda.veniceseedream.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simpan gambar ke folder Download/VeniceSeedream di penyimpanan HP.
 * Android 10+ (API 29+) pakai MediaStore, tidak perlu izin.
 * Android 9 ke bawah pakai file langsung (perlu WRITE_EXTERNAL_STORAGE).
 */
object Downloader {

    suspend fun saveToDownloads(context: Context, source: File, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!source.exists()) return@withContext false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                        put(MediaStore.Downloads.MIME_TYPE, "image/png")
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/VeniceSeedream"
                        )
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    ) ?: return@withContext false
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        source.inputStream().use { it.copyTo(out) }
                    } != null
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        ),
                        "VeniceSeedream"
                    ).apply { mkdirs() }
                    source.copyTo(File(dir, displayName), overwrite = true)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
}
