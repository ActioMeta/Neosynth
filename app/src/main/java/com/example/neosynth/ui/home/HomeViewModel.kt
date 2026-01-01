package com.example.neosynth.ui.home
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.data.worker.DownloadWorker
import com.example.neosynth.domain.model.Album
import com.example.neosynth.player.MusicController
import com.example.neosynth.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository,
    val musicController: MusicController,
    private val urlInterceptor: DynamicUrlInterceptor,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private var browserFuture: ListenableFuture<MediaBrowser>? = null
    private val browser: MediaBrowser?
        get() = if (browserFuture?.isDone == true) browserFuture?.get() else null

    var recentlyAdded by mutableStateOf<List<Album>>(emptyList())

    var randomCoverArts by mutableStateOf<List<String>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    
    // IDs de canciones descargadas
    val downloadedSongIds = musicRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    // Eventos de UI (Snackbar messages)
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
    }

    private var albumsLoaded = false
    private var randomSongsLoaded = false

    init {
        loadHomeData()
    }

    fun initPlayer(context: Context) {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
        browserFuture?.addListener({
            // todo: player ready status
        }, MoreExecutors.directExecutor())
    }

    fun loadHomeData() {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                
                // Cargar álbumes si no están cargados
                if (!albumsLoaded) {
                    loadRecentAlbums(server)
                    albumsLoaded = true
                }
                
                // Cargar canciones random si no están cargadas
                if (!randomSongsLoaded) {
                    loadRandomSongs(server)
                    randomSongsLoaded = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                error = e.localizedMessage ?: "error de conexión"
                android.util.Log.e("NEOSYNTH_DEBUG", "Error cargando datos: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
    
    private suspend fun loadRecentAlbums(server: com.example.neosynth.data.local.entities.ServerEntity) {
        val response = api.getRecentlyAdded(
            type = "newest",
            u = server.username,
            t = server.token,
            s = server.salt,
            v = "1.16.1",
            c = "NeoSynth",
            f = "json"
        )
        val albumsFromApi =
            response.response.albumList2?.album ?: response.response.albumList?.album
            ?: emptyList()
        recentlyAdded = albumsFromApi.map { dto ->
            val url = buildCoverArtUrl(server, dto.coverArt)
            com.example.neosynth.domain.model.Album(
                id = dto.id,
                title = dto.title,
                serverId = server.id,
                genre = dto.genre,
                artist = dto.artist,
                coverArtUrl = url,
                year = dto.year
            )
        }
    }
    
    private suspend fun loadRandomSongs(server: com.example.neosynth.data.local.entities.ServerEntity) {
        val resposeRandom = api.getRandomSongs(
            size = 3,
            u = server.username,
            t = server.token,
            s = server.salt,
            v = "1.16.1",
            c = "NeoSynth",
            f = "json"
        )
        val randomSongs = resposeRandom.response.randomSongs?.song.orEmpty()
        randomCoverArts = randomSongs.mapNotNull { songDto ->
            buildCoverArtUrl(server, songDto.coverArt)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            error = null
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                
                // Recargar álbumes y canciones random
                loadRecentAlbums(server)
                loadRandomSongs(server)
                randomSongsLoaded = true
            } catch (e: Exception) {
                e.printStackTrace()
                error = e.localizedMessage ?: "error de conexión"
            } finally {
                isRefreshing = false
            }
        }
    }

    fun playShuffle() {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            urlInterceptor.setBaseUrl(server.url)
            try {
                val response = api.getRandomSongs(
                    size = 20,
                    u = server.username,
                    t = server.token,
                    s = server.salt,
                    v = "1.16.1",
                    c = "NeoSynth",
                    f = "json"
                )

                val songsDto = response.response.randomSongs?.song.orEmpty()

                randomCoverArts =
                    songsDto.take(3).mapNotNull { buildCoverArtUrl(server, it.coverArt) }

                val mediaItems = songsDto.map { songDto ->
                    val baseUrl = server.url.removeSuffix("/")
                    val streamUrl =
                        "$baseUrl/rest/stream?id=${songDto.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"

                    MediaItem.Builder()
                        .setMediaId(songDto.id)
                        .setUri(streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(songDto.title)
                                .setArtist(songDto.artist)
                                .setArtworkUri(buildCoverArtUrl(server, songDto.coverArt)?.toUri())
                                .build()
                        )
                        .build()
                }

                musicController.playQueue(mediaItems,0)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Reproduce todas las canciones de un álbum
     */
    fun playAlbum(albumId: String, shuffle: Boolean = false) {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            urlInterceptor.setBaseUrl(server.url)
            try {
                val response = api.getAlbum(
                    albumId = albumId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )

                val songs = response.response.albumDetails?.song.orEmpty()
                val mediaItems = songs.map { songDto ->
                    songDtoToMediaItem(songDto, server)
                }

                if (shuffle && mediaItems.isNotEmpty()) {
                    musicController.playQueue(mediaItems.shuffled(), 0)
                } else {
                    musicController.playQueue(mediaItems, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Descarga todas las canciones de un álbum
     */
    fun downloadAlbum(albumId: String) {
        android.util.Log.d("HomeViewModel", "downloadAlbum called with albumId: $albumId")
        viewModelScope.launch {
            val server = serverDao.getActiveServer()
            if (server == null) {
                android.util.Log.e("HomeViewModel", "No active server found")
                return@launch
            }
            urlInterceptor.setBaseUrl(server.url)
            try {
                android.util.Log.d("HomeViewModel", "Fetching album from API...")
                val response = api.getAlbum(
                    albumId = albumId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )

                val songs = response.response.albumDetails?.song.orEmpty()
                android.util.Log.d("HomeViewModel", "Found ${songs.size} songs in album")
                
                songs.forEach { songDto ->
                    android.util.Log.d("HomeViewModel", "Enqueueing download: ${songDto.title}")
                    enqueueSongDownload(songDto, server)
                }
                // Las notificaciones se manejan en el DownloadWorker
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error downloading album: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Descarga una canción individual (usada desde PlayerScreen)
     */
    fun downloadCurrentSong() {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val currentItem = musicController.currentMediaItem.value ?: return@launch

            val baseUrl = server.url.removeSuffix("/")
            val streamUrl = "$baseUrl/rest/stream?id=${currentItem.mediaId}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
            val songTitle = currentItem.mediaMetadata.title?.toString() ?: "canción"

            val inputData = Data.Builder()
                .putString("song_id", currentItem.mediaId)
                .putString("url", streamUrl)
                .putString("title", currentItem.mediaMetadata.title?.toString() ?: "Unknown")
                .putString("artist", currentItem.mediaMetadata.artist?.toString() ?: "Unknown")
                .putString("artist_id", "")
                .putString("album", currentItem.mediaMetadata.albumTitle?.toString() ?: "Unknown")
                .putString("album_id", "")
                .putLong("duration", 0L)
                .putString("image_url", currentItem.mediaMetadata.artworkUri?.toString())
                .putLong("server_id", server.id)
                .build()

            val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .addTag("download_${currentItem.mediaId}")
                .build()

            WorkManager.getInstance(appContext).enqueue(downloadRequest)
            // Las notificaciones se manejan en el DownloadWorker
        }
    }

    /**
     * Toggle favorito para la canción actual
     */
    fun toggleFavorite() {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val currentItem = musicController.currentMediaItem.value ?: return@launch
            val songId = currentItem.mediaId

            try {
                // Por ahora solo intentar agregar a favoritos (star)
                // TODO: Implementar tracking local de favoritos para saber si hacer star o unstar
                val response = api.star(
                    id = songId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                android.util.Log.d("HomeViewModel", "Star response: ${response.response.status}")
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error toggling favorite for $songId", e)
            }
        }
    }

    private fun enqueueSongDownload(
        songDto: SongDto,
        server: com.example.neosynth.data.local.entities.ServerEntity
    ) {
        val baseUrl = server.url.removeSuffix("/")
        val streamUrl = "$baseUrl/rest/stream?id=${songDto.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
        val coverUrl = buildCoverArtUrl(server, songDto.coverArt)

        android.util.Log.d("HomeViewModel", "Creating download request for: ${songDto.title}")
        android.util.Log.d("HomeViewModel", "Stream URL: $streamUrl")

        val inputData = Data.Builder()
            .putString("song_id", songDto.id)
            .putString("url", streamUrl)
            .putString("title", songDto.title)
            .putString("artist", songDto.artist)
            .putString("artist_id", songDto.artistId ?: "")
            .putString("album", songDto.album)
            .putString("album_id", songDto.albumId ?: "")
            .putLong("duration", songDto.duration.toLong())
            .putString("image_url", coverUrl)
            .putLong("server_id", server.id)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag("download_${songDto.id}")
            .build()

        WorkManager.getInstance(appContext).enqueue(downloadRequest)
        android.util.Log.d("HomeViewModel", "Download request enqueued for: ${songDto.title}")
    }

    private fun songDtoToMediaItem(
        songDto: SongDto,
        server: com.example.neosynth.data.local.entities.ServerEntity
    ): MediaItem {
        val baseUrl = server.url.removeSuffix("/")
        val streamUrl = "$baseUrl/rest/stream?id=${songDto.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"

        return MediaItem.Builder()
            .setMediaId(songDto.id)
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(songDto.title)
                    .setArtist(songDto.artist)
                    .setAlbumTitle(songDto.album)
                    .setArtworkUri(buildCoverArtUrl(server, songDto.coverArt)?.toUri())
                    .build()
            )
            .build()
    }
}