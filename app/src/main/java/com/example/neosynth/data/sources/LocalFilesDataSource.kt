package com.example.neosynth.data.sources

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import com.example.neosynth.domain.repository.MusicDataSource
import com.example.neosynth.domain.repository.StreamQuality
import com.example.neosynth.domain.repository.DownloadQuality
import com.example.neosynth.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Implementación de MusicDataSource para archivos de música locales
 * 
 * NOTA: Esta es una implementación de ejemplo/template.
 * Falta implementar:
 * - Permisos de almacenamiento
 * - Cache de metadatos
 * - Observadores de cambios en archivos
 * - Manejo de covers embebidos
 * - Soporte para más formatos de tags
 */
class LocalFilesDataSource @Inject constructor(
    private val context: Context
) : MusicDataSource {
    
    override val sourceType = MusicSourceType.LOCAL_FILES
    override val sourceId = "device"
    override val sourceName = "Local Files"
    
    private val cachedSongs = mutableListOf<Song>()
    private var isScanned = false
    
    override suspend fun isAvailable(): Boolean {
        // TODO: Verificar permisos de almacenamiento
        return true
    }
    
    // ===== SONGS =====
    
    override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> {
        return try {
            if (!isScanned) scanLocalFiles()
            
            val results = cachedSongs
                .filter { 
                    it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
                }
                .take(limit)
            
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getSongById(songId: String): Result<Song?> {
        val song = cachedSongs.find { it.id == songId }
        return Result.success(song)
    }
    
    override suspend fun getSongsByAlbum(albumId: String): Result<List<Song>> {
        val songs = cachedSongs.filter { it.albumId == albumId }
        return Result.success(songs)
    }
    
    override suspend fun getSongsByArtist(artistId: String): Result<List<Song>> {
        val songs = cachedSongs.filter { it.artistId == artistId }
        return Result.success(songs)
    }
    
    override suspend fun getStreamUrl(songId: String, quality: StreamQuality?): Result<String> {
        // Para archivos locales, el URL es el path del archivo
        val song = cachedSongs.find { it.id == songId }
        return if (song != null) {
            val filePath = song.metadata["filePath"] ?: ""
            Result.success("file://$filePath")
        } else {
            Result.failure(IllegalArgumentException("Song not found: $songId"))
        }
    }
    
    override suspend fun getDownloadUrl(songId: String, quality: DownloadQuality?): Result<String> {
        // Los archivos locales ya están descargados
        return getStreamUrl(songId, null)
    }
    
    // ===== ARTISTS =====
    
    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> {
        if (!isScanned) scanLocalFiles()
        
        val artists = cachedSongs
            .groupBy { it.artist }
            .map { (artistName, songs) ->
                val artistId = generateArtistId(artistName)
                Artist(
                    id = artistId,
                    name = artistName,
                    coverArtUrl = songs.firstOrNull()?.coverArtUrl,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    albumCount = songs.map { it.album }.distinct().size,
                    metadata = emptyMap()
                )
            }
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(limit)
        
        return Result.success(artists)
    }
    
    override suspend fun getArtistById(artistId: String): Result<Artist?> {
        if (!isScanned) scanLocalFiles()
        
        val artistSongs = cachedSongs.filter { 
            generateArtistId(it.artist) == artistId 
        }
        
        if (artistSongs.isEmpty()) return Result.success(null)
        
        val artist = Artist(
            id = artistId,
            name = artistSongs.first().artist,
            coverArtUrl = artistSongs.firstOrNull()?.coverArtUrl,
            sourceType = sourceType,
            sourceId = sourceId,
            albumCount = artistSongs.map { it.album }.distinct().size,
            metadata = emptyMap()
        )
        
        return Result.success(artist)
    }
    
    override suspend fun getAllArtists(): Result<List<Artist>> {
        if (!isScanned) scanLocalFiles()
        
        val artists = cachedSongs
            .groupBy { it.artist }
            .map { (artistName, songs) ->
                Artist(
                    id = generateArtistId(artistName),
                    name = artistName,
                    coverArtUrl = songs.firstOrNull()?.coverArtUrl,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    albumCount = songs.map { it.album }.distinct().size,
                    metadata = emptyMap()
                )
            }
        
        return Result.success(artists)
    }
    
    // ===== ALBUMS =====
    
    override suspend fun searchAlbums(query: String, limit: Int): Result<List<Album>> {
        if (!isScanned) scanLocalFiles()
        
        val albums = cachedSongs
            .groupBy { it.album }
            .map { (albumName, songs) ->
                val albumId = generateAlbumId(albumName, songs.first().artist)
                Album(
                    id = albumId,
                    name = albumName,
                    artistId = generateArtistId(songs.first().artist),
                    artistName = songs.first().artist,
                    coverArtUrl = songs.firstOrNull()?.coverArtUrl,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    year = songs.firstOrNull()?.year,
                    songCount = songs.size,
                    duration = songs.sumOf { it.duration },
                    genre = songs.firstOrNull()?.genre,
                    metadata = emptyMap()
                )
            }
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(limit)
        
        return Result.success(albums)
    }
    
    override suspend fun getAlbumById(albumId: String): Result<Album?> {
        // TODO: Implementar
        return Result.success(null)
    }
    
    override suspend fun getAlbumsByArtist(artistId: String): Result<List<Album>> {
        if (!isScanned) scanLocalFiles()
        
        val artistSongs = cachedSongs.filter { 
            generateArtistId(it.artist) == artistId 
        }
        
        val albums = artistSongs
            .groupBy { it.album }
            .map { (albumName, songs) ->
                Album(
                    id = generateAlbumId(albumName, songs.first().artist),
                    name = albumName,
                    artistId = artistId,
                    artistName = songs.first().artist,
                    coverArtUrl = songs.firstOrNull()?.coverArtUrl,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    year = songs.firstOrNull()?.year,
                    songCount = songs.size,
                    duration = songs.sumOf { it.duration },
                    genre = songs.firstOrNull()?.genre,
                    metadata = emptyMap()
                )
            }
        
        return Result.success(albums)
    }
    
    override suspend fun getRecentAlbums(limit: Int): Result<List<Album>> {
        // TODO: Implementar usando fecha de modificación del archivo
        return Result.success(emptyList())
    }
    
    // ===== PLAYLISTS =====
    
    override suspend fun getPlaylists(): Result<List<Playlist>> {
        // Los archivos locales no tienen playlists remotas
        return Result.success(emptyList())
    }
    
    override suspend fun getPlaylistById(playlistId: String): Result<Playlist?> {
        return Result.success(null)
    }
    
    override suspend fun createPlaylist(name: String, description: String?): Result<Playlist> {
        return Result.failure(NotImplementedError("Local files don't support playlists"))
    }
    
    override suspend fun addSongToPlaylist(playlistId: String, songId: String): Result<Boolean> {
        return Result.failure(NotImplementedError("Local files don't support playlists"))
    }
    
    override suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Result<Boolean> {
        return Result.failure(NotImplementedError("Local files don't support playlists"))
    }
    
    // ===== COVER ART =====
    
    override suspend fun getCoverArtUrl(coverArtId: String, size: Int?): Result<String?> {
        // TODO: Extraer cover art embebido del archivo
        return Result.success(null)
    }
    
    // ===== PRIVATE HELPERS =====
    
    /**
     * Escanea todos los archivos de música en el dispositivo
     * usando MediaStore
     */
    private suspend fun scanLocalFiles() = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA, // File path
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.ALBUM_ID
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            
            cachedSongs.clear()
            
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn)
                val year = cursor.getInt(yearColumn)
                val track = cursor.getInt(trackColumn) % 1000 // Remove disc number
                val albumId = cursor.getLong(albumIdColumn)
                
                val songId = "${sourceType.name}_${sourceId}_$mediaId"
                val artistId = generateArtistId(artist)
                val compositeAlbumId = generateAlbumId(album, artist)
                
                // Intentar extraer cover art del álbum
                val coverArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                
                val song = Song(
                    id = songId,
                    title = title,
                    artist = artist,
                    artistId = artistId,
                    album = album,
                    albumId = compositeAlbumId,
                    duration = duration,
                    coverArtUrl = coverArtUri.toString(),
                    sourceType = sourceType,
                    sourceId = sourceId,
                    year = if (year > 0) year else null,
                    trackNumber = if (track > 0) track else null,
                    metadata = mapOf(
                        "filePath" to filePath,
                        "mediaStoreId" to mediaId.toString()
                    )
                )
                
                cachedSongs.add(song)
            }
            
            isScanned = true
        }
    }
    
    /**
     * Genera un ID único para un artista basado en su nombre
     */
    private fun generateArtistId(artistName: String): String {
        val normalized = artistName.lowercase().replace(" ", "_")
        return "${sourceType.name}_${sourceId}_artist_$normalized"
    }
    
    /**
     * Genera un ID único para un álbum basado en nombre y artista
     */
    private fun generateAlbumId(albumName: String, artistName: String): String {
        val normalized = "${albumName}_${artistName}".lowercase().replace(" ", "_")
        return "${sourceType.name}_${sourceId}_album_$normalized"
    }
}
