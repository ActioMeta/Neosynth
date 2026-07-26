package com.example.neosynth.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicBrainzApiService {
    @GET("ws/2/artist/")
    suspend fun searchArtist(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Header("User-Agent") userAgent: String = "NeoSynth/1.0.0 (https://github.com/actioMeta/NeoSynth)"
    ): MusicBrainzSearchResponse

    @GET("ws/2/artist/{mbid}")
    suspend fun getArtistDetails(
        @Path("mbid") mbid: String,
        @Query("inc") inc: String = "url-rels+tags",
        @Query("fmt") format: String = "json",
        @Header("User-Agent") userAgent: String = "NeoSynth/1.0.0 (https://github.com/actioMeta/NeoSynth)"
    ): MusicBrainzArtistDetails
}

interface WikipediaApiService {
    @GET("api/rest_v1/page/summary/{title}")
    suspend fun getPageSummary(
        @Path("title") title: String
    ): WikipediaSummaryResponse
}

data class MusicBrainzSearchResponse(
    val artists: List<MusicBrainzArtistDto>? = null
)

data class MusicBrainzArtistDto(
    val id: String,
    val name: String,
    val type: String? = null,
    val country: String? = null,
    val disambiguation: String? = null,
    val tags: List<MusicBrainzTagDto>? = null
)

data class MusicBrainzTagDto(
    val name: String,
    val count: Int? = null
)

data class MusicBrainzArtistDetails(
    val id: String,
    val name: String,
    val relations: List<MusicBrainzRelationDto>? = null
)

data class MusicBrainzRelationDto(
    val type: String? = null,
    val url: MusicBrainzUrlDto? = null
)

data class MusicBrainzUrlDto(
    val resource: String? = null
)

data class WikipediaSummaryResponse(
    val title: String? = null,
    val extract: String? = null,
    val thumbnail: WikipediaImageDto? = null,
    val originalimage: WikipediaImageDto? = null,
    @SerializedName("content_urls") val contentUrls: WikipediaContentUrlsDto? = null
)

data class WikipediaImageDto(
    val source: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class WikipediaContentUrlsDto(
    val desktop: WikipediaPageUrlDto? = null
)

data class WikipediaPageUrlDto(
    val page: String? = null
)
