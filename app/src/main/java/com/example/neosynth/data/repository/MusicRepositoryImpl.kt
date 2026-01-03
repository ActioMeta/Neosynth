package com.example.neosynth.data.repository

import com.example.neosynth.domain.repository.IMusicRepository
import com.example.neosynth.domain.repository.MusicDataSource
import com.example.neosynth.domain.repository.StreamQuality
import com.example.neosynth.domain.repository.DownloadQuality
import com.example.neosynth.domain.repository.SearchResults
import com.example.neosynth.domain.model.*
import com.example.neosynth.data.local.MusicDao
import com.example.neosynth.data.mappers.toDomain
import com.example.neosynth.data.mappers.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio que gestiona múltiples fuentes de música
 */
@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val musicDao: MusicDao
) : IMusicRepository {
    
    // Registro de fuentes disponibles
    private val registeredSources = mutableMapOf<String, MusicDataSource>()
    private val _sources = MutableStateFlow<List<MusicDataSource>>(emptyList())
    private var activeSourceId: String? = null
    private val _activeSource = MutableStateFlow<MusicDataSource?>(null)
    
    // ===== SOURCE MANAGEMENT =====
    
    override fun getAllSources(): Flow<List<MusicDataSource>> {
        return _sources.asStateFlow()
    }
    
    override fun getActiveSource(): Flow<MusicDataSource?> {
        return _activeSource.asStateFlow()
    }
    
    override suspend fun setActiveSource(sourceId: String) {
        if (registeredSources.containsKey(sourceId)) {
            activeSourceId = sourceId
            _activeSource.value = registeredSources[sourceId]
        }
    }
    
    override suspend fun registerSource(source: MusicDataSource) {
        registeredSources[source.sourceId] = source
        _sources.value = registeredSources.values.toList()
        // Si es la primera fuente, establecerla como activa
        if (activeSourceId == null) {
            setActiveSource(source.sourceId)
        }
    }
    
    override suspend fun removeSource(sourceId: String) {
        registeredSources.remove(sourceId)
        _sources.value = registeredSources.values.toList()
        // Si se removió la fuente activa, seleccionar otra
        if (activeSourceId == sourceId) {
            activeSourceId = registeredSources.keys.firstOrNull()
            _activeSource.value = activeSourceId?.let { registeredSources[it] }
        }
    }
    
    // ===== SEARCH =====
    
    override suspend fun searchAll(query: String): Result<SearchResults> {
        val songs = mutableListOf<Song>()
        val artists = mutableListOf<Artist>()
        val albums = mutableListOf<Album>()
        
        // Buscar en todas las fuentes disponibles
        registeredSources.values.forEach { source ->
            source.searchSongs(query, limit = 20).getOrNull()?.let { songs.addAll(it) }
            source.searchArtists(query, limit = 20).getOrNull()?.let { artists.addAll(it) }
            source.searchAlbums(query, limit = 20).getOrNull()?.let { albums.addAll(it) }
        }
        
        return Result.success(SearchResults(songs, artists, albums))
    }
    
    override suspend fun searchInActiveSource(query: String): Result<SearchResults> {
        val source = _activeSource.value 
            ?: return Result.failure(IllegalStateException("No active source"))
        
        val songs = source.searchSongs(query, limit = 50).getOrElse { emptyList() }
        val artists = source.searchArtists(query, limit = 20).getOrElse { emptyList() }
        val albums = source.searchAlbums(query, limit = 20).getOrElse { emptyList() }
        
        return Result.success(SearchResults(songs, artists, albums))
    }
    
    // ===== SONGS =====
    
    override suspend fun getSongById(songId: String): Song? {
        // Primero intentar desde la base de datos local
        val localSong = musicDao.getSongById(songId)?.toDomain()
        if (localSong != null) {
            return localSong
        }
        
        // Si no está en local, buscar en las fuentes
        registeredSources.values.forEach { source ->
            val song = source.getSongById(songId).getOrNull()
            if (song != null) {
                return song
            }
        }
        
        return null
    }
    
    // ===== PLAYLISTS =====
    
    override fun getAllPlaylists(): Flow<List<Playlist>> {
        // TODO: Combinar playlists de fuente activa + locales
        return getActiveSourcePlaylists()
    }
    
    override fun getActiveSourcePlaylists(): Flow<List<Playlist>> {
        // TODO: Implementar con Flow reactivo
        return MutableStateFlow(emptyList<Playlist>())
    }
    
    override fun getLocalPlaylists(): Flow<List<Playlist>> {
        // TODO: Implementar playlists locales
        return MutableStateFlow(emptyList<Playlist>())
    }
    
    override suspend fun createLocalPlaylist(name: String, description: String?): Playlist {
        // TODO: Implementar creación de playlists locales
        throw NotImplementedError("Local playlists not implemented")
    }
    
    override suspend fun addSongToLocalPlaylist(playlistId: String, song: Song) {
        // TODO: Implementar agregar canción a playlist local
        throw NotImplementedError("Local playlists not implemented")
    }
    
    // ===== DOWNLOADS =====
    
    override fun getDownloadedSongs(): Flow<List<Song>> {
        return musicDao.getDownloadedSongs()
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override suspend fun downloadSong(song: Song, quality: DownloadQuality): Result<String> {
        // TODO: Implementar descarga de canciones
        throw NotImplementedError("Download not implemented")
    }
    
    override suspend fun deleteDownloadedSong(songId: String) {
        musicDao.deleteSong(songId)
    }
    
    override suspend fun deleteAllDownloads() {
        musicDao.deleteAllDownloadedSongs()
    }
    
    // ===== FAVORITES =====
    
    override fun getFavoriteSongs(): Flow<List<Song>> {
        return musicDao.getFavoriteSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun addToFavorites(songId: String) {
        musicDao.addToFavorites(songId)
    }
    
    override suspend fun removeFromFavorites(songId: String) {
        musicDao.removeFromFavorites(songId)
    }
    
    override suspend fun isFavorite(songId: String): Boolean {
        return musicDao.isFavorite(songId) ?: false
    }
}
