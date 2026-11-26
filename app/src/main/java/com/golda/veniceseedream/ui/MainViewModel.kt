package com.golda.veniceseedream.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golda.veniceseedream.data.GalleryItem
import com.golda.veniceseedream.data.GalleryStore
import com.golda.veniceseedream.data.ImageCompressor
import com.golda.veniceseedream.data.SettingsStore
import com.golda.veniceseedream.data.VeniceApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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
    /** Resolusi khusus img2img — endpoint /image/edit cuma terima tier 1K/2K/4K. */
    var editResolution by mutableStateOf(Options.EDIT_RESOLUTIONS.first())

    /** Gambar referensi img2img, sudah dikompresi (maks 1024px) di client. */
    var referenceImage by mutableStateOf<ImageCompressor.CompressedImage?>(null)
        private set
    var isCompressing by mutableStateOf(false)
        private set

    var isGenerating by mutableStateOf(false)
        private set
    var elapsedSeconds by mutableIntStateOf(0)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** True sesaat setelah generate berhasil — HomeScreen pindah ke Galeri. */
    var navigateToGallery by mutableStateOf(false)
        private set

    var galleryItems by mutableStateOf<List<GalleryItem>>(emptyList())
        private set

    private var timerJob: Job? = null

    init {
        refreshGallery()
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
        if (isGenerating) return
        isCompressing = true
        errorMessage = null
        viewModelScope.launch {
            try {
                referenceImage = ImageCompressor.compress(app, uri)
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
            galleryItems = galleryStore.load()
        }
    }

    fun consumeNavigateToGallery() {
        navigateToGallery = false
    }

    fun generate() {
        val currentPrompt = prompt.trim()
        if (currentPrompt.isBlank()) {
            errorMessage = "Prompt-nya kosong, isi dulu!"
            return
        }
        if (mode == GenerateMode.IMG2IMG && referenceImage == null) {
            errorMessage = "Pilih gambar referensi dulu ya."
            return
        }
        if (isGenerating) return

        errorMessage = null
        isGenerating = true
        elapsedSeconds = 0
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                elapsedSeconds++
            }
        }

        viewModelScope.launch {
            try {
                val item = if (mode == GenerateMode.IMG2IMG) {
                    val bytes = VeniceApi.editImage(
                        apiKey = apiKey.value,
                        prompt = currentPrompt,
                        imageBase64 = referenceImage!!.base64,
                        resolution = editResolution
                    )
                    galleryStore.add(
                        prompt = currentPrompt,
                        resolution = editResolution,
                        aspectRatio = "",
                        pngBytes = bytes,
                        mode = GalleryItem.MODE_IMG2IMG
                    )
                } else {
                    val bytes = VeniceApi.generate(
                        apiKey = apiKey.value,
                        prompt = currentPrompt,
                        resolution = resolution,
                        aspectRatio = aspectRatio
                    )
                    galleryStore.add(
                        prompt = currentPrompt,
                        resolution = resolution,
                        aspectRatio = aspectRatio,
                        pngBytes = bytes,
                        mode = GalleryItem.MODE_TEXT2IMAGE
                    )
                }
                galleryItems = listOf(item) + galleryItems
                // Prompt sudah ikut ke galeri, jadi kotaknya langsung dikosongkan.
                // (Cuma kalau berhasil — kalau gagal, prompt tetap ada biar bisa coba lagi.)
                prompt = ""
                navigateToGallery = true
            } catch (e: VeniceApi.VeniceException) {
                errorMessage = e.message
            } catch (e: Exception) {
                errorMessage = "Ada yang gagal: ${e.message}"
            } finally {
                timerJob?.cancel()
                isGenerating = false
            }
        }
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
