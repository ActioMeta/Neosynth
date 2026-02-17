package com.example.neosynth.data.model

data class LyricsResult(
    val id: String,
    val source: String, // "LRCLIB", "Netease"
    val isSynced: Boolean,
    val lyric: String,
    val quality: String? = null // e.g., duration match confidence
)
