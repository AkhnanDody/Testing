package com.golda.veniceseedream.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golda.veniceseedream.data.GalleryItem
import com.golda.veniceseedream.data.GalleryStore
import com.golda.veniceseedream.data.SettingsStore
import com.golda.veniceseedream.data.VeniceApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val galleryStore = GalleryStore(application)

    val galleryDir get() = galleryStore.galleryDir

    val apiKey: StateFlow<String> = settingsStore.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    var prompt by mutableStateOf("")
    var resolution by mutableStateOf(Options.RESOLUTIONS.first())
    var aspectRatio by mutableStateOf(Options.ASPECT_RATIOS.first())

    var isGenerating by mutableStateOf(false)
        private set
    var elapsedSeconds by mutableIntStateOf(0)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var latestItem by mutableStateOf<GalleryItem?>(null)
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

    fun refreshGallery() {
        viewModelScope.launch {
            galleryItems = galleryStore.load()
            if (latestItem == null) latestItem = galleryItems.firstOrNull()
        }
    }

    fun generate() {
        val currentPrompt = prompt.trim()
        if (currentPrompt.isBlank()) {
            errorMessage = "Prompt-nya kosong, isi dulu!"
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
                val bytes = VeniceApi.generate(
                    apiKey = apiKey.value,
                    prompt = currentPrompt,
                    resolution = resolution,
                    aspectRatio = aspectRatio
                )
                val item = galleryStore.add(currentPrompt, resolution, aspectRatio, bytes)
                latestItem = item
                galleryItems = listOf(item) + galleryItems
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
            if (latestItem?.id == item.id) latestItem = galleryItems.firstOrNull()
        }
    }

    fun clearGallery() {
        viewModelScope.launch {
            galleryStore.clear()
            galleryItems = emptyList()
            latestItem = null
        }
    }
}
