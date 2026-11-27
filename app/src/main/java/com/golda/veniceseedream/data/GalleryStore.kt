package com.golda.veniceseedream.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class GalleryItem(
    val id: String,
    val prompt: String,
    val resolution: String,
    val aspectRatio: String,
    val fileName: String,
    val createdAt: Long,
    /** "text2image" atau "img2img" — item lama (sebelum ada field ini) dianggap text2image. */
    val mode: String = MODE_TEXT2IMAGE
) {
    fun file(dir: File): File = File(dir, fileName)

    val isImg2Img: Boolean get() = mode == MODE_IMG2IMG

    /** Label info di kartu galeri: "1K • 1:1" atau "Img2Img • 1K • 9:16". */
    val infoText: String
        get() = if (isImg2Img) {
            "Img2Img • $resolution" + if (aspectRatio.isNotBlank()) " • $aspectRatio" else ""
        } else {
            "$resolution • $aspectRatio"
        }

    companion object {
        const val MODE_TEXT2IMAGE = "text2image"
        const val MODE_IMG2IMG = "img2img"
    }
}

/**
 * Galeri lokal: file PNG disimpan di filesDir/gallery/,
 * metadata (prompt, resolusi, ratio, waktu) di index.json di folder yang sama.
 * Jadi galeri tetap ada setelah aplikasi ditutup, tanpa database.
 */
class GalleryStore(context: Context) {

    val galleryDir: File = File(context.filesDir, "gallery").apply { mkdirs() }
    private val indexFile: File = File(galleryDir, "index.json")

    suspend fun load(): List<GalleryItem> = withContext(Dispatchers.IO) {
        readIndex()
    }

    suspend fun add(
        prompt: String,
        resolution: String,
        aspectRatio: String,
        pngBytes: ByteArray,
        mode: String = GalleryItem.MODE_TEXT2IMAGE
    ): GalleryItem = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val prefix = if (mode == GalleryItem.MODE_IMG2IMG) "edit" else "seedream"
        val item = GalleryItem(
            id = now.toString(),
            prompt = prompt,
            resolution = resolution,
            aspectRatio = aspectRatio,
            fileName = "${prefix}_$now.png",
            createdAt = now,
            mode = mode
        )
        File(galleryDir, item.fileName).writeBytes(pngBytes)
        val items = listOf(item) + readIndex()
        writeIndex(items)
        item
    }

    suspend fun delete(item: GalleryItem) = withContext(Dispatchers.IO) {
        File(galleryDir, item.fileName).delete()
        writeIndex(readIndex().filter { it.id != item.id })
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        readIndex().forEach { File(galleryDir, it.fileName).delete() }
        writeIndex(emptyList())
    }

    private fun readIndex(): List<GalleryItem> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val fileName = o.optString("fileName")
                    if (fileName.isBlank()) continue
                    add(
                        GalleryItem(
                            id = o.optString("id"),
                            prompt = o.optString("prompt"),
                            resolution = o.optString("resolution"),
                            aspectRatio = o.optString("aspectRatio"),
                            fileName = fileName,
                            createdAt = o.optLong("createdAt"),
                            mode = o.optString("mode").ifBlank { GalleryItem.MODE_TEXT2IMAGE }
                        )
                    )
                }
            }.sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(items: List<GalleryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("prompt", item.prompt)
                    put("resolution", item.resolution)
                    put("aspectRatio", item.aspectRatio)
                    put("fileName", item.fileName)
                    put("createdAt", item.createdAt)
                    put("mode", item.mode)
                }
            )
        }
        indexFile.writeText(arr.toString())
    }
}
