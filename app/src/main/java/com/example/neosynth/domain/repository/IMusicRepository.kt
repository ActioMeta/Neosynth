package com.example.neosynth.domain.repository

import com.example.neosynth.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository principal que gestiona múltiples fuentes de música
 * Actúa como punto de acceso único para toda la app
 */
interface IMusicRepository {
    
    /**
     * Obtener todas las fuentes de música registradas
     */
    fun getAllSources(): Flow<List<MusicDataSource>>
    
    /**
     * Obtener fuente activa actual
     */
    fun getActiveSource(): Flow<MusicDataSource?>
    
    /**
     * Establecer fuente activa
     */
    suspend fun setActiveSource(sourceId: String)
    
    /**
     * Registrar una nueva fuente de música
     */
    suspend fun registerSource(source: MusicDataSource)
    
    /**
     * Eliminar una fuente
     */
    suspend fun removeSource(sourceId: String)
    
    // ===== BÚSQUEDA GLOBAL (todas las fuentes) =====
    
    /**
     * Buscar en todas las fuentes disponibles
     */
    suspend fun searchAll(query: String): Result<SearchResults>
    
    /**
     * Buscar solo en la fuente activa
     */
    suspend fun searchInActiveSource(query: String): Result<SearchResults>
    
    // ===== CANCIONES =====
    
    /**
     * Obtener todas las canciones descargadas (local cache)
     */
    fun getDownloadedSongs(): Flow<List<Song>>
    
    /**
     * Obtener canción por ID (busca en cache local primero)
     */
    suspend fun getSongById(songId: String): Song?
    
    /**
     * Descargar canción
     */
    suspend fun downloadSong(song: Song, quality: DownloadQuality): Result<String>
    
    /**
     * Eliminar canción descargada
     */
    suspend fun deleteDownloadedSong(songId: String)
    
    /**
     * Eliminar todas las descargas
     */
    suspend fun deleteAllDownloads()
    
    // ===== PLAYLISTS =====
    
    /**
     * Obtener playlists de todas las fuentes
     */
    fun getAllPlaylists(): Flow<List<Playlist>>
    
    /**
     * Obtener playlists de la fuente activa
     */
    fun getActiveSourcePlaylists(): Flow<List<Playlist>>
    
    /**
     * Obtener playlists locales (creadas en la app)
     */
    fun getLocalPlaylists(): Flow<List<Playlist>>
    
    /**
     * Crear playlist local
     */
    suspend fun createLocalPlaylist(name: String, description: String? = null): Playlist
    
    /**
     * Agregar canción a playlist local
     */
    suspend fun addSongToLocalPlaylist(playlistId: String, song: Song)
    
    // ===== FAVORITOS (local) =====
    
    /**
     * Obtener canciones favoritas
     */
    fun getFavoriteSongs(): Flow<List<Song>>
    
    /**
     * Agregar a favoritos
     */
    suspend fun addToFavorites(songId: String)
    
    /**
     * Quitar de favoritos
     */
    suspend fun removeFromFavorites(songId: String)
    
    /**
     * Verificar si es favorito
     */
    suspend fun isFavorite(songId: String): Boolean
}

/**
 * Resultado de búsqueda que puede venir de múltiples fuentes
 */
data class SearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val sourceType: MusicSourceType? = null
)
