package com.example.neosynth.data.repository

import android.util.Log
import com.example.neosynth.data.model.LyricsResult
import com.example.neosynth.data.remote.LyricsApiService
import com.example.neosynth.data.remote.NeteaseApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Singleton
class LyricsRepository @Inject constructor(
    private val lyricsApi: LyricsApiService,
    private val neteaseApi: NeteaseApiService
) {
    
    /**
     * Buscar opciones de letras en todas las fuentes disponibles:
     * 1. LRCLIB (api/get y api/search)
     * 2. Netease
     * Returns a list of all found lyrics options.
     */
    suspend fun searchLyricsOptions(
        artist: String,
        title: String,
        album: String? = null,
        duration: Int? = null
    ): List<LyricsResult> = coroutineScope {
        Log.d("LyricsRepository", "Searching lyrics options for: $artist - $title")
        
        val results = mutableListOf<LyricsResult>()
        
        // Ejecutar búsquedas en paralelo
        val lrclibJob = async { getLyricsFromLrclib(artist, title, album, duration) }
        val neteaseJob = async { getLyricsFromNetease(artist, title) }
        
        val (lrclibResults, neteaseResults) = awaitAll(lrclibJob, neteaseJob)
        
        results.addAll(lrclibResults)
        results.addAll(neteaseResults)
        
        Log.d("LyricsRepository", "Found ${results.size} total lyrics options")
        return@coroutineScope results.distinctBy { it.id } // Evitar duplicados exactos de ID si los hubiera
    }
    
    /**
     * Obtener letras de LRCLIB
     */
    private suspend fun getLyricsFromLrclib(
        artist: String,
        title: String,
        album: String?,
        duration: Int?
    ): List<LyricsResult> {
        val results = mutableListOf<LyricsResult>()
        val artistVariants = generateArtistVariants(artist)
        
        // PASO 1: Intentar con cada variante del artista usando /api/get (coincidencia exacta)
        for (artistVariant in artistVariants) {
            try {
                val response = lyricsApi.getLyricsFromLrclib(
                    artistName = artistVariant,
                    trackName = title,
                    albumName = album,
                    duration = duration
                )
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val id = body.id?.toString() ?: "lrclib_get_${body.hashCode()}"
                        
                        // Agregar synced si existe
                        if (!body.syncedLyrics.isNullOrBlank()) {
                            results.add(LyricsResult(
                                id = "${id}_synced",
                                source = "LRCLIB (Exact Match)",
                                isSynced = true,
                                lyric = body.syncedLyrics
                            ))
                        }
                        
                        // Agregar plain si existe
                        if (!body.plainLyrics.isNullOrBlank()) {
                            results.add(LyricsResult(
                                id = "${id}_plain",
                                source = "LRCLIB (Exact Match)",
                                isSynced = false,
                                lyric = body.plainLyrics
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LyricsRepository", "LRCLIB /get error: ${e.message}")
            }
        }
        
        // PASO 2: Intentar con /api/search para obtener más opciones
        try {
            // Usar la primera variante (la más completa) para la búsqueda general
            val searchResponse = lyricsApi.searchLyrics(
                trackName = title,
                artistName = artist,
                duration = duration
            )
            
            if (searchResponse.isSuccessful) {
                searchResponse.body()?.forEach { match ->
                    val id = match.id?.toString() ?: "lrclib_search_${match.hashCode()}"
                    
                    if (!match.syncedLyrics.isNullOrBlank()) {
                        results.add(LyricsResult(
                            id = "${id}_synced",
                            source = "LRCLIB (Search)",
                            isSynced = true,
                            lyric = match.syncedLyrics
                        ))
                    }
                    
                    if (!match.plainLyrics.isNullOrBlank()) {
                        results.add(LyricsResult(
                            id = "${id}_plain",
                            source = "LRCLIB (Search)",
                            isSynced = false,
                            lyric = match.plainLyrics
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LyricsRepository", "LRCLIB /search error: ${e.message}")
        }
        
        return results
    }
    
    /**
     * Obtener letras de Netease Cloud Music
     */
    private suspend fun getLyricsFromNetease(
        artist: String,
        title: String
    ): List<LyricsResult> {
        val results = mutableListOf<LyricsResult>()
        val artistVariants = generateArtistVariants(artist)
        
        // Intentar con variantes hasta encontrar algo, pero netease suele devolver una lista de canciones en search
        // Así que buscaremos con la variante principal y procesaremos los resultados
        
        for (artistVariant in artistVariants) {
            try {
                val keywords = "$artistVariant $title"
                val searchResponse = neteaseApi.searchSong(keywords = keywords, limit = 5)
                
                if (searchResponse.code == 200 && !searchResponse.result?.songs.isNullOrEmpty()) {
                    // Procesar las primeras 3 canciones encontradas
                    searchResponse.result!!.songs!!.take(3).forEach { song ->
                        try {
                            val lyricsResponse = neteaseApi.getLyrics(songId = song.id)
                            if (lyricsResponse.code == 200) {
                                val songName = "${song.name} - ${song.artists?.firstOrNull()?.name ?: "Unknown"}"
                                
                                // Preferir LRC
                                val lrc = lyricsResponse.lrc?.lyric
                                if (!lrc.isNullOrBlank()) {
                                    results.add(LyricsResult(
                                        id = "netease_${song.id}_lrc",
                                        source = "Netease ($songName)",
                                        isSynced = true,
                                        lyric = lrc
                                    ))
                                }
                                
                                // KLyric (Karaoke) a veces es mejor o diferente
                                val klyric = lyricsResponse.klyric?.lyric
                                if (!klyric.isNullOrBlank() && klyric != lrc) {
                                     results.add(LyricsResult(
                                        id = "netease_${song.id}_klyric",
                                        source = "Netease Karaoke ($songName)",
                                        isSynced = true,
                                        lyric = klyric
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LyricsRepository", "Netease lyric fetch error for ${song.id}: ${e.message}")
                        }
                    }
                    
                    // Si encontramos algo con esta variante, terminamos (para no spammear)
                    if (results.isNotEmpty()) break
                }
            } catch (e: Exception) {
                Log.e("LyricsRepository", "Netease search error: ${e.message}")
            }
        }
        
        return results
    }
    
    private fun normalizeText(text: String): String {
        return text.trim()
            .replace(Regex("\\s+"), " ")
            .replace("'", "'")
            .replace("'", "'")
            .replace(""", "\"")
            .replace(""", "\"")
    }

    private fun generateArtistVariants(artist: String): List<String> {
        val variants = mutableListOf<String>()
        val normalizedArtist = normalizeText(artist)
        variants.add(normalizedArtist)
        
        val separators = listOf(",", "&", "•", "/", ";", " x ", " X ", " - ")
        for (separator in separators) {
            if (normalizedArtist.contains(separator)) {
                val splitArtists = normalizedArtist.split(separator)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                variants.addAll(splitArtists)
            }
        }
        
        val featPattern = Regex("""(.+?)\s+(?:feat\.|ft\.|featuring)\s+.+""", RegexOption.IGNORE_CASE)
        featPattern.find(artist)?.let { match ->
            val mainArtist = match.groupValues[1].trim()
            if (mainArtist.isNotEmpty()) {
                variants.add(mainArtist)
            }
        }
        
        return variants.distinct()
    }
}
