package com.example.neosynth.data.repository

import android.util.Log
import com.example.neosynth.data.remote.LyricsApiService
import com.example.neosynth.data.remote.NeteaseApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val lyricsApi: LyricsApiService,
    private val neteaseApi: NeteaseApiService
) {
    
    /**
     * Obtener letras sincronizadas con estrategia de fallback:
     * 1. Intenta LRCLIB (gratis, sin límites, música occidental)
     * 2. Intenta Netease (gratis, sin límites, fuerte en música asiática)
     * 3. Si ambos fallan, retorna null
     * 
     * Nota: Musixmatch se agregará cuando el usuario configure su API key
     */
    suspend fun getSyncedLyrics(
        artist: String,
        title: String,
        album: String? = null,
        duration: Int? = null
    ): String? {
        return try {
            Log.d("LyricsRepository", "Fetching lyrics for: $artist - $title")
            
            // Intentar LRCLIB primero
            val lrclibResult = getLyricsFromLrclib(artist, title, album, duration)
            if (lrclibResult != null) {
                Log.d("LyricsRepository", "Found synced lyrics from LRCLIB (${lrclibResult.length} chars)")
                return lrclibResult
            }
            
            // Intentar Netease como fallback
            val neteaseResult = getLyricsFromNetease(artist, title)
            if (neteaseResult != null) {
                Log.d("LyricsRepository", "Found synced lyrics from Netease (${neteaseResult.length} chars)")
                return neteaseResult
            }
            
            Log.d("LyricsRepository", "No synced lyrics found")
            null
        } catch (e: Exception) {
            Log.e("LyricsRepository", "Error fetching lyrics", e)
            null
        }
    }
    
    /**
     * Obtener letras de LRCLIB con estrategia de búsqueda múltiple
     */
    private suspend fun getLyricsFromLrclib(
        artist: String,
        title: String,
        album: String?,
        duration: Int?
    ): String? {
        // Generar variantes del artista para búsqueda
        val artistVariants = generateArtistVariants(artist)
        
        Log.d("LyricsRepository", "Artist variants for '$artist': $artistVariants")
        
        // Intentar con cada variante del artista
        for (artistVariant in artistVariants) {
            try {
                Log.d("LyricsRepository", "Trying LRCLIB with artist: '$artistVariant', title: '$title'")
                
                val response = lyricsApi.getLyricsFromLrclib(
                    artistName = artistVariant,
                    trackName = title,
                    albumName = album,
                    duration = duration
                )
                
                // Verificar código de respuesta HTTP
                if (!response.isSuccessful) {
                    Log.d("LyricsRepository", "LRCLIB HTTP ${response.code()} with artist '$artistVariant'")
                    continue
                }
                
                // Verificar si hay datos
                val body = response.body()
                if (body == null) {
                    Log.d("LyricsRepository", "LRCLIB returned null body with artist '$artistVariant'")
                    continue
                }
                
                // Si encontramos letras, retornarlas
                val lyrics = body.syncedLyrics ?: body.plainLyrics
                if (lyrics != null && lyrics.isNotBlank()) {
                    Log.d("LyricsRepository", "✅ LRCLIB SUCCESS with artist: '$artistVariant' (${lyrics.length} chars)")
                    return lyrics
                } else {
                    Log.d("LyricsRepository", "LRCLIB returned empty lyrics with artist '$artistVariant'")
                }
            } catch (e: Exception) {
                Log.e("LyricsRepository", "LRCLIB exception with artist '$artistVariant': ${e.javaClass.simpleName} - ${e.message}")
                e.printStackTrace()
                // Continuar con la siguiente variante
            }
        }
        
        Log.d("LyricsRepository", "❌ LRCLIB: No results with any artist variant")
        return null
    }
    
    /**
     * Genera variantes del nombre del artista para mejorar búsqueda
     * Ejemplo: "Pearl Jam • Stone Gossard • Eddie Vedder" -> ["Pearl Jam • Stone Gossard • Eddie Vedder", "Pearl Jam", "Stone Gossard", "Eddie Vedder"]
     */
    private fun generateArtistVariants(artist: String): List<String> {
        val variants = mutableListOf<String>()
        
        // 1. Artista original completo (normalizado)
        val normalizedArtist = normalizeText(artist)
        variants.add(normalizedArtist)
        
        // Lista de separadores comunes
        val separators = listOf(",", "&", "•", "/", ";", " x ", " X ", " - ")
        
        // 2. Detectar y separar por cualquier separador común
        for (separator in separators) {
            if (normalizedArtist.contains(separator)) {
                val splitArtists = normalizedArtist.split(separator)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                // Agregar cada artista individual (el primero suele ser el principal)
                variants.addAll(splitArtists)
            }
        }
        
        // 3. Si tiene "feat." o "ft.", extraer artista principal
        val featPattern = Regex("""(.+?)\s+(?:feat\.|ft\.|featuring)\s+.+""", RegexOption.IGNORE_CASE)
        featPattern.find(artist)?.let { match ->
            val mainArtist = match.groupValues[1].trim()
            if (mainArtist.isNotEmpty()) {
                variants.add(mainArtist)
            }
        }
        
        // Eliminar duplicados manteniendo el orden
        return variants.distinct()
    }
    
    /**
     * Obtener letras de Netease Cloud Music con estrategia de búsqueda múltiple
     */
    private suspend fun getLyricsFromNetease(
        artist: String,
        title: String
    ): String? {
        val artistVariants = generateArtistVariants(artist)
        
        // Intentar con cada variante del artista
        for (artistVariant in artistVariants) {
            try {
                val keywords = "$artistVariant $title"
                Log.d("LyricsRepository", "Searching Netease with keywords: $keywords")
                
                val searchResponse = neteaseApi.searchSong(keywords = keywords, limit = 5)
                
                if (searchResponse.code != 200 || searchResponse.result?.songs.isNullOrEmpty()) {
                    Log.d("LyricsRepository", "Netease: No results with artist '$artistVariant'")
                    continue
                }
                
                // Tomar la primera canción que mejor coincida
                val song = searchResponse.result!!.songs!!.firstOrNull() ?: continue
                Log.d("LyricsRepository", "Found Netease song: ${song.name} by ${song.artists?.firstOrNull()?.name}")
                
                // Obtener letras
                val lyricsResponse = neteaseApi.getLyrics(songId = song.id)
                
                if (lyricsResponse.code != 200) {
                    Log.d("LyricsRepository", "Netease lyrics fetch failed with code: ${lyricsResponse.code}")
                    continue
                }
                
                // Priorizar letras en formato LRC (sincronizadas)
                val lyrics = lyricsResponse.klyric?.lyric 
                    ?: lyricsResponse.lrc?.lyric 
                    ?: lyricsResponse.tlyric?.lyric
                
                if (lyrics != null) {
                    Log.d("LyricsRepository", "Netease success with artist: '$artistVariant'")
                    return lyrics
                }
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Netease failed with artist '$artistVariant': ${e.message}")
                // Continuar con la siguiente variante
            }
        }
        
        Log.d("LyricsRepository", "Netease: No results with any artist variant")
        return null
    }
    
    // TODO: Agregar método para Musixmatch cuando se implemente configuración de API key
    /*
    private suspend fun getLyricsFromMusixmatch(
        artist: String,
        title: String,
        apiKey: String
    ): String? {
        // Implementar cuando se agregue soporte para API key del usuario
        return null
    }
    */
    
    /**
     * Normaliza texto para mejorar coincidencias en búsquedas
     * - Quita espacios extras
     * - Normaliza caracteres especiales comunes
     */
    private fun normalizeText(text: String): String {
        return text.trim()
            .replace(Regex("\\s+"), " ") // Múltiples espacios a uno solo
            .replace("'", "'") // Apóstrofe curly a recto
            .replace("'", "'")
            .replace(""", "\"") // Comillas curly a rectas
            .replace(""", "\"")
    }
}
