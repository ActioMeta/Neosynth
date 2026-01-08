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

    private val _allPlaylists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    val allPlaylists: StateFlow<List<PlaylistDto>> = _allPlaylists

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
            
            // Logs detallados para debugging
            android.util.Log.d("PlaylistDownload", "═══════════════════════════════════════")
            android.util.Log.d("PlaylistDownload", "Iniciando descarga de playlist: ${currentPlaylist.name}")
            android.util.Log.d("PlaylistDownload", "Total canciones en playlist: ${currentSongs.size}")
            android.util.Log.d("PlaylistDownload", "Ya descargadas: ${currentSongs.size - songsToDownload.size}")
            android.util.Log.d("PlaylistDownload", "A descargar: ${songsToDownload.size}")
            android.util.Log.d("PlaylistDownload", "═══════════════════════════════════════")
            
            if (songsToDownload.isEmpty()) {
                android.util.Log.d("PlaylistDownload", "⚠️ Todas las canciones ya están descargadas")
                return@launch
            }

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
                
                // 2. Insertar TODAS las canciones INMEDIATAMENTE (aunque aún no estén descargadas)
                // Esto permite que PlaylistWithSongs funcione correctamente
                currentSongs.forEach { song ->
                    val songEntity = com.example.neosynth.data.local.entities.SongEntity(
                        id = song.id,
                        title = song.title,
                        serverID = 0L, // DEPRECATED
                        sourceType = "SUBSONIC",
                        sourceId = server.id.toString(),
                        artistID = song.artistId ?: "",
                        artist = song.artist ?: "Unknown Artist",
                        albumID = song.albumId ?: "",
                        album = song.album ?: "Unknown Album",
                        duration = song.duration.toLong(),
                        imageUrl = song.coverArt,
                        path = "", // Se actualizará cuando el DownloadWorker termine
                        isDownloaded = false // Se marcará como true cuando se descargue
                    )
                    musicRepository.insertSong(songEntity)
                }
                
                // 3. Crear las referencias playlist-canción con posiciones
                val crossRefs = currentSongs.mapIndexed { index, song ->
                    com.example.neosynth.data.local.entities.PlaylistSongCrossRef(
                        playlistId = currentPlaylist.id,
                        songId = song.id,
                        position = index
                    )
                }
                musicRepository.insertPlaylistSongCrossRefs(crossRefs)
                
                android.util.Log.d("PlaylistDownload", "Playlist guardada en Room")
            } catch (e: Exception) {
                android.util.Log.e("PlaylistDownload", "❌ Error guardando playlist: ${e.message}", e)
                e.printStackTrace()
            }

            // 4. ESTRATEGIA HÍBRIDA: Lotes pequeños en paralelo, batches secuenciales
            // Optimizado para playlists grandes (1000+ canciones) sin colapsar el sistema
            // Adaptación dinámica según capacidades del dispositivo (Galaxy A7 2018, etc.)
            val parallelSize = com.example.neosynth.utils.DownloadOptimizer.getOptimalBatchSize(appContext)
            val workManager = androidx.work.WorkManager.getInstance(appContext)
            
            // Configurar constraints para descargas
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // Permitir con batería baja
                .build()

            // Crear batches de workers
            val batches = songsToDownload.chunked(parallelSize)
            var workContinuation: androidx.work.WorkContinuation? = null
            
            batches.forEachIndexed { batchIndex, batch ->
                // Crear workers para este batch (se ejecutarán en paralelo)
                val parallelWorkers = batch.mapIndexed { indexInBatch, song ->
                    val globalIndex = batchIndex * parallelSize + indexInBatch + 1
                    
                    val inputData = androidx.work.Data.Builder()
                        .putString("songId", song.id)
                        .putString("title", song.title)
                        .putString("artist", song.artist ?: "Unknown Artist")
                        .putString("artistId", song.artistId ?: "")
                        .putString("album", song.album ?: "Unknown Album")
                        .putString("albumId", song.albumId ?: "")
                        .putInt("duration", song.duration)
                        .putString("coverArt", song.coverArt)
                        .putLong("serverId", server.id)
                        .putString("serverUrl", server.url)
                        .putString("username", server.username)
                        .putString("token", server.token)
                        .putString("salt", server.salt)
                        // Metadata para notificación consolidada
                        .putString("playlist_id", currentPlaylist.id)
                        .putString("playlist_name", currentPlaylist.name)
                        .putInt("total_songs", songsToDownload.size)
                        .putInt("current_index", globalIndex)
                        .build()

                    androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                        .setInputData(inputData)
                        .setConstraints(constraints)
                        .addTag("playlist_${currentPlaylist.id}")
                        .addTag("download_worker")
                        .setBackoffCriteria(
                            androidx.work.BackoffPolicy.EXPONENTIAL,
                            10000L, // 10 segundos de backoff inicial
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        )
                        .build()
                }
                
                // Encadenar batches: cada batch espera a que el anterior termine
                // Dentro de cada batch, los workers se ejecutan en paralelo
                workContinuation = if (workContinuation == null) {
                    workManager.beginWith(parallelWorkers)
                } else {
                    workContinuation!!.then(parallelWorkers)
                }
            }
            
            // Encolar toda la cadena de trabajo
            workContinuation?.enqueue()
            
            android.util.Log.d("PlaylistDownload", "✅ ${songsToDownload.size} canciones encoladas en ${batches.size} batches de $parallelSize")
            android.util.Log.d("PlaylistDownload", "⏱️ Tiempo estimado: ~${(songsToDownload.size * 8) / 60} minutos (${parallelSize} workers paralelos)")
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
            
            // Add to local database
            songIds.forEach { songId ->
                try {
                    musicRepository.addToFavorites(songId)
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistDetailViewModel", "Failed to add to favorites: $songId", e)
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
                    android.util.Log.d("PlaylistDetailViewModel", "Starred ${songIds.size} songs on server")
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistDetailViewModel", "Failed to star songs on server", e)
                }
            }
        }
    }

    fun loadAllPlaylists() {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                val response = api.getPlaylists(
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                _allPlaylists.value = response.response.playlistsContainer?.playlist ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("PlaylistDetailViewModel", "Failed to load playlists", e)
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
