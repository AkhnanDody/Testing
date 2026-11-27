package com.golda.veniceseedream.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golda.veniceseedream.data.GalleryItem
import com.golda.veniceseedream.data.GalleryStore
import com.golda.veniceseedream.data.ImageCompressor
import com.golda.veniceseedream.data.SettingsStore
import com.golda.veniceseedream.data.VeniceApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GenerateMode { TEXT2IMAGE, IMG2IMG }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val settingsStore = SettingsStore(application)
    private val galleryStore = GalleryStore(application)

    val galleryDir get() = galleryStore.galleryDir

    val apiKey: StateFlow<String> = settingsStore.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    var mode by mutableStateOf(GenerateMode.TEXT2IMAGE)

    var prompt by mutableStateOf("")
    var resolution by mutableStateOf(Options.RESOLUTIONS.first())
    var aspectRatio by mutableStateOf(Options.ASPECT_RATIOS.first())
    /** Rasio & resolusi khusus img2img — dikirim ke /image/edit. */
    var editResolution by mutableStateOf(Options.EDIT_RESOLUTIONS.first())
    var editAspectRatio by mutableStateOf(Options.ASPECT_RATIOS.first())

    /** Gambar referensi img2img, sudah dikompresi (maks 1024px) di client. */
    var referenceImage by mutableStateOf<ImageCompressor.CompressedImage?>(null)
        private set
    var isCompressing by mutableStateOf(false)
        private set

    /** Error seputar input di Beranda (mis. gambar referensi gagal diproses).
     *  Error generate TIDAK di sini — sesuai filosofi aplikasi, hasil dan
     *  kegagalan generate muncul sebagai item di Galeri. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** True sesaat setelah Generate ditekan — HomeScreen langsung pindah ke Galeri. */
    var navigateToGallery by mutableStateOf(false)
        private set

    var galleryItems by mutableStateOf<List<GalleryItem>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            galleryStore.failStalePending()
            galleryItems = galleryStore.load()
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { settingsStore.saveApiKey(key) }
    }

    fun clearApiKey() {
        viewModelScope.launch { settingsStore.clearApiKey() }
    }

    fun usePrompt(text: String) {
        prompt = text
    }

    /** Ganti mode Text2Image/Img2Img. Namanya selectMode (bukan setMode) karena
     *  properti `mode` di atas sudah otomatis punya setter setMode() dari Kotlin —
     *  kalau namanya sama, compiler protes "platform declaration clash". */
    fun selectMode(newMode: GenerateMode) {
        mode = newMode
        errorMessage = null
    }

    /** Pilih gambar referensi dari galeri HP, lalu kompres otomatis (maks 1024px). */
    fun setReferenceImage(uri: Uri) {
        isCompressing = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val compressed = ImageCompressor.compress(app, uri)
                referenceImage = compressed
                // Rasio default ngikutin gambar referensi — kalau mau diubah,
                // tinggal pilih di dropdown Aspect Ratio.
                editAspectRatio = Options.closestAspectRatio(compressed.width, compressed.height)
            } catch (e: ImageCompressor.CompressException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = "Gagal proses gambar: ${e.message}"
            } finally {
                isCompressing = false
            }
        }
    }

    fun clearReferenceImage() {
        referenceImage = null
    }

    fun refreshGallery() {
        viewModelScope.launch {
            val loaded = galleryStore.load()
            // Item pending yang lagi jalan di sesi ini jangan sampai tertimpa
            // hasil load yang lebih lama urutannya — gabungkan berdasarkan id,
            // utamakan state yang sudah ada di memori.
            val inMemory = galleryItems.associateBy { it.id }
            galleryItems = loaded.map { inMemory[it.id] ?: it }
                .sortedByDescending { it.createdAt }
        }
    }

    fun consumeNavigateToGallery() {
        navigateToGallery = false
    }

    /**
     * Filosofi aplikasi: tekan Generate, lalu lupakan.
     *
     * Begitu tombol ditekan: prompt disalin, kotak prompt dikosongkan, item
     * "pending" langsung dibuat di Galeri, dan pengguna langsung dipindah ke
     * Galeri. Panggilan ke Venice jalan sendiri di latar — berhasil jadi
     * gambar, gagal jadi item "failed" berisi pesan errornya, dua-duanya di
     * Galeri. Tidak ada state "sedang generate" global, jadi pengguna bebas
     * balik ke Beranda dan generate lagi tanpa menunggu yang sebelumnya.
     */
    fun generate() {
        val currentPrompt = prompt.trim()
        if (currentPrompt.isBlank()) return
        val currentMode = mode
        if (currentMode == GenerateMode.IMG2IMG && referenceImage == null) return

        // Salin semua yang dibutuhkan proses latar sekarang juga,
        // karena pengguna bebas mengubah isi Beranda setelah ini.
        val key = apiKey.value
        val currentResolution = if (currentMode == GenerateMode.IMG2IMG) editResolution else resolution
        val currentAspectRatio = if (currentMode == GenerateMode.IMG2IMG) editAspectRatio else aspectRatio
        val currentImageBase64 = referenceImage?.base64
        val galleryMode = if (currentMode == GenerateMode.IMG2IMG) {
            GalleryItem.MODE_IMG2IMG
        } else {
            GalleryItem.MODE_TEXT2IMAGE
        }

        errorMessage = null
        prompt = ""
        navigateToGallery = true

        viewModelScope.launch {
            val pending = galleryStore.addPending(
                prompt = currentPrompt,
                resolution = currentResolution,
                aspectRatio = currentAspectRatio,
                mode = galleryMode
            )
            galleryItems = listOf(pending) + galleryItems

            try {
                val bytes = if (currentMode == GenerateMode.IMG2IMG) {
                    VeniceApi.editImage(
                        apiKey = key,
                        prompt = currentPrompt,
                        imageBase64 = currentImageBase64!!,
                        aspectRatio = currentAspectRatio,
                        resolution = currentResolution
                    )
                } else {
                    VeniceApi.generate(
                        apiKey = key,
                        prompt = currentPrompt,
                        resolution = currentResolution,
                        aspectRatio = currentAspectRatio
                    )
                }
                val done = galleryStore.completePending(pending, bytes)
                if (done != null) replaceGalleryItem(done)
            } catch (e: VeniceApi.VeniceException) {
                replaceGalleryItem(galleryStore.failPending(pending, e.message ?: "Generate gagal."))
            } catch (e: Exception) {
                replaceGalleryItem(
                    galleryStore.failPending(pending, "Ada yang gagal: ${e.message}")
                )
            }
        }
    }

    private fun replaceGalleryItem(updated: GalleryItem) {
        galleryItems = galleryItems.map { if (it.id == updated.id) updated else it }
    }

    fun deleteGalleryItem(item: GalleryItem) {
        viewModelScope.launch {
            galleryStore.delete(item)
            galleryItems = galleryItems.filter { it.id != item.id }
        }
    }

    fun clearGallery() {
        viewModelScope.launch {
            galleryStore.clear()
            galleryItems = emptyList()
        }
    }
}
