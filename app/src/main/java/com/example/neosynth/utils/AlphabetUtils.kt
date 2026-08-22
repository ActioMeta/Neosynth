package com.example.neosynth.utils

import java.text.Normalizer
import java.util.Locale

object AlphabetUtils {

    /**
     * Extracts a normalized uppercase section letter ('A'..'Z') or '#' for any symbol, digit, or non-Latin char.
     * Skips decorative leading symbols like quotes or brackets to reach the true first letter.
     * Accents are stripped (e.g. 'Á' -> 'A', 'É' -> 'E', 'Ñ' -> 'N').
     */
    fun getSectionKey(rawText: String?): Char {
        if (rawText.isNullOrBlank()) return '#'
        
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return '#'
        
        // Find the first meaningful alphanumeric character, skipping decorative leading punctuation
        var targetChar = trimmed.first()
        for (c in trimmed) {
            if (!c.isWhitespace() && c !in "[\"'({<«“`¿¡~.*#_+-/\\") {
                targetChar = c
                break
            }
        }
        
        // Normalize accents: 'Á' -> 'A', 'é' -> 'E', etc.
        val normalized = Normalizer.normalize(targetChar.toString(), Normalizer.Form.NFD)
        val baseChar = normalized.firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }?.uppercaseChar()
        
        return if (baseChar != null && baseChar in 'A'..'Z') {
            baseChar
        } else {
            '#'
        }
    }

    /**
     * Normalized key string for accurate alphabetical sorting where accents are sorted naturally,
     * decorative leading punctuation is ignored for letter ordering,
     * and symbols/numbers ('#') are placed at the beginning.
     */
    fun getSortKey(rawText: String?): String {
        if (rawText.isNullOrBlank()) return "~~~"
        val trimmed = rawText.trim()
        val section = getSectionKey(trimmed)
        
        // Normalize string removing diacritics
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.getDefault())

        // If it belongs to '#', prefix with a space so it consistently sorts before 'A'..'Z'
        return if (section == '#') {
            " $normalized"
        } else {
            // Strip decorative leading quotes/brackets from sort key so "[Official] Song" sorts under 'o'
            val cleanStart = normalized.trimStart { it in "[\"'({<«“`¿¡~.*#_+-/\\ " }
            if (cleanStart.isNotEmpty()) cleanStart else normalized
        }
    }
}
