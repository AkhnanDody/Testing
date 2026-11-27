package com.golda.veniceseedream.ui

object Options {
    val RESOLUTIONS = listOf("1K", "1.5K", "2K")
    val ASPECT_RATIOS = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3", "21:9")
    /** Resolusi & rasio buat img2img — tier & rasio keluarga Seedream. */
    val EDIT_RESOLUTIONS = listOf("1K", "1.5K", "2K")
    val EDIT_ASPECT_RATIOS = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3", "21:9")

    /** Cari rasio terdekat dari daftar buat ukuran gambar (lebar x tinggi). */
    fun closestAspectRatio(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "1:1"
        val target = width.toFloat() / height
        val values = mapOf(
            "1:1" to 1f,
            "3:2" to 1.5f,
            "16:9" to (16f / 9f),
            "21:9" to (21f / 9f),
            "9:16" to (9f / 16f),
            "2:3" to (2f / 3f),
            "3:4" to (3f / 4f),
            "4:3" to (4f / 3f)
        )
        return values.minByOrNull { kotlin.math.abs(it.value - target) }?.key ?: "1:1"
    }
}
