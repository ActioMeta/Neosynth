package com.example.neosynth.data.remote

import com.example.neosynth.data.remote.responses.SubsonicResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NavidromeApiService {
    @GET("rest/search3.view")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("u") user: String, @Query("t") token: String, @Query("s") salt: String,
        @Query("v") v: String = "1.16.1", @Query("c") c: String = "Neosynth", @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getArtists.view")
    suspend fun getArtists(
        @Query("u") user: String, @Query("t") token: String, @Query("s") salt: String,
        @Query("v") v: String = "1.16.1", @Query("c") c: String = "Neosynth", @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getAlbumList2.view")
    suspend fun getAlbumList(
        @Query("type") type: String,
        @Query("u") user: String, @Query("t") token: String, @Query("s") salt: String,
        @Query("v") v: String = "1.16.1", @Query("c") c: String = "Neosynth", @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(
        @Query("u") user: String, @Query("t") token: String, @Query("s") salt: String,
        @Query("v") v: String = "1.16.1", @Query("c") c: String = "Neosynth", @Query("f") f: String = "json"
    ): SubsonicResponse
    @GET("rest/ping.view")
    suspend fun ping(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "Neosynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getAlbumList2")
    suspend fun getRecentlyAdded(
        @Query("type") type: String = "newest",
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String,
        @Query("c") c: String,
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(
        @Query("size") size: Int = 20,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String,
        @Query("c") c: String,
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getAlbum")
    suspend fun getAlbum(
        @Query("id") albumId: String,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getGenres")
    suspend fun getGenres(
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getSongsByGenre")
    suspend fun getSongsByGenre(
        @Query("genre") genre: String,
        @Query("count") count: Int = 50,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getArtist")
    suspend fun getArtist(
        @Query("id") artistId: String,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getArtistInfo2")
    suspend fun getArtistInfo(
        @Query("id") artistId: String,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(
        @Query("id") playlistId: String,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/createPlaylist")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/updatePlaylist")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("comment") comment: String? = null,
        @Query("public") isPublic: Boolean? = null,
        @Query("songIdToAdd") songIdToAdd: String? = null,
        @Query("songIndexToRemove") songIndexToRemove: Int? = null,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/deletePlaylist")
    suspend fun deletePlaylist(
        @Query("id") id: String,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/star")
    suspend fun star(
        @Query("id") id: List<String>, // Múltiples IDs permitidos según Subsonic API
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/unstar")
    suspend fun unstar(
        @Query("id") id: List<String>, // Múltiples IDs permitidos según Subsonic API
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse

    @GET("rest/updatePlaylist")
    suspend fun addToPlaylist(
        @Query("playlistId") playlistId: String,
        @Query("songIdToAdd") songIds: List<String>,
        @Query("u") u: String,
        @Query("t") t: String,
        @Query("s") s: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "NeoSynth",
        @Query("f") f: String = "json"
    ): SubsonicResponse
}