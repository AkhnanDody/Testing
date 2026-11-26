package com.golda.veniceseedream.ui

object Options {
    val RESOLUTIONS = listOf("1K", "1.5K", "2K")
    val ASPECT_RATIOS = listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3", "21:9")
    /** Resolusi buat img2img — endpoint /image/edit cuma terima tier 1K/2K/4K. */
    val EDIT_RESOLUTIONS = listOf("1K", "2K")
}
