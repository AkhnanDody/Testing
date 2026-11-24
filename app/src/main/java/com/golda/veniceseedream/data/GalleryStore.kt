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
    val createdAt: Long
) {
    fun file(dir: File): File = File(dir, fileName)
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
        pngBytes: ByteArray
    ): GalleryItem = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val item = GalleryItem(
            id = now.toString(),
            prompt = prompt,
            resolution = resolution,
            aspectRatio = aspectRatio,
            fileName = "seedream_$now.png",
            createdAt = now
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
                            createdAt = o.optLong("createdAt")
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
                }
            )
        }
        indexFile.writeText(arr.toString())
    }
}
