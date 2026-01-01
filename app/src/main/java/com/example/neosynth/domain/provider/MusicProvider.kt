package com.example.neosynth.domain.provider

import com.example.neosynth.domain.model.*

interface MusicProvider {

    suspend fun getAllSongs(): List<Song>
    suspend fun getAllArtists(): List<Artist>
    suspend fun getAllAlbums(): List<Album>
    suspend fun getAllPlaylists(): List<Playlist>
    suspend fun getAllGenres(): List<Genre>
    suspend fun getAllYears(): List<Int>

    suspend fun getSongsByArtist(artistId: String): List<Song>
    suspend fun getSongsByAlbum(albumId: String): List<Song>
    suspend fun getSongsByPlaylist(playlistId: String): List<Song>
    suspend fun getSongsByGenre(genre: String): List<Song>
    suspend fun getSongsByYear(year: Int): List<Song>

    suspend fun getArtistDetails(artistId: String): Artist
    suspend fun getAlbumDetails(albumId: String): Album
    suspend fun getPlaylistDetails(playlistId: String): Playlist

    suspend fun getRecentlyAddedSongs(limit: Int = 50): List<Song>
    suspend fun search(query: String): List<Song>

    suspend fun getStreamUrl(songId: String): String
}