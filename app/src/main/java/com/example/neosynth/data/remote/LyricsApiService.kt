package com.example.neosynth.data.remote

import com.example.neosynth.data.remote.responses.LrclibResponse
import com.example.neosynth.data.remote.responses.MusixmatchResponse
import com.example.neosynth.data.remote.responses.NeteaseSearchResponse
import com.example.neosynth.data.remote.responses.NeteaseLyricResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface LyricsApiService {
    
    // LRCLIB API (gratis, sin API key)
    @GET("get")
    suspend fun getLyricsFromLrclib(
        @Query("artist_name") artistName: String,
        @Query("track_name") trackName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") duration: Int? = null
    ): Response<LrclibResponse>
    
    // Musixmatch API (requiere API key)
    @GET("track.lyrics.get")
    suspend fun getLyricsFromMusixmatch(
        @Query("track_id") trackId: String? = null,
        @Query("commontrack_id") commontrackId: String? = null,
        @Query("apikey") apiKey: String
    ): MusixmatchResponse
    
    // Musixmatch: Buscar track para obtener ID
    @GET("track.search")
    suspend fun searchTrackMusixmatch(
        @Query("q_artist") artist: String,
        @Query("q_track") track: String,
        @Query("page_size") pageSize: Int = 1,
        @Query("apikey") apiKey: String
    ): MusixmatchResponse
}

// Netease Cloud Music API Service (separado)
interface NeteaseApiService {
    
    // Buscar canción por nombre y artista
    @GET("search")
    suspend fun searchSong(
        @Query("keywords") keywords: String,
        @Query("type") type: Int = 1, // 1 = songs
        @Query("limit") limit: Int = 5
    ): NeteaseSearchResponse
    
    // Obtener letras por ID de canción
    @GET("lyric")
    suspend fun getLyrics(
        @Query("id") songId: Long
    ): NeteaseLyricResponse
}
