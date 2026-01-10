package com.example.neosynth.utils

/**
 * Representa una línea de letra con su timestamp
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * Parser para formato LRC (Lyrics)
 * Soporta formatos: [mm:ss.xx] [mm:ss:xx] [mm:ss]
 */
object LrcParser {
    private val LRC_REGEX = """\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\](.*)""".toRegex()
    
    /**
     * Parsea contenido LRC a lista de líneas con timestamps
     */
    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        
        val lines = mutableListOf<LyricLine>()
        
        lrcContent.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach
            
            val match = LRC_REGEX.find(trimmedLine)
            if (match != null) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0
                val seconds = match.groupValues[2].toLongOrNull() ?: 0
                val milliseconds = match.groupValues[3].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0
                val text = match.groupValues[4].trim()
                
                val timeMs = minutes * 60000 + 
                             seconds * 1000 + 
                             when (match.groupValues[3].length) {
                                 1 -> milliseconds * 100  // .x -> x00ms
                                 2 -> milliseconds * 10   // .xx -> xx0ms
                                 3 -> milliseconds        // .xxx -> xxxms
                                 else -> 0
                             }
                
                if (text.isNotBlank()) {
                    lines.add(LyricLine(timeMs, text))
                }
            }
        }
        
        return lines.sortedBy { it.timeMs }
    }
    
    /**
     * Encuentra el índice de la línea actual basado en la posición de reproducción
     */
    fun getCurrentLineIndex(lyrics: List<LyricLine>, currentPositionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        
        for (i in lyrics.indices.reversed()) {
            if (currentPositionMs >= lyrics[i].timeMs) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Verifica si el contenido es formato LRC válido
     */
    fun isLrcFormat(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        return content.lines().any { LRC_REGEX.containsMatchIn(it) }
    }
}
