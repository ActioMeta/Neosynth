package com.example.neosynth.data.repository

import com.example.neosynth.data.remote.MusicBrainzApiService
import com.example.neosynth.data.remote.WikipediaApiService
import com.example.neosynth.data.remote.responses.ArtistInfo
import com.example.neosynth.depsInjection.WikipediaEnRetrofit
import com.example.neosynth.depsInjection.WikipediaEsRetrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicBrainzRepository @Inject constructor(
    private val musicBrainzApi: MusicBrainzApiService,
    @WikipediaEsRetrofit private val wikiEsApi: WikipediaApiService,
    @WikipediaEnRetrofit private val wikiEnApi: WikipediaApiService
) {
    suspend fun getArtistInfo(artistName: String): ArtistInfo? {
        if (artistName.isBlank()) return null
        return try {
            // 1. Consultar búsqueda en MusicBrainz
            val query = "artist:\"${artistName.trim()}\""
            val searchResponse = musicBrainzApi.searchArtist(query = query)
            val topArtist = searchResponse.artists?.firstOrNull() ?: return fetchFromWikipediaDirect(artistName)

            val mbid = topArtist.id

            // 2. Obtener detalles del artista e identificar relaciones externas
            val details = try {
                musicBrainzApi.getArtistDetails(mbid = mbid)
            } catch (e: Exception) {
                null
            }

            var wikiTitle: String? = null
            var isSpanish = false
            details?.relations?.forEach { rel ->
                val res = rel.url?.resource ?: ""
                if (res.contains("wikipedia.org/wiki/")) {
                    if (res.contains("es.wikipedia.org")) {
                        isSpanish = true
                        wikiTitle = res.substringAfter("/wiki/")
                    } else if (wikiTitle == null && res.contains("en.wikipedia.org")) {
                        isSpanish = false
                        wikiTitle = res.substringAfter("/wiki/")
                    }
                }
            }

            val searchTitle = if (!wikiTitle.isNullOrBlank()) {
                java.net.URLDecoder.decode(wikiTitle!!, "UTF-8")
            } else {
                artistName
            }

            // 3. Consultar resumen en Wikipedia (español primero, luego inglés)
            var wikiSummary = if (isSpanish && wikiTitle != null) {
                try { wikiEsApi.getPageSummary(searchTitle) } catch (e: Exception) { null }
            } else null

            if (wikiSummary == null || wikiSummary.extract.isNullOrBlank()) {
                wikiSummary = try { wikiEsApi.getPageSummary(artistName) } catch (e: Exception) { null }
            }
            if (wikiSummary == null || wikiSummary.extract.isNullOrBlank()) {
                wikiSummary = try { wikiEnApi.getPageSummary(searchTitle) } catch (e: Exception) { null }
            }

            val bioText = wikiSummary?.extract
                ?: topArtist.disambiguation
                ?: topArtist.type?.let { type -> "Artista ($type)${if (!topArtist.country.isNullOrBlank()) " - ${topArtist.country}" else ""}" }

            val imageUrl = wikiSummary?.originalimage?.source
                ?: wikiSummary?.thumbnail?.source

            val wikiPageUrl = wikiSummary?.contentUrls?.desktop?.page
                ?: "https://musicbrainz.org/artist/$mbid"

            if (bioText.isNullOrBlank() && imageUrl.isNullOrBlank()) {
                return null
            }

            ArtistInfo(
                biography = bioText,
                musicBrainzId = mbid,
                lastFmUrl = wikiPageUrl,
                smallImageUrl = imageUrl,
                mediumImageUrl = imageUrl,
                largeImageUrl = imageUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
            fetchFromWikipediaDirect(artistName)
        }
    }

    private suspend fun fetchFromWikipediaDirect(artistName: String): ArtistInfo? {
        return try {
            var wikiSummary = try { wikiEsApi.getPageSummary(artistName) } catch (e: Exception) { null }
            if (wikiSummary == null || wikiSummary.extract.isNullOrBlank()) {
                wikiSummary = try { wikiEnApi.getPageSummary(artistName) } catch (e: Exception) { null }
            }
            val bio = wikiSummary?.extract ?: return null
            val img = wikiSummary.originalimage?.source ?: wikiSummary.thumbnail?.source
            val page = wikiSummary.contentUrls?.desktop?.page
            ArtistInfo(
                biography = bio,
                musicBrainzId = null,
                lastFmUrl = page,
                smallImageUrl = img,
                mediumImageUrl = img,
                largeImageUrl = img
            )
        } catch (e: Exception) {
            null
        }
    }
}
