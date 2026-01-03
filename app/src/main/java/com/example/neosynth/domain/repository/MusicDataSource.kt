package com.example.neosynth.domain.repository

import com.example.neosynth.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz base para cualquier fuente de música
 * Cada implementación (Subsonic, Local Files, etc) debe implementar esta interfaz
 */
interface MusicDataSource {
    
    /**
     * Tipo de fuente que implementa este data source
     */
    val sourceType: MusicSourceType
    
    /**
     * ID único de esta instancia de fuente (ej: ID del servidor)
     */
    val sourceId: String
    
    /**
     * Nombre descriptivo de la fuente
     */
    val sourceName: String
    
    /**
     * Si la fuente está disponible/conectada
     */
    suspend fun isAvailable(): Boolean
    
    // ===== SONGS =====
    
    /**
     * Buscar canciones
     */
    suspend fun searchSongs(query: String, limit: Int = 50): Result<List<Song>>
    
    /**
     * Obtener canción por ID
     */
    suspend fun getSongById(songId: String): Result<Song?>
    
    /**
     * Obtener canciones de un álbum
     */
    suspend fun getSongsByAlbum(albumId: String): Result<List<Song>>
    
    /**
     * Obtener canciones de un artista
     */
    suspend fun getSongsByArtist(artistId: String): Result<List<Song>>
    
    /**
     * Obtener URL de streaming para una canción
     * @param quality Configuración de calidad opcional
     */
    suspend fun getStreamUrl(songId: String, quality: StreamQuality? = null): Result<String>
    
    /**
     * Obtener URL de descarga para una canción
     * @param quality Configuración de calidad opcional
     */
    suspend fun getDownloadUrl(songId: String, quality: DownloadQuality? = null): Result<String>
    
    // ===== ARTISTS =====
    
    /**
     * Buscar artistas
     */
    suspend fun searchArtists(query: String, limit: Int = 50): Result<List<Artist>>
    
    /**
     * Obtener artista por ID
     */
    suspend fun getArtistById(artistId: String): Result<Artist?>
    
    /**
     * Obtener todos los artistas
     */
    suspend fun getAllArtists(): Result<List<Artist>>
    
    // ===== ALBUMS =====
    
    /**
     * Buscar álbumes
     */
    suspend fun searchAlbums(query: String, limit: Int = 50): Result<List<Album>>
    
    /**
     * Obtener álbum por ID
     */
    suspend fun getAlbumById(albumId: String): Result<Album?>
    
    /**
     * Obtener álbumes de un artista
     */
    suspend fun getAlbumsByArtist(artistId: String): Result<List<Album>>
    
    /**
     * Obtener álbumes recientes
     */
    suspend fun getRecentAlbums(limit: Int = 20): Result<List<Album>>
    
    // ===== PLAYLISTS =====
    
    /**
     * Obtener todas las playlists
     */
    suspend fun getPlaylists(): Result<List<Playlist>>
    
    /**
     * Obtener playlist por ID
     */
    suspend fun getPlaylistById(playlistId: String): Result<Playlist?>
    
    /**
     * Crear nueva playlist (si la fuente lo soporta)
     */
    suspend fun createPlaylist(name: String, description: String? = null): Result<Playlist>
    
    /**
     * Agregar canción a playlist (si la fuente lo soporta)
     */
    suspend fun addSongToPlaylist(playlistId: String, songId: String): Result<Boolean>
    
    /**
     * Eliminar canción de playlist (si la fuente lo soporta)
     */
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Result<Boolean>
    
    // ===== COVER ART =====
    
    /**
     * Obtener URL de cover art
     */
    suspend fun getCoverArtUrl(coverArtId: String, size: Int? = null): Result<String?>
}

/**
 * Enum para calidad de streaming (reutilizado de SettingsPreferences)
 */
enum class StreamQuality(val bitrate: Int, val format: String) {
    LOW(128, "mp3"),
    MEDIUM(192, "mp3"),
    HIGH(256, "mp3"),
    VERY_HIGH(320, "mp3"),
    LOSSLESS(0, "raw")
}

/**
 * Enum para calidad de descarga
 */
enum class DownloadQuality(val bitrate: Int, val format: String) {
    LOW(128, "mp3"),
    MEDIUM(192, "mp3"),
    HIGH(256, "mp3"),
    VERY_HIGH(320, "mp3"),
    LOSSLESS(0, "raw")
}
