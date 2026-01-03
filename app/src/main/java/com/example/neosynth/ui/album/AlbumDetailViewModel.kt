package com.example.neosynth.ui.album

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.AlbumDetails
import com.example.neosynth.data.remote.responses.PlaylistDto
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.player.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val musicRepository: MusicRepository,
    val musicController: MusicController,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _album = MutableStateFlow<AlbumDetails?>(null)
    val album: StateFlow<AlbumDetails?> = _album

    private val _songs = MutableStateFlow<List<SongDto>>(emptyList())
    val songs: StateFlow<List<SongDto>> = _songs

    private val _playlists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistDto>> = _playlists

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var cachedServer: ServerEntity? = null

    val downloadedSongIds = musicRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                cachedServer = server
                urlInterceptor.setBaseUrl(server.url)

                val response = api.getAlbum(
                    albumId = albumId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )

                _album.value = response.response.albumDetails
                _songs.value = response.response.albumDetails?.song ?: emptyList()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playSong(song: SongDto) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val baseUrl = server.url.removeSuffix("/")

            val mediaItems = _songs.value.map { s ->
                val streamUrl = "$baseUrl/rest/stream?id=${s.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
                val coverUrl = buildCoverArtUrl(server, s.coverArt ?: _album.value?.coverArt)

                MediaItem.Builder()
                    .setMediaId(s.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(_album.value?.name ?: s.album)
                            .setArtworkUri(coverUrl?.toUri())
                            .build()
                    )
                    .build()
            }

            val startIndex = _songs.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    fun playAlbum() {
        val firstSong = _songs.value.firstOrNull() ?: return
        playSong(firstSong)
    }

    fun shufflePlay() {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val baseUrl = server.url.removeSuffix("/")

            val shuffledSongs = _songs.value.shuffled()
            if (shuffledSongs.isEmpty()) return@launch

            val mediaItems = shuffledSongs.map { s ->
                val streamUrl = "$baseUrl/rest/stream?id=${s.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
                val coverUrl = buildCoverArtUrl(server, s.coverArt ?: _album.value?.coverArt)

                MediaItem.Builder()
                    .setMediaId(s.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(_album.value?.name ?: s.album)
                            .setArtworkUri(coverUrl?.toUri())
                            .build()
                    )
                    .build()
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun getCoverUrl(coverArt: String?): String? {
        val server = cachedServer ?: return null
        return buildCoverArtUrl(server, coverArt)
    }

    fun downloadSong(song: SongDto) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch

            if (song.id in downloadedSongIds.value) return@launch

            val inputData = androidx.work.Data.Builder()
                .putString("songId", song.id)
                .putString("title", song.title)
                .putString("artist", song.artist)
                .putString("album", _album.value?.name ?: song.album)
                .putInt("duration", song.duration)
                .putString("coverArt", song.coverArt ?: _album.value?.coverArt)
                .putLong("serverId", server.id)
                .putString("serverUrl", server.url)
                .putString("username", server.username)
                .putString("token", server.token)
                .putString("salt", server.salt)
                .build()

            val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                .setInputData(inputData)
                .build()

            androidx.work.WorkManager.getInstance(appContext).enqueue(downloadRequest)
        }
    }

    fun downloadAlbum() {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val currentSongs = _songs.value
            val downloadedIds = downloadedSongIds.value

            currentSongs.forEach { song ->
                if (song.id !in downloadedIds) {
                    val inputData = androidx.work.Data.Builder()
                        .putString("songId", song.id)
                        .putString("title", song.title)
                        .putString("artist", song.artist)
                        .putString("album", _album.value?.name ?: song.album)
                        .putInt("duration", song.duration)
                        .putString("coverArt", song.coverArt ?: _album.value?.coverArt)
                        .putLong("serverId", server.id)
                        .putString("serverUrl", server.url)
                        .putString("username", server.username)
                        .putString("token", server.token)
                        .putString("salt", server.salt)
                        .build()

                    val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                        .setInputData(inputData)
                        .build()

                    androidx.work.WorkManager.getInstance(appContext).enqueue(downloadRequest)
                }
            }
        }
    }

    // Multi-selection methods
    fun playSongs(songIds: Set<String>) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val baseUrl = server.url.removeSuffix("/")
            
            val selectedSongs = _songs.value.filter { it.id in songIds }
            if (selectedSongs.isEmpty()) return@launch

            val mediaItems = selectedSongs.map { s ->
                val streamUrl = "$baseUrl/rest/stream?id=${s.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
                val coverUrl = buildCoverArtUrl(server, s.coverArt ?: _album.value?.coverArt)

                MediaItem.Builder()
                    .setMediaId(s.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(_album.value?.name ?: s.album)
                            .setArtworkUri(coverUrl?.toUri())
                            .build()
                    )
                    .build()
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun downloadSongs(songIds: Set<String>) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val downloadedIds = downloadedSongIds.value
            val selectedSongs = _songs.value.filter { it.id in songIds }

            selectedSongs.forEach { song ->
                if (song.id !in downloadedIds) {
                    val inputData = androidx.work.Data.Builder()
                        .putString("songId", song.id)
                        .putString("title", song.title)
                        .putString("artist", song.artist)
                        .putString("album", _album.value?.name ?: song.album)
                        .putInt("duration", song.duration)
                        .putString("coverArt", song.coverArt ?: _album.value?.coverArt)
                        .putLong("serverId", server.id)
                        .putString("serverUrl", server.url)
                        .putString("username", server.username)
                        .putString("token", server.token)
                        .putString("salt", server.salt)
                        .build()

                    val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                        .setInputData(inputData)
                        .build()

                    androidx.work.WorkManager.getInstance(appContext).enqueue(downloadRequest)
                }
            }
        }
    }

    fun addToFavorites(songIds: Set<String>) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val albumId = _album.value?.id ?: return@launch
            
            // Add to local database
            songIds.forEach { songId ->
                try {
                    musicRepository.addToFavorites(songId)
                } catch (e: Exception) {
                    android.util.Log.e("AlbumDetailViewModel", "Failed to add to favorites: $songId", e)
                    e.printStackTrace()
                }
            }
            
            // Sync with Navidrome server en una sola llamada (batch operation)
            if (songIds.isNotEmpty()) {
                try {
                    android.util.Log.d("AlbumDetailViewModel", "Starring songs on server - IDs: ${songIds.joinToString(", ")}")
                    val response = api.star(
                        id = songIds.toList(), // ← Batch operation: enviar todas a la vez
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                    android.util.Log.d("AlbumDetailViewModel", "Starred ${songIds.size} songs on server - Status: ${response.response.status}")
                    android.util.Log.d("AlbumDetailViewModel", "Server response: ${response}")
                    
                    // Refrescar datos del álbum para mostrar estrellas actualizadas
                    loadAlbum(albumId)
                } catch (e: Exception) {
                    android.util.Log.e("AlbumDetailViewModel", "Failed to star songs on server", e)
                    // Continue even if server sync fails
                }
            }
            
            android.util.Log.d("AlbumDetailViewModel", "Successfully added ${songIds.size} songs to favorites")
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                val response = api.getPlaylists(
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                _playlists.value = response.response.playlistsContainer?.playlist ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("AlbumDetailViewModel", "Failed to load playlists", e)
                e.printStackTrace()
            }
        }
    }

    fun addToPlaylist(songIds: Set<String>, playlistId: String) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            
            try {
                api.addToPlaylist(
                    playlistId = playlistId,
                    songIds = songIds.toList(),
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
