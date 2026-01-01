package com.example.neosynth.ui.playlist

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
class PlaylistDetailViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val musicRepository: MusicRepository,
    val musicController: MusicController,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _playlist = MutableStateFlow<PlaylistDto?>(null)
    val playlist: StateFlow<PlaylistDto?> = _playlist

    private val _songs = MutableStateFlow<List<SongDto>>(emptyList())
    val songs: StateFlow<List<SongDto>> = _songs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var cachedServer: ServerEntity? = null
    private var currentPlaylistId: String? = null

    val downloadedSongIds = musicRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun loadPlaylist(playlistId: String) {
        currentPlaylistId = playlistId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                cachedServer = server
                urlInterceptor.setBaseUrl(server.url)

                val response = api.getPlaylist(
                    playlistId = playlistId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )

                val playlistDetails = response.response.playlistDetails
                if (playlistDetails != null) {
                    _playlist.value = PlaylistDto(
                        id = playlistDetails.id,
                        name = playlistDetails.name,
                        songCount = playlistDetails.entry?.size ?: 0,
                        duration = playlistDetails.entry?.sumOf { it.duration } ?: 0,
                        coverArt = playlistDetails.entry?.firstOrNull()?.coverArt
                    )
                    _songs.value = playlistDetails.entry ?: emptyList()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playPlaylist() {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val currentSongs = _songs.value
            if (currentSongs.isEmpty()) return@launch

            val mediaItems = currentSongs.map { song ->
                val streamUrl = "${server.url.removeSuffix("/")}/rest/stream?id=${song.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
                val coverUrl = buildCoverArtUrl(server, song.coverArt)

                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(coverUrl?.toUri())
                            .build()
                    )
                    .build()
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun shufflePlay() {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val currentSongs = _songs.value.shuffled()
            if (currentSongs.isEmpty()) return@launch

            val mediaItems = currentSongs.map { song ->
                val streamUrl = "${server.url.removeSuffix("/")}/rest/stream?id=${song.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
                val coverUrl = buildCoverArtUrl(server, song.coverArt)

                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(coverUrl?.toUri())
                            .build()
                    )
                    .build()
            }

            musicController.playQueue(mediaItems, 0)
        }
    }

    fun playSong(song: SongDto) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val currentSongs = _songs.value

            val mediaItems = currentSongs.map { s ->
                val streamUrl = "${server.url.removeSuffix("/")}/rest/stream?id=${s.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
                val coverUrl = buildCoverArtUrl(server, s.coverArt)

                MediaItem.Builder()
                    .setMediaId(s.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(coverUrl?.toUri())
                            .build()
                    )
                    .build()
            }

            val startIndex = currentSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    fun removeSongFromPlaylist(songIndex: Int) {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                val playlistId = currentPlaylistId ?: return@launch

                api.updatePlaylist(
                    playlistId = playlistId,
                    songIndexToRemove = songIndex,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )

                // Reload playlist
                loadPlaylist(playlistId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadPlaylist() {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val currentPlaylist = _playlist.value ?: return@launch
            val currentSongs = _songs.value
            val songsToDownload = currentSongs.filter { song -> song.id !in downloadedSongIds.value }
            
            if (songsToDownload.isEmpty()) return@launch

            // 1. Guardar la playlist en Room
            try {
                val playlistEntity = com.example.neosynth.data.local.entities.PlaylistEntity(
                    id = currentPlaylist.id,
                    name = currentPlaylist.name,
                    serverId = server.id,
                    coverArt = currentPlaylist.coverArt,
                    songCount = currentSongs.size
                )
                musicRepository.insertPlaylist(playlistEntity)
                
                // 2. Crear las referencias playlist-canción con posiciones
                val crossRefs = currentSongs.mapIndexed { index, song ->
                    com.example.neosynth.data.local.entities.PlaylistSongCrossRef(
                        playlistId = currentPlaylist.id,
                        songId = song.id,
                        position = index
                    )
                }
                musicRepository.insertPlaylistSongCrossRefs(crossRefs)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Procesar descargas en lotes para soportar miles de canciones sin bloquear
            val batchSize = 50
            val workManager = androidx.work.WorkManager.getInstance(appContext)
            
            // Configurar constraints para descargas
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            songsToDownload.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                batch.forEach { song ->
                    val inputData = androidx.work.Data.Builder()
                        .putString("songId", song.id)
                        .putString("title", song.title)
                        .putString("artist", song.artist)
                        .putString("album", song.album)
                        .putInt("duration", song.duration)
                        .putString("coverArt", song.coverArt)
                        .putLong("serverId", server.id)
                        .putString("serverUrl", server.url)
                        .putString("username", server.username)
                        .putString("token", server.token)
                        .putString("salt", server.salt)
                        .build()

                    val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                        .setInputData(inputData)
                        .setConstraints(constraints)
                        .addTag("playlist_download_${currentPlaylistId}")
                        .build()

                    workManager.enqueue(downloadRequest)
                }
                
                // Pequeña pausa entre lotes para no saturar la cola
                if (batchIndex < (songsToDownload.size / batchSize)) {
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }

    fun getCoverUrl(coverArt: String?): String? {
        val server = cachedServer ?: return null
        return buildCoverArtUrl(server, coverArt)
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
                val coverUrl = buildCoverArtUrl(server, s.coverArt)

                MediaItem.Builder()
                    .setMediaId(s.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
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
                        .putString("album", song.album)
                        .putInt("duration", song.duration)
                        .putString("coverArt", song.coverArt)
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
            
            songIds.forEach { songId ->
                try {
                    api.star(
                        id = songId,
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
