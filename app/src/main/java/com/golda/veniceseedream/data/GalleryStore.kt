package com.golda.veniceseedream.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class GalleryItem(
    val id: String,
    val prompt: String,
    val resolution: String,
    val aspectRatio: String,
    val fileName: String,
    val createdAt: Long,
    /** "text2image" atau "img2img" — item lama (sebelum ada field ini) dianggap text2image. */
    val mode: String = MODE_TEXT2IMAGE,
    /** "pending" (lagi dibuat), "done" (gambarnya ada), atau "failed" (gagal, [error] berisi sebabnya).
     *  Item lama (sebelum ada field ini) dianggap "done". */
    val status: String = STATUS_DONE,
    /** Pesan kegagalan untuk item "failed" — ditampilkan di Galeri, bukan di Beranda. */
    val error: String = ""
) {
    fun file(dir: File): File = File(dir, fileName)

    val isImg2Img: Boolean get() = mode == MODE_IMG2IMG
    val isPending: Boolean get() = status == STATUS_PENDING
    val isFailed: Boolean get() = status == STATUS_FAILED
    val isDone: Boolean get() = status == STATUS_DONE

    /** Label info di kartu galeri: "1K • 1:1" atau "Img2Img • 1K • 9:16". */
    val infoText: String
        get() = when {
            isPending -> "Sedang dibuat…"
            isFailed -> "Gagal dibuat"
            isImg2Img -> "Img2Img • $resolution" + if (aspectRatio.isNotBlank()) " • $aspectRatio" else ""
            else -> "$resolution • $aspectRatio"
        }

    companion object {
        const val MODE_TEXT2IMAGE = "text2image"
        const val MODE_IMG2IMG = "img2img"
        const val STATUS_PENDING = "pending"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Galeri lokal: file PNG disimpan di filesDir/gallery/,
 * metadata (prompt, resolusi, ratio, waktu, status) di index.json di folder yang sama.
 * Jadi galeri tetap ada setelah aplikasi ditutup, tanpa database.
 *
 * Filosofi "tekan Generate, lalu lupakan": generate langsung membuat item
 * berstatus pending di galeri, prosesnya jalan sendiri, lalu item itu berubah
 * jadi done (ada gambarnya) atau failed (ada pesan errornya) — semua di Galeri.
 * Beberapa generate boleh jalan barengan, jadi semua tulis-index dikunci [mutex].
 */
class GalleryStore(context: Context) {

    val galleryDir: File = File(context.filesDir, "gallery").apply { mkdirs() }
    private val indexFile: File = File(galleryDir, "index.json")
    private val mutex = Mutex()

    suspend fun load(): List<GalleryItem> = withContext(Dispatchers.IO) {
        mutex.withLock { readIndex() }
    }

    /** Buat item pending di galeri — dipanggil begitu tombol Generate ditekan. */
    suspend fun addPending(
        prompt: String,
        resolution: String,
        aspectRatio: String,
        mode: String = GalleryItem.MODE_TEXT2IMAGE
    ): GalleryItem = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val item = GalleryItem(
                id = UUID.randomUUID().toString(),
                prompt = prompt,
                resolution = resolution,
                aspectRatio = aspectRatio,
                fileName = "",
                createdAt = now,
                mode = mode,
                status = GalleryItem.STATUS_PENDING
            )
            writeIndex(listOf(item) + readIndex())
            item
        }
    }

    /**
     * Ubah item pending jadi done dan simpan gambarnya.
     * Mengembalikan null kalau itemnya sudah dihapus user selagi proses jalan —
     * gambarnya ikut dibuang, tidak dimunculkan lagi.
     */
    suspend fun completePending(item: GalleryItem, pngBytes: ByteArray): GalleryItem? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val items = readIndex()
                if (items.none { it.id == item.id }) return@withLock null
                val now = System.currentTimeMillis()
                val prefix = if (item.isImg2Img) "edit" else "seedream"
                val done = item.copy(
                    status = GalleryItem.STATUS_DONE,
                    fileName = "${prefix}_$now.png",
                    error = ""
                )
                File(galleryDir, done.fileName).writeBytes(pngBytes)
                writeIndex(items.map { if (it.id == item.id) done else it })
                done
            }
        }

    /** Ubah item pending jadi failed, lengkap dengan pesan errornya untuk Galeri. */
    suspend fun failPending(item: GalleryItem, errorMessage: String): GalleryItem =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val failed = item.copy(
                    status = GalleryItem.STATUS_FAILED,
                    error = errorMessage
                )
                writeIndex(readIndex().map { if (it.id == item.id) failed else it })
                failed
            }
        }

    suspend fun add(
        prompt: String,
        resolution: String,
        aspectRatio: String,
        pngBytes: ByteArray,
        mode: String = GalleryItem.MODE_TEXT2IMAGE
    ): GalleryItem = withContext(Dispatchers.IO) {
        mutex.withLock {
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
            writeIndex(listOf(item) + readIndex())
            item
        }
    }

    /**
     * Di awal aplikasi dibuka, item pending peninggalan sesi sebelumnya tidak
     * mungkin selesai (prosesnya sudah mati) — ubah jadi failed biar tidak
     * menggantung "Sedang dibuat…" selamanya. Hanya dipanggil sekali di init
     * ViewModel, tidak pernah selama sesi berjalan.
     */
    suspend fun failStalePending() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val items = readIndex()
            if (items.any { it.isPending }) {
                writeIndex(items.map {
                    if (it.isPending) {
                        it.copy(
                            status = GalleryItem.STATUS_FAILED,
                            error = "Prosesnya terputus karena aplikasi ditutup sebelum selesai."
                        )
                    } else it
                })
            }
        }
    }

    suspend fun delete(item: GalleryItem) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (item.fileName.isNotBlank()) File(galleryDir, item.fileName).delete()
            writeIndex(readIndex().filter { it.id != item.id })
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            readIndex().forEach {
                if (it.fileName.isNotBlank()) File(galleryDir, it.fileName).delete()
            }
            writeIndex(emptyList())
        }
    }

    private fun readIndex(): List<GalleryItem> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    add(
                        GalleryItem(
                            id = id,
                            prompt = o.optString("prompt"),
                            resolution = o.optString("resolution"),
                            aspectRatio = o.optString("aspectRatio"),
                            fileName = o.optString("fileName"),
                            createdAt = o.optLong("createdAt"),
                            mode = o.optString("mode").ifBlank { GalleryItem.MODE_TEXT2IMAGE },
                            status = o.optString("status").ifBlank { GalleryItem.STATUS_DONE },
                            error = o.optString("error")
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
                    put("status", item.status)
                    put("error", item.error)
                }
            )
        }
        indexFile.writeText(arr.toString())
    }
}
