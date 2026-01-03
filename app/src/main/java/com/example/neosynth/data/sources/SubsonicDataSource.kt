package com.example.neosynth.data.sources

import com.example.neosynth.domain.repository.MusicDataSource
import com.example.neosynth.domain.repository.StreamQuality
import com.example.neosynth.domain.repository.DownloadQuality
import com.example.neosynth.domain.model.*
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.local.entities.ServerEntity
import javax.inject.Inject

/**
 * Implementación de MusicDataSource para servidores Subsonic/Navidrome
 */
class SubsonicDataSource(
    private val api: NavidromeApiService,
    private val server: ServerEntity
) : MusicDataSource {
    
    override val sourceType = MusicSourceType.SUBSONIC
    override val sourceId = server.id.toString()
    override val sourceName = server.name
    
    private val baseUrl = server.url.removeSuffix("/")
    private val authParams = "u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
    
    override suspend fun isAvailable(): Boolean {
        return try {
            val response = api.ping(
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            response.response.status == "ok"
        } catch (e: Exception) {
            false
        }
    }
    
    // ===== SONGS =====
    
    override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> {
        return try {
            val response = api.searchSongs(
                query = query,
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            
            val songs = response.response.searchResult3?.song?.map { dto ->
                Song(
                    id = "${sourceType.name}_${sourceId}_${dto.id}",
                    title = dto.title,
                    artist = dto.artist ?: "Unknown",
                    artistId = "${sourceType.name}_${sourceId}_${dto.artistId ?: ""}",
                    album = dto.album ?: "Unknown",
                    albumId = "${sourceType.name}_${sourceId}_${dto.albumId ?: ""}",
                    duration = (dto.duration ?: 0) * 1000L,
                    coverArtUrl = buildCoverArtUrl(dto.coverArt),
                    sourceType = sourceType,
                    sourceId = sourceId,
                    year = dto.year,
                    genre = null,
                    trackNumber = null,
                    metadata = mapOf(
                        "subsonicId" to dto.id
                    )
                )
            } ?: emptyList()
            
            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getSongById(songId: String): Result<Song?> {
        // NOTE: Subsonic API does not have a getSong endpoint for individual songs.
        // Songs are obtained through getAlbum, getPlaylist, getArtist, or search3 endpoints.
        // This method returns null as the app uses Room database for local song lookups.
        return Result.success(null)
    }
    
    override suspend fun getSongsByAlbum(albumId: String): Result<List<Song>> {
        return try {
            // Extraer el ID original de Subsonic del albumId compuesto
            val subsonicAlbumId = albumId.removePrefix("${sourceType.name}_${sourceId}_")
            
            val response = api.getAlbum(
                albumId = subsonicAlbumId,
                u = server.username,
                t = server.token,
                s = server.salt
            )
            
            val songs = response.response.albumDetails?.song?.map { dto ->
                Song(
                    id = "${sourceType.name}_${sourceId}_${dto.id}",
                    title = dto.title,
                    artist = dto.artist ?: "Unknown",
                    artistId = "${sourceType.name}_${sourceId}_${dto.artistId ?: ""}",
                    album = dto.album ?: "Unknown",
                    albumId = albumId,
                    duration = (dto.duration ?: 0) * 1000L,
                    coverArtUrl = buildCoverArtUrl(dto.coverArt),
                    sourceType = sourceType,
                    sourceId = sourceId,
                    year = dto.year,
                    trackNumber = null,
                    metadata = mapOf("subsonicId" to dto.id)
                )
            } ?: emptyList()
            
            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getSongsByArtist(artistId: String): Result<List<Song>> {
        // TODO: Implementar usando getArtist endpoint
        return Result.success(emptyList())
    }
    
    override suspend fun getStreamUrl(songId: String, quality: StreamQuality?): Result<String> {
        val subsonicId = extractSubsonicId(songId)
        val url = buildString {
            append("$baseUrl/rest/stream?")
            append("id=$subsonicId&")
            append(authParams)
            
            if (quality != null && quality != StreamQuality.LOSSLESS) {
                append("&maxBitRate=${quality.bitrate}")
                append("&format=${quality.format}")
            }
        }
        return Result.success(url)
    }
    
    override suspend fun getDownloadUrl(songId: String, quality: DownloadQuality?): Result<String> {
        val subsonicId = extractSubsonicId(songId)
        val url = buildString {
            append("$baseUrl/rest/download?")
            append("id=$subsonicId&")
            append(authParams)
            
            if (quality != null && quality != DownloadQuality.LOSSLESS) {
                append("&maxBitRate=${quality.bitrate}")
                append("&format=${quality.format}")
            }
        }
        return Result.success(url)
    }
    
    // ===== ARTISTS =====
    
    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> {
        return try {
            val response = api.getArtists(
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            
            val allArtists = response.response.artistsContainer?.indices
                ?.flatMap { it.artist ?: emptyList() } ?: emptyList()
            
            val matchingArtists = allArtists
                .filter { it.name.contains(query, ignoreCase = true) }
                .take(limit)
                .map { dto ->
                    Artist(
                        id = "${sourceType.name}_${sourceId}_${dto.id}",
                        name = dto.name,
                        coverArtUrl = buildCoverArtUrl(dto.coverArt),
                        sourceType = sourceType,
                        sourceId = sourceId,
                        albumCount = dto.albumCount ?: 0,
                        metadata = mapOf("subsonicId" to dto.id)
                    )
                }
            
            Result.success(matchingArtists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getArtistById(artistId: String): Result<Artist?> {
        // TODO: Implementar usando getArtist endpoint
        return Result.success(null)
    }
    
    override suspend fun getAllArtists(): Result<List<Artist>> {
        return try {
            val response = api.getArtists(
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            
            val artists = response.response.artistsContainer?.indices
                ?.flatMap { it.artist ?: emptyList() }
                ?.map { dto ->
                    Artist(
                        id = "${sourceType.name}_${sourceId}_${dto.id}",
                        name = dto.name,
                        coverArtUrl = buildCoverArtUrl(dto.coverArt),
                        sourceType = sourceType,
                        sourceId = sourceId,
                        albumCount = dto.albumCount ?: 0,
                        metadata = mapOf("subsonicId" to dto.id)
                    )
                } ?: emptyList()
            
            Result.success(artists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ===== ALBUMS =====
    
    override suspend fun searchAlbums(query: String, limit: Int): Result<List<Album>> {
        return try {
            val response = api.getAlbumList(
                type = "alphabeticalByName",
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            
            val albums = response.response.albumList?.album
                ?.filter { it.title.contains(query, ignoreCase = true) }
                ?.take(limit)
                ?.map { dto ->
                    Album(
                        id = "${sourceType.name}_${sourceId}_${dto.id}",
                        name = dto.title,
                        artistId = "${sourceType.name}_${sourceId}_${dto.artistId ?: ""}",
                        artistName = dto.artist ?: "Unknown",
                        coverArtUrl = buildCoverArtUrl(dto.coverArt),
                        sourceType = sourceType,
                        sourceId = sourceId,
                        year = dto.year,
                        songCount = dto.songCount ?: 0,
                        duration = 0L,
                        genre = dto.genre,
                        metadata = mapOf("subsonicId" to dto.id)
                    )
                } ?: emptyList()
            
            Result.success(albums)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getAlbumById(albumId: String): Result<Album?> {
        // TODO: Implementar usando getAlbum endpoint
        return Result.success(null)
    }
    
    override suspend fun getAlbumsByArtist(artistId: String): Result<List<Album>> {
        // TODO: Implementar usando getArtist endpoint que incluye álbumes
        return Result.success(emptyList())
    }
    
    override suspend fun getRecentAlbums(limit: Int): Result<List<Album>> {
        return try {
            val response = api.getRecentlyAdded(
                type = "newest",
                u = server.username,
                t = server.token,
                s = server.salt,
                v = "1.16.1",
                c = "NeoSynth"
            )
            
            val albums = response.response.albumList2?.album
                ?.take(limit)
                ?.map { dto ->
                    Album(
                        id = "${sourceType.name}_${sourceId}_${dto.id}",
                        name = dto.title,
                        artistId = "${sourceType.name}_${sourceId}_${dto.artistId ?: ""}",
                        artistName = dto.artist ?: "Unknown",
                        coverArtUrl = buildCoverArtUrl(dto.coverArt),
                        sourceType = sourceType,
                        sourceId = sourceId,
                        year = dto.year,
                        songCount = dto.songCount ?: 0,
                        metadata = mapOf("subsonicId" to dto.id)
                    )
                } ?: emptyList()
            
            Result.success(albums)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ===== PLAYLISTS =====
    
    override suspend fun getPlaylists(): Result<List<Playlist>> {
        return try {
            val response = api.getPlaylists(
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            
            val playlists = response.response.playlistsContainer?.playlist?.map { dto ->
                Playlist(
                    id = "${sourceType.name}_${sourceId}_${dto.id}",
                    name = dto.name,
                    description = null,
                    coverArtUrl = buildCoverArtUrl(dto.coverArt),
                    sourceType = sourceType,
                    sourceId = sourceId,
                    songCount = dto.songCount ?: 0,
                    duration = (dto.duration ?: 0) * 1000L,
                    isPublic = dto.public ?: false,
                    owner = dto.owner,
                    metadata = mapOf("subsonicId" to dto.id)
                )
            } ?: emptyList()
            
            Result.success(playlists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getPlaylistById(playlistId: String): Result<Playlist?> {
        // TODO: Implementar usando getPlaylist endpoint
        return Result.success(null)
    }
    
    override suspend fun createPlaylist(name: String, description: String?): Result<Playlist> {
        // TODO: Implementar usando createPlaylist endpoint
        return Result.failure(NotImplementedError("Create playlist not implemented"))
    }
    
    override suspend fun addSongToPlaylist(playlistId: String, songId: String): Result<Boolean> {
        // TODO: Implementar usando updatePlaylist endpoint
        return Result.failure(NotImplementedError("Add song to playlist not implemented"))
    }
    
    override suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Result<Boolean> {
        // TODO: Implementar usando updatePlaylist endpoint
        return Result.failure(NotImplementedError("Remove song from playlist not implemented"))
    }
    
    // ===== COVER ART =====
    
    override suspend fun getCoverArtUrl(coverArtId: String, size: Int?): Result<String?> {
        val url = buildCoverArtUrl(coverArtId, size)
        return Result.success(url)
    }
    
    // ===== HELPERS =====
    
    private fun buildCoverArtUrl(coverArtId: String?, size: Int? = null): String? {
        if (coverArtId == null) return null
        return buildString {
            append("$baseUrl/rest/getCoverArt?")
            append("id=$coverArtId&")
            if (size != null) append("size=$size&")
            append(authParams)
        }
    }
    
    private fun extractSubsonicId(compositeId: String): String {
        // Formato: "SUBSONIC_serverID_subsonicID"
        return compositeId.removePrefix("${sourceType.name}_${sourceId}_")
    }
}
