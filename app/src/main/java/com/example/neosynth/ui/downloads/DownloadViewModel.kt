package com.example.neosynth.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.core.net.toUri
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.local.entities.PlaylistWithSongs
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.player.MusicController
import com.example.neosynth.domain.model.Song
import com.example.neosynth.domain.model.MusicSourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicController: MusicController,
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    // Estado para filtro de playlist seleccionada
    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    // Flow de playlists descargadas (necesario antes para usarlo en el filtro)
    // Solo mostrar playlists que tengan al menos 1 canción descargada
    val downloadedPlaylists: StateFlow<List<PlaylistWithSongs>> = serverDao.getActiveServerFlow()
        .flatMapLatest { server ->
            if (server != null) {
                musicRepository.getPlaylistsWithSongs(server.id)
            } else {
                flowOf(emptyList())
            }
        }
        .map { playlists ->
            // Filtrar solo playlists con al menos 1 canción descargada
            playlists.filter { playlistWithSongs ->
                playlistWithSongs.songs.any { it.isDownloaded && it.path.isNotEmpty() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Estado de canciones agrupadas (UI State) - Solo canciones descargadas
    // Ahora puede filtrar por playlist si hay una seleccionada
    val groupedSongs: StateFlow<Map<Char, List<SongEntity>>> = combine(
        musicRepository.getDownloadedSongs(),
        _selectedPlaylistId,
        downloadedPlaylists
    ) { allSongs, playlistId, playlists ->
        val filteredSongs = if (playlistId != null) {
            // Encontrar la playlist seleccionada y mostrar TODAS sus canciones (descargadas o no)
            val selectedPlaylist = playlists.find { it.playlist.id == playlistId }
            selectedPlaylist?.songs ?: emptyList()
        } else {
            // Sin playlist seleccionada, mostrar solo canciones descargadas
            allSongs
        }
        
        val sortedList = filteredSongs.sortedWith(compareBy<SongEntity> {
            val firstChar = it.title.firstOrNull() ?: ' '
            !firstChar.isLetter()
        }.thenBy { it.title.lowercase() })

        sortedList.groupBy { song ->
            val firstChar = song.title.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar else '#'
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Reproducir lista completa (o desde un índice específico)
    fun playAll(songs: List<SongEntity>, startIndex: Int = 0) {
        val mediaItems = songs.map { it.toMediaItem() }
        musicController.playQueue(mediaItems, startIndex)
    }

    // 3. Reproducir solo la selección múltiple
    fun playSelected(selectedIds: Set<String>, allSongs: List<SongEntity>) {
        if (selectedIds.isEmpty()) return

        // Filtramos las canciones que coincidan con los IDs seleccionados
        val selectedMediaItems = allSongs
            .filter { selectedIds.contains(it.id) }
            .map { it.toMediaItem() }

        musicController.playQueue(selectedMediaItems, 0)
    }

    // 4. Mapeador interno de Entity a MediaItem (Media3)
    private fun SongEntity.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(this.id)
            .setUri(this.path.toUri()) // Usamos el path local porque es "Downloads"
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.title)
                    .setArtist(this.artist)
                    .setAlbumTitle(this.album)
                    .setArtworkUri(this.imageUrl?.toUri())
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
            // TODO: Mostrar mensaje de que no hay canciones descargadas aún
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
    }
    
    // 12. Limpiar filtro de playlist
    fun clearPlaylistFilter() {
        _selectedPlaylistId.value = null
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