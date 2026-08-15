package com.example.neosynth.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.PendingSyncActionEntity
import com.example.neosynth.data.local.entities.PlaylistSongCrossRef
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.local.entities.PlaylistWithSongs
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.data.worker.PlaylistSyncWorker
import com.example.neosynth.player.MusicController
import com.example.neosynth.domain.model.Song
import com.example.neosynth.domain.model.MusicSourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    val musicController: MusicController,
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val currentSong = musicController.currentMediaItem

    // Estado para filtro de playlist seleccionada
    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    // Estados de filtrado y ordenamiento
    private val _activeFilterCategory = MutableStateFlow(FilterCategory.SONGS)
    val activeFilterCategory: StateFlow<FilterCategory> = _activeFilterCategory.asStateFlow()

    private val _activeSortOrder = MutableStateFlow(SortOrder.TITLE)
    val activeSortOrder: StateFlow<SortOrder> = _activeSortOrder.asStateFlow()

    fun setFilterCategory(category: FilterCategory) {
        _activeFilterCategory.value = category
        if (category != FilterCategory.PLAYLISTS) {
            _selectedPlaylistId.value = null
        }
    }

    fun setSortOrder(order: SortOrder) {
        _activeSortOrder.value = order
    }
    val activeServer = serverDao.getActiveServerFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Flow de playlists (sincronizadas y descargadas)
    val allPlaylists: StateFlow<List<PlaylistWithSongs>> = serverDao.getActiveServerFlow()
        .flatMapLatest { server ->
            if (server != null) {
                musicRepository.getPlaylistsWithSongs(server.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Estado de canciones agrupadas (UI State)
    val groupedSongs: StateFlow<Map<Char, List<SongEntity>>> = combine(
        musicRepository.getDownloadedSongs(),
        _selectedPlaylistId,
        allPlaylists,
        _activeFilterCategory,
        _activeSortOrder
    ) { allSongs, playlistId, playlists, category, sortOrder ->
        var filteredSongs = if (playlistId != null) {
            val selectedPlaylist = playlists.find { it.playlist.id == playlistId }
            selectedPlaylist?.songs ?: emptyList()
        } else {
            allSongs
        }

        if (category == FilterCategory.FAVORITES) {
            filteredSongs = filteredSongs.filter { it.isFavorite }
        }

        val sortedList = when (sortOrder) {
            SortOrder.ASCENDING -> {
                filteredSongs.sortedBy { it.title.lowercase() }
            }
            SortOrder.DESCENDING -> {
                filteredSongs.sortedByDescending { it.title.lowercase() }
            }
            SortOrder.TITLE -> {
                filteredSongs.sortedWith(compareBy<SongEntity> {
                    val firstChar = it.title.firstOrNull() ?: ' '
                    !firstChar.isLetter()
                }.thenBy { it.title.lowercase() })
            }
            SortOrder.ARTIST -> {
                filteredSongs.sortedWith(compareBy<SongEntity> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            }
            SortOrder.ALBUM -> {
                filteredSongs.sortedWith(compareBy<SongEntity> { it.album.lowercase() }.thenBy { it.trackNumber ?: 0 }.thenBy { it.title.lowercase() })
            }
            SortOrder.RECENT -> {
                filteredSongs.sortedByDescending { it.downloadedAt ?: 0L }
            }
        }

        when {
            sortOrder == SortOrder.RECENT -> {
                sortedList.groupBy { '↓' }
            }
            category == FilterCategory.ALBUMS -> {
                sortedList.groupBy { song ->
                    val firstChar = song.album.firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
            }
            category == FilterCategory.ARTISTS -> {
                sortedList.groupBy { song ->
                    val firstChar = song.artist.firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
            }
            else -> {
                sortedList.groupBy { song ->
                    val firstChar = song.title.firstOrNull()?.uppercaseChar() ?: '#'
                    if (firstChar.isLetter()) firstChar else '#'
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Reproducir lista completa
    fun playAll(songs: List<SongEntity>, startIndex: Int = 0) {
        val mediaItems = songs.map { it.toMediaItem() }
        musicController.playQueue(mediaItems, startIndex)
    }

    // Reproducir todas en aleatorio
    fun shufflePlayAll(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        
        // Desactivar modo aleatorio del controlador si está activo para evitar bucles de reordenamiento de ExoPlayer
        if (musicController.shuffleModeEnabled.value) {
            musicController.toggleShuffle()
        }
        
        // Mezclar la lista de canciones en memoria
        val shuffledSongs = songs.shuffled()
        
        // Reproducir la lista mezclada desde el inicio (índice 0)
        playAll(shuffledSongs, 0)
    }

    // 3. Reproducir solo la selección múltiple (Bug 2 Fix)
    fun playSelected(selectedIds: Set<String>, allSongs: List<SongEntity>) {
        if (selectedIds.isEmpty()) return

        val songsMap = allSongs.associateBy { it.id }
        val selectedMediaItems = selectedIds
            .mapNotNull { id -> songsMap[id] }
            .map { it.toMediaItem() }

        musicController.playQueue(selectedMediaItems, 0)
    }

    // 4. Mapeador interno de Entity a MediaItem (Media3)
    private fun SongEntity.toMediaItem(): MediaItem {
        var bitRate = 0
        var format = "MP3"
        
        try {
            this.metadata?.let { metadataStr ->
                val json = org.json.JSONObject(metadataStr)
                if (json.has("bitRate")) bitRate = json.getInt("bitRate")
                if (json.has("format")) format = json.getString("format")
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadViewModel", "Error parsing metadata for offline song", e)
        }

        val cleanPath = this.path.removePrefix("file://")
        val mediaUri = if (this.path.startsWith("/") || this.path.startsWith("file:/")) {
            android.net.Uri.fromFile(java.io.File(cleanPath))
        } else {
            android.net.Uri.parse(this.path)
        }

        val artworkUri = if (!this.imageUrl.isNullOrBlank()) {
            val cleanImgPath = this.imageUrl.removePrefix("file://")
            if (this.imageUrl.startsWith("/") || this.imageUrl.startsWith("file:/")) {
                android.net.Uri.fromFile(java.io.File(cleanImgPath))
            } else {
                android.net.Uri.parse(this.imageUrl)
            }
        } else null

        return MediaItem.Builder()
            .setMediaId(this.id)
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.title)
                    .setArtist(this.artist)
                    .setAlbumTitle(this.album)
                    .setArtworkUri(artworkUri)
                    .setExtras(
                        android.os.Bundle().apply {
                            putString("path", this@toMediaItem.path)
                            putString("coverArtId", this@toMediaItem.imageUrl)
                            putLong("duration", this@toMediaItem.duration)
                            putBoolean("isDownloaded", true)
                            putInt("bitRate", bitRate)
                            putString("suffix", format)
                        }
                    )
                    .build()
            )
            .build()
    }

    // 5. Reproducir la cola actual (sin cambiar la cola)
    fun playCurrentQueue() {
        // Simplemente reanuda la reproducción de la cola actual
        musicController.play()
    }

    // 6. Agregar canciones a la cola de reproducción
    fun addToQueue(songs: List<SongEntity>) {
        val mediaItems = songs.map { it.toMediaItem() }
        musicController.addToQueue(mediaItems)
    }

    fun playNext(songs: List<SongEntity>) {
        val mediaItems = songs.map { it.toMediaItem() }
        musicController.addAfterCurrent(mediaItems)
    }

    fun playNextSelected(songIds: Set<String>, allSongs: List<SongEntity>) {
        val selectedSongs = allSongs.filter { it.id in songIds }
        val mediaItems = selectedSongs.map { it.toMediaItem() }
        musicController.addAfterCurrent(mediaItems)
    }

    fun addToQueueSelected(songIds: Set<String>, allSongs: List<SongEntity>) {
        val selectedSongs = allSongs.filter { it.id in songIds }
        val mediaItems = selectedSongs.map { it.toMediaItem() }
        musicController.addToQueue(mediaItems)
    }

    // 7. Eliminar canciones seleccionadas del almacenamiento local
    fun deleteSelectedSongs(songIds: Set<String>) {
        viewModelScope.launch {
            songIds.forEach { songId ->
                try {
                    // Obtener la canción de la base de datos
                    val song = musicRepository.getSongById(songId)
                    if (song != null) {
                        // Eliminar archivo físico
                        val file = File(song.path)
                        if (file.exists()) {
                            file.delete()
                        }
                        // Eliminar de la base de datos
                        musicRepository.deleteSong(songId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 8. Agregar a favoritos
    fun addToFavorites(songIds: Set<String>) {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            
            // Add to local database
            songIds.forEach { songId ->
                try {
                    musicRepository.addToFavorites(songId)
                } catch (e: Exception) {
                    android.util.Log.e("DownloadViewModel", "Failed to add to favorites: $songId", e)
                    e.printStackTrace()
                }
            }
            
            // Sync with Navidrome server en batch
            if (songIds.isNotEmpty()) {
                try {
                    api.star(
                        id = songIds.toList(), // ← Batch operation
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                    android.util.Log.d("DownloadViewModel", "Starred ${songIds.size} songs on server")
                } catch (e: Exception) {
                    android.util.Log.e("DownloadViewModel", "Failed to star songs on server", e)
                    // Continue even if server sync fails
                }
            }
        }
    }
    
    // 9. Reproducir playlist descargada
    fun playPlaylist(playlistWithSongs: PlaylistWithSongs) {
        // Solo reproducir las canciones que SÍ estén descargadas (path no vacío)
        val downloadedSongs = playlistWithSongs.songs.filter { 
            it.isDownloaded && it.path.isNotEmpty() 
        }
        
        if (downloadedSongs.isEmpty()) {
            android.util.Log.w("DownloadViewModel", "No downloaded songs in playlist '${playlistWithSongs.playlist.name}'")
            // TODO: Mostrar snackbar de que no hay canciones descargadas aún
            return
        }
        
        val mediaItems = downloadedSongs.map { it.toMediaItem() }
        musicController.playQueue(mediaItems, 0)
    }
    
    // 10. Eliminar playlist descargada
    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                musicRepository.deletePlaylist(playlistId)
                // Si la playlist eliminada estaba seleccionada, limpiar el filtro
                if (_selectedPlaylistId.value == playlistId) {
                    _selectedPlaylistId.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // 11. Seleccionar playlist para filtrar canciones
    fun selectPlaylist(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
        if (playlistId != null) {
            _activeFilterCategory.value = FilterCategory.SONGS
        }
    }
    
    // 12. Limpiar filtro de playlist
    fun clearPlaylistFilter() {
        _selectedPlaylistId.value = null
    }
    
    // 13. Agregar canciones seleccionadas a una playlist (local-first, syncs when online)
    fun addSongsToPlaylist(songIds: Set<String>, playlistId: String) {
        viewModelScope.launch {
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                
                // Get current max position in playlist to append at end
                val currentPlaylist = allPlaylists.value.find { it.playlist.id == playlistId }
                val currentSongCount = currentPlaylist?.songs?.size ?: 0
                
                // 1. Insert cross-refs locally
                val crossRefs = songIds.mapIndexed { index, songId ->
                    PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = songId,
                        position = currentSongCount + index
                    )
                }
                musicRepository.insertPlaylistSongCrossRefs(crossRefs)
                
                // 2. Queue sync action
                val payloadObj = JSONObject().apply {
                    put("playlistId", playlistId)
                    val songArray = JSONArray()
                    songIds.forEach { songArray.put(it) }
                    put("songIds", songArray)
                }
                val pendingAction = PendingSyncActionEntity(
                    serverId = server.id,
                    actionType = "ADD_SONG",
                    payload = payloadObj.toString()
                )
                musicRepository.insertPendingSyncAction(pendingAction)
                
                // 3. Best-effort immediate sync with server
                try {
                    api.addToPlaylist(
                        playlistId = playlistId,
                        songIds = songIds.toList(),
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                } catch (e: Exception) {
                    android.util.Log.d("DownloadsViewModel", "Offline, ADD_SONG queued for sync")
                }
                
                // 4. Trigger WorkManager for background sync
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val syncRequest = OneTimeWorkRequestBuilder<PlaylistSyncWorker>()
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(appContext).enqueue(syncRequest)
                
                android.util.Log.d("DownloadsViewModel", "Added ${songIds.size} songs to playlist $playlistId")
            } catch (e: Exception) {
                android.util.Log.e("DownloadsViewModel", "Failed to add songs to playlist", e)
            }
        }
    }
}

fun SongEntity.toDomainModel(): Song {
    return Song(
        id = this.id,
        title = this.title,
        artist = this.artist,
        artistId = this.artistID,
        album = this.album,
        albumId = this.albumID,
        duration = this.duration,
        coverArtUrl = this.imageUrl,
        sourceType = try { MusicSourceType.valueOf(this.sourceType) } catch (e: Exception) { MusicSourceType.SUBSONIC },
        sourceId = this.sourceId
    )
}

enum class SortOrder {
    ASCENDING, DESCENDING, TITLE, ARTIST, ALBUM, RECENT
}

enum class FilterCategory {
    SONGS, ALBUMS, ARTISTS, PLAYLISTS, FAVORITES
}