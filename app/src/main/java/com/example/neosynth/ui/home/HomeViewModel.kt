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
import com.example.neosynth.data.local.entities.SongEntity
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.utils.NetworkHelper
import com.example.neosynth.utils.StreamUrlBuilder
import com.example.neosynth.utils.ConnectionType
import com.example.neosynth.data.repository.LyricsRepository
import kotlinx.coroutines.flow.first
import android.util.Log

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository,
    private val lyricsRepository: LyricsRepository,
    val musicController: MusicController,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val settingsPreferences: SettingsPreferences,
    private val networkHelper: NetworkHelper,
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
    
    // Estado de favorito de la canción actual
    private val _isCurrentSongFavorite = MutableStateFlow(false)
    val isCurrentSongFavorite: StateFlow<Boolean> = _isCurrentSongFavorite
    
    // Estado de letras con caché
    private val _currentLyrics = MutableStateFlow<String?>(null)
    val currentLyrics: StateFlow<String?> = _currentLyrics
    
    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics
    
    private val _lyricsError = MutableStateFlow<String?>(null)
    val lyricsError: StateFlow<String?> = _lyricsError
    
    // Caché de letras (songId -> lyrics)
    private val lyricsCache = mutableMapOf<String, String?>()
    
    /**
     * Actualizar el estado de favorito de la canción actual
     * Debe llamarse cuando cambie la canción
     */
    fun updateCurrentSongFavoriteStatus() {
        viewModelScope.launch {
            val mediaItem = musicController.currentMediaItem.value
            _isCurrentSongFavorite.value = if (mediaItem != null) {
                musicRepository.isFavorite(mediaItem.mediaId)
            } else {
                false
            }
        }
    }
    
    /**
     * Cargar letras sincronizadas de la canción actual (con caché)
     */
    fun loadLyrics() {
        viewModelScope.launch {
            val mediaItem = musicController.currentMediaItem.value
            if (mediaItem == null) {
                _currentLyrics.value = null
                _lyricsError.value = null
                return@launch
            }
            
            val songId = mediaItem.mediaId
            
            // Verificar caché primero (solo si fue exitoso)
            if (lyricsCache.containsKey(songId)) {
                val cachedLyrics = lyricsCache[songId]
                if (cachedLyrics != null) {
                    // Letras encontradas en cache
                    _currentLyrics.value = cachedLyrics
                    _lyricsError.value = null
                    return@launch
                }
                // Si hay null en cache, significa que falló antes, reintentar
            }
            
            _isLoadingLyrics.value = true
            _lyricsError.value = null
            _currentLyrics.value = null
            
            try {
                val artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
                val title = mediaItem.mediaMetadata.title?.toString() ?: ""
                val album = mediaItem.mediaMetadata.albumTitle?.toString()
                
                Log.d("HomeViewModel", "Fetching lyrics: $artist - $title")
                
                val lyrics = lyricsRepository.getSyncedLyrics(
                    artist = artist,
                    title = title,
                    album = album
                )
                
                _currentLyrics.value = lyrics
                
                if (lyrics != null) {
                    // Solo cachear si se encontraron letras
                    lyricsCache[songId] = lyrics
                    _lyricsError.value = null
                } else {
                    // NO cachear errores - permitir reintentos
                    _lyricsError.value = "No se encontraron letras para esta canción"
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading lyrics", e)
                _currentLyrics.value = null
                // NO cachear excepciones - permitir reintentos
                _lyricsError.value = "Error al cargar las letras: ${e.message}"
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }
    
    /**
     * Limpiar letras (cuando cambia la canción)
     */
    fun clearLyrics() {
        _currentLyrics.value = null
        _lyricsError.value = null
    }

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
                name = dto.title,
                sourceType = com.example.neosynth.domain.model.MusicSourceType.SUBSONIC,
                sourceId = server.id.toString(),
                artistId = dto.artistId ?: "",
                artistName = dto.artist,
                coverArtUrl = url,
                year = dto.year,
                songCount = dto.songCount ?: 0,
                genre = dto.genre
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
            try {
                val server = serverDao.getActiveServer()
                if (server == null) {
                    android.util.Log.e("HomeViewModel", "No active server found")
                    _uiEvent.emit(UiEvent.ShowSnackbar("No hay servidor activo"))
                    return@launch
                }
                
                val currentItem = musicController.currentMediaItem.value
                if (currentItem == null) {
                    android.util.Log.e("HomeViewModel", "No current song playing")
                    _uiEvent.emit(UiEvent.ShowSnackbar("No hay canción reproduciéndose"))
                    return@launch
                }

                val songId = currentItem.mediaId
                val songTitle = currentItem.mediaMetadata.title?.toString() ?: "canción"
                
                // Verificar si ya está descargada
                val existingSong = musicRepository.getSongById(songId)
                if (existingSong != null && existingSong.isDownloaded) {
                    android.util.Log.d("HomeViewModel", "Song already downloaded: $songTitle")
                    _uiEvent.emit(UiEvent.ShowSnackbar("$songTitle ya está descargada"))
                    return@launch
                }

                android.util.Log.d("HomeViewModel", "Starting download for: $songTitle ($songId)")

                val inputData = Data.Builder()
                    .putString("songId", songId) // ← Corregido de "song_id" a "songId"
                    .putString("title", currentItem.mediaMetadata.title?.toString() ?: "Unknown")
                    .putString("artist", currentItem.mediaMetadata.artist?.toString() ?: "Unknown")
                    .putString("artistId", "") // ← Corregido de "artist_id"
                    .putString("album", currentItem.mediaMetadata.albumTitle?.toString() ?: "Unknown")
                    .putString("albumId", "") // ← Corregido de "album_id"
                    .putInt("duration", 0) // ← Corregido a Int
                    .putString("coverArt", currentItem.mediaMetadata.artworkUri?.toString()) // ← Corregido de "image_url"
                    .putLong("serverId", server.id) // ← Corregido de "server_id"
                    .putString("serverUrl", server.url) // ← Agregado
                    .putString("username", server.username) // ← Agregado
                    .putString("token", server.token) // ← Agregado
                    .putString("salt", server.salt) // ← Agregado
                    .build()

                val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(inputData)
                    .addTag("download_$songId")
                    .build()

                WorkManager.getInstance(appContext).enqueue(downloadRequest)
                android.util.Log.d("HomeViewModel", "Download request enqueued for: $songTitle")
                
                // Insertar en Room si no existe
                if (existingSong == null) {
                    val newSong = com.example.neosynth.data.local.entities.SongEntity(
                        id = songId,
                        title = currentItem.mediaMetadata.title?.toString() ?: "Unknown",
                        serverID = 0L,
                        sourceType = "SUBSONIC",
                        sourceId = server.id.toString(),
                        artistID = "",
                        artist = currentItem.mediaMetadata.artist?.toString() ?: "Unknown",
                        albumID = "",
                        album = currentItem.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                        duration = 0L,
                        imageUrl = currentItem.mediaMetadata.artworkUri?.toString(),
                        path = "",
                        isDownloaded = false,
                        isFavorite = false
                    )
                    musicRepository.insertSong(newSong)
                }
                
                // Mostrar feedback al usuario
                _uiEvent.emit(UiEvent.ShowSnackbar("Descargando: $songTitle"))
                
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error downloading song", e)
                _uiEvent.emit(UiEvent.ShowSnackbar("Error al descargar: ${e.message}"))
            }
        }
    }

    /**
     * Toggle favorito para la canción actual
     */
    fun toggleFavorite() {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: run {
                android.util.Log.e("HomeViewModel", "No active server found")
                _uiEvent.emit(UiEvent.ShowSnackbar("No hay servidor activo"))
                return@launch
            }
            
            val currentItem = musicController.currentMediaItem.value ?: run {
                android.util.Log.e("HomeViewModel", "No current song")
                _uiEvent.emit(UiEvent.ShowSnackbar("No hay canción reproduciéndose"))
                return@launch
            }
            
            val songId = currentItem.mediaId

            try {
                // Check current favorite status
                val isFavorite = musicRepository.isFavorite(songId)
                
                if (isFavorite) {
                    // Remove from favorites
                    musicRepository.removeFromFavorites(songId)
                    
                    // Sync with server
                    try {
                        api.unstar(
                            id = listOf(songId), // ← Corregido: usar List en lugar de String
                            u = server.username,
                            t = server.token,
                            s = server.salt
                        )
                        android.util.Log.d("HomeViewModel", "Removed from favorites: $songId")
                        _uiEvent.emit(UiEvent.ShowSnackbar("Eliminado de favoritos"))
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Failed to unstar on server: $songId", e)
                        _uiEvent.emit(UiEvent.ShowSnackbar("Error al sincronizar con servidor"))
                    }
                } else {
                    // Primero, asegurarse de que la canción existe en Room
                    // Si no existe, crearla con la metadata del MediaItem
                    val existingSong = musicRepository.getSongById(songId)
                    if (existingSong == null) {
                        val newSong = com.example.neosynth.data.local.entities.SongEntity(
                            id = songId,
                            title = currentItem.mediaMetadata.title?.toString() ?: "Unknown",
                            serverID = 0L,
                            sourceType = "SUBSONIC",
                            sourceId = server.id.toString(),
                            artistID = "",
                            artist = currentItem.mediaMetadata.artist?.toString() ?: "Unknown",
                            albumID = "",
                            album = currentItem.mediaMetadata.albumTitle?.toString() ?: "Unknown",
                            duration = 0L,
                            imageUrl = currentItem.mediaMetadata.artworkUri?.toString(),
                            path = "",
                            isDownloaded = false,
                            isFavorite = false
                        )
                        musicRepository.insertSong(newSong)
                        android.util.Log.d("HomeViewModel", "Created song entity for favoriting: $songId")
                    }
                    
                    // Add to favorites
                    musicRepository.addToFavorites(songId)
                    
                    // Sync with server
                    try {
                        api.star(
                            id = listOf(songId), // ← Corregido: usar List en lugar de String
                            u = server.username,
                            t = server.token,
                            s = server.salt
                        )
                        android.util.Log.d("HomeViewModel", "Added to favorites: $songId")
                        _uiEvent.emit(UiEvent.ShowSnackbar("Agregado a favoritos"))
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Failed to star on server: $songId", e)
                        _uiEvent.emit(UiEvent.ShowSnackbar("Error al sincronizar con servidor"))
                    }
                }
                
                // Actualizar estado de UI
                updateCurrentSongFavoriteStatus()
                
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error toggling favorite for $songId", e)
                _uiEvent.emit(UiEvent.ShowSnackbar("Error al cambiar favorito"))
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
        // Obtener configuración de calidad según tipo de conexión
        val connectionType = networkHelper.getConnectionType()
        val audioSettings = runCatching { 
            kotlinx.coroutines.runBlocking { settingsPreferences.audioSettings.first() }
        }.getOrNull()
        
        val streamQuality = when (connectionType) {
            ConnectionType.WIFI -> audioSettings?.streamWifiQuality ?: com.example.neosynth.data.preferences.StreamQuality.LOSSLESS
            ConnectionType.MOBILE -> audioSettings?.streamMobileQuality ?: com.example.neosynth.data.preferences.StreamQuality.MEDIUM
            ConnectionType.NONE -> com.example.neosynth.data.preferences.StreamQuality.MEDIUM // Fallback
        }
        
        // Construir URL con parámetros de transcodificación
        val streamUrl = StreamUrlBuilder.buildStreamUrl(server, songDto.id, streamQuality)

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