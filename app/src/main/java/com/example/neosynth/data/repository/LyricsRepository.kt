package com.example.neosynth.data.repository

import android.util.Log
import com.example.neosynth.data.model.LyricsResult
import com.example.neosynth.data.remote.LyricsApiService
import com.example.neosynth.data.remote.NeteaseApiService
import com.example.neosynth.utils.LrcParser
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

    suspend fun getBestLyricsOption(
        artist: String,
        title: String,
        album: String? = null,
        duration: Int? = null
    ): LyricsResult? {
        Log.d("LyricsRepository", "Searching best lyrics option for: $artist - $title")

        val lrclibBest = getBestFromLrclib(
            artist = artist,
            title = title,
            album = album,
            duration = duration
        )
        if (lrclibBest != null) {
            return lrclibBest
        }

        return getBestFromNetease(
            artist = artist,
            title = title
        )
    }
    
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
                        val label = lrclibLabel(body)
                        if (!body.syncedLyrics.isNullOrBlank() && isUsableSyncedLyrics(body.syncedLyrics)) {
                            results.add(LyricsResult(
                                id = "${id}_synced",
                                source = label,
                                isSynced = true,
                                lyric = body.syncedLyrics
                            ))
                        }
                        if (!body.plainLyrics.isNullOrBlank() && isUsablePlainLyrics(body.plainLyrics)) {
                            results.add(LyricsResult(
                                id = "${id}_plain",
                                source = label,
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
        
        // PASO 2: /api/search por campos separados (title + artist)
        try {
            val byFields = lyricsApi.searchLyrics(
                trackName = title,
                artistName = artist
            )
            if (byFields.isSuccessful) {
                byFields.body()?.forEach { match ->
                    val id = match.id?.toString() ?: "lrclib_sf_${match.hashCode()}"
                    val label = lrclibLabel(match)
                    if (!match.syncedLyrics.isNullOrBlank() && isUsableSyncedLyrics(match.syncedLyrics)) {
                        results.add(LyricsResult(
                            id = "${id}_synced",
                            source = label,
                            isSynced = true,
                            lyric = match.syncedLyrics
                        ))
                    }
                    if (!match.plainLyrics.isNullOrBlank() && isUsablePlainLyrics(match.plainLyrics)) {
                        results.add(LyricsResult(
                            id = "${id}_plain",
                            source = label,
                            isSynced = false,
                            lyric = match.plainLyrics
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LyricsRepository", "LRCLIB /search (fields) error: ${e.message}")
        }

        // PASO 3: /api/search con q libre (igual que la búsqueda en la web de LRCLIB)
        // Esto captura coincidencias que los campos separados pueden no devolver.
        try {
            val qQuery = "$title $artist".trim()
            val byQ = lyricsApi.searchLyrics(query = qQuery)
            if (byQ.isSuccessful) {
                byQ.body()?.forEach { match ->
                    val id = match.id?.toString() ?: "lrclib_q_${match.hashCode()}"
                    val label = lrclibLabel(match)
                    if (!match.syncedLyrics.isNullOrBlank() && isUsableSyncedLyrics(match.syncedLyrics)) {
                        results.add(LyricsResult(
                            id = "${id}_synced",
                            source = label,
                            isSynced = true,
                            lyric = match.syncedLyrics
                        ))
                    }
                    if (!match.plainLyrics.isNullOrBlank() && isUsablePlainLyrics(match.plainLyrics)) {
                        results.add(LyricsResult(
                            id = "${id}_plain",
                            source = label,
                            isSynced = false,
                            lyric = match.plainLyrics
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LyricsRepository", "LRCLIB /search (q) error: ${e.message}")
        }

        return results.distinctBy { it.id }
    }

    private suspend fun getBestFromLrclib(
        artist: String,
        title: String,
        album: String?,
        duration: Int?
    ): LyricsResult? {
        val artistVariants = generateArtistVariants(artist)

        // Ruta rápida inicial: intentar solo match exacto con pocas variantes.
        // La búsqueda amplia (/search) queda para loadLyricsOptions().
        for (artistVariant in artistVariants.take(2)) {
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
                        if (!body.syncedLyrics.isNullOrBlank() && isUsableSyncedLyrics(body.syncedLyrics)) {
                            return LyricsResult(
                                id = "${id}_synced",
                                   source = lrclibLabel(body),
                                isSynced = true,
                                lyric = body.syncedLyrics
                            )
                        }
                        if (!body.plainLyrics.isNullOrBlank() && isUsablePlainLyrics(body.plainLyrics)) {
                            return LyricsResult(
                                id = "${id}_plain",
                                   source = lrclibLabel(body),
                                isSynced = false,
                                lyric = body.plainLyrics
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LyricsRepository", "Best LRCLIB /get error: ${e.message}")
            }
        }

        return null
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
                                if (!lrc.isNullOrBlank() && isUsableSyncedLyrics(lrc)) {
                                    results.add(LyricsResult(
                                        id = "netease_${song.id}_lrc",
                                        source = "Netease ($songName)",
                                        isSynced = true,
                                        lyric = lrc
                                    ))
                                }
                                
                                // KLyric (Karaoke) a veces es mejor o diferente
                                val klyric = lyricsResponse.klyric?.lyric
                                          if (!klyric.isNullOrBlank() && klyric != lrc && isUsableSyncedLyrics(klyric)) {
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

    private suspend fun getBestFromNetease(
        artist: String,
        title: String
    ): LyricsResult? {
        val artistVariants = generateArtistVariants(artist)

        for (artistVariant in artistVariants) {
            try {
                val keywords = "$artistVariant $title"
                val searchResponse = neteaseApi.searchSong(keywords = keywords, limit = 1)

                val firstSong = if (searchResponse.code == 200) {
                    searchResponse.result?.songs?.firstOrNull()
                } else {
                    null
                }

                if (firstSong != null) {
                    val lyricsResponse = neteaseApi.getLyrics(songId = firstSong.id)
                    if (lyricsResponse.code == 200) {
                        val songName = "${firstSong.name} - ${firstSong.artists?.firstOrNull()?.name ?: "Unknown"}"
                        val lrc = lyricsResponse.lrc?.lyric
                        if (!lrc.isNullOrBlank() && isUsableSyncedLyrics(lrc)) {
                            return LyricsResult(
                                id = "netease_${firstSong.id}_lrc",
                                source = "Netease ($songName)",
                                isSynced = true,
                                lyric = lrc
                            )
                        }

                        val klyric = lyricsResponse.klyric?.lyric
                        if (!klyric.isNullOrBlank() && isUsableSyncedLyrics(klyric)) {
                            return LyricsResult(
                                id = "netease_${firstSong.id}_klyric",
                                source = "Netease Karaoke ($songName)",
                                isSynced = true,
                                lyric = klyric
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LyricsRepository", "Best Netease error: ${e.message}")
            }
        }

        return null
    }
    
    /** Construye una etiqueta legible para mostrar en el selector de letras */
    private fun lrclibLabel(match: com.example.neosynth.data.remote.responses.LrclibResponse): String {
        val album = match.albumName?.takeIf { it.isNotBlank() }
        val dur = match.duration?.let { d ->
            val m = d / 60
            val s = d % 60
            "${m}:${s.toString().padStart(2, '0')}"
        }
        return buildString {
            append(album ?: match.trackName ?: "LRCLIB")
            if (dur != null) { append(" • "); append(dur) }
        }
    }

    private fun isUsableSyncedLyrics(content: String): Boolean {
        val parsed = LrcParser.parse(content)
        if (parsed.size < 4) return false
        val distinctLines = parsed
            .map { it.text.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .size
        return distinctLines >= 3
    }

    private fun isUsablePlainLyrics(content: String): Boolean {
        val lines = content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("[") && it.endsWith("]") }
            .filterNot { it.startsWith("作词") || it.startsWith("作曲") || it.startsWith("编曲") }
        if (lines.size < 4) return false
        val distinct = lines.map { it.lowercase() }.distinct().size
        return distinct >= 3
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
