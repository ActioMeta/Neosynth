package com.example.neosynth.ui.discover.recent

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.player.MusicController
import com.example.neosynth.utils.NetworkHelper
import com.example.neosynth.utils.ConnectionType
import com.example.neosynth.utils.StreamUrlBuilder
import com.example.neosynth.data.preferences.StreamQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import javax.inject.Inject

enum class SongSortOrder(val label: String) {
    CREATED_DESC("Más recientes"),
    CREATED_ASC("Más antiguos"),
    TITLE("Título A→Z"),
    ARTIST("Artista A→Z"),
    ALBUM("Álbum A→Z"),
    DURATION_DESC("Mayor duración")
}

@HiltViewModel
class RecentSongsViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val musicRepository: MusicRepository,
    val musicController: MusicController,
    private val settingsPreferences: SettingsPreferences,
    private val networkHelper: NetworkHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 10 // álbumes por página
    }

    // --- State ---
    var songs by mutableStateOf<List<SongDto>>(emptyList())
        private set

    var sortOrder by mutableStateOf(SongSortOrder.CREATED_DESC)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var hasMore by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    // Multi-select
    var selectedSongIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val isSelectionMode: Boolean get() = selectedSongIds.isNotEmpty()

    // Downloaded IDs for badges
    val downloadedSongIds = musicRepository.getDownloadedSongs()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    // Cached server for cover URLs
    private var cachedServer: com.example.neosynth.data.local.entities.ServerEntity? = null

    // Pagination state
    private var albumOffset = 0
    private val allRawSongs = mutableListOf<SongDto>() // acumulado sin ordenar

    init {
        loadSongs()
    }

    // ---------- Carga ----------

    fun loadSongs() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            error = null
            albumOffset = 0
            allRawSongs.clear()
            hasMore = true
            try {
                fetchPage()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || isLoading || !hasMore) return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                fetchPage()
            } finally {
                isLoadingMore = false
            }
        }
    }

    private suspend fun fetchPage() {
        val server = serverDao.getActiveServer() ?: run {
            error = "No hay servidor configurado"
            hasMore = false
            return
        }
        cachedServer = server
        urlInterceptor.setBaseUrl(server.url)

        // 1. Obtener PAGE_SIZE álbumes con offset actual
        val albumResponse = api.getAlbumList(
            type = "newest",
            size = PAGE_SIZE,
            offset = albumOffset,
            user = server.username,
            token = server.token,
            salt = server.salt
        )
        val albums = albumResponse.response.albumList2?.album
            ?: albumResponse.response.albumList?.album
            ?: emptyList()

        if (albums.isEmpty()) {
            hasMore = false
            return
        }

        // 2. Obtener canciones de cada álbum en paralelo
        val newSongs = coroutineScope {
            albums.map { album ->
                async {
                    try {
                        val albumDetail = api.getAlbum(
                            albumId = album.id,
                            u = server.username,
                            t = server.token,
                            s = server.salt
                        )
                        albumDetail.response.albumDetails?.song ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }.flatten()

        // 3. Acumular y re-ordenar
        allRawSongs.addAll(newSongs)
        // Deduplicar por ID (un álbum puede aparecer en varias páginas edge-case)
        val deduped = allRawSongs.distinctBy { it.id }
        allRawSongs.clear()
        allRawSongs.addAll(deduped)

        albumOffset += PAGE_SIZE
        hasMore = albums.size == PAGE_SIZE

        songs = withContext(kotlinx.coroutines.Dispatchers.Default) {
            applySortOrder(allRawSongs)
        }
    }

    // ---------- Ordenamiento ----------

    fun changeSortOrder(order: SongSortOrder) {
        sortOrder = order
        viewModelScope.launch {
            val sorted = withContext(kotlinx.coroutines.Dispatchers.Default) {
                applySortOrder(allRawSongs)
            }
            songs = sorted
        }
    }

    private fun applySortOrder(list: List<SongDto>): List<SongDto> = when (sortOrder) {
        SongSortOrder.CREATED_DESC -> list.sortedByDescending { it.created ?: "" }
        SongSortOrder.CREATED_ASC  -> list.sortedBy { it.created ?: "" }
        SongSortOrder.TITLE        -> list.sortedBy { it.title.lowercase() }
        SongSortOrder.ARTIST       -> list.sortedBy { it.artist.lowercase() }
        SongSortOrder.ALBUM        -> list.sortedBy { it.album.lowercase() }
        SongSortOrder.DURATION_DESC -> list.sortedByDescending { it.duration }
    }

    // ---------- Multi-select ----------

    fun toggleSelection(songId: String) {
        selectedSongIds = if (songId in selectedSongIds) {
            selectedSongIds - songId
        } else {
            selectedSongIds + songId
        }
    }

    fun selectAll() {
        selectedSongIds = songs.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedSongIds = emptySet()
    }

    // ---------- Descarga masiva ----------

    fun downloadSelected() {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val toDownload = songs.filter {
                it.id in selectedSongIds && it.id !in downloadedSongIds.value
            }
            if (toDownload.isEmpty()) return@launch

            val workManager = androidx.work.WorkManager.getInstance(appContext)

            toDownload.forEach { song ->
                val inputData = androidx.work.Data.Builder()
                    .putString("songId", song.id)
                    .putString("title", song.title)
                    .putString("artist", song.artist)
                    .putString("album", song.album)
                    .putString("albumId", song.albumId ?: "")
                    .putInt("duration", song.duration)
                    .putInt("originalBitRate", song.bitRate ?: 0)
                    .putString("originalSuffix", song.suffix ?: "MP3")
                    .putString("coverArt", song.coverArt)
                    .putLong("serverId", server.id)
                    .putString("serverUrl", server.url)
                    .putString("username", server.username)
                    .putString("token", server.token)
                    .putString("salt", server.salt)
                    .build()

                val request = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.DownloadWorker>()
                    .setInputData(inputData)
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag("recent_songs_download")
                    .build()
                workManager.enqueue(request)
            }

            clearSelection()
        }
    }
    // ---------- Quick Actions ----------

    fun playSelected() {
        val selected = songs.filter { it.id in selectedSongIds }
        if (selected.isEmpty()) return
        
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val mediaItems = buildMediaItems(selected, server)
            if (mediaItems.isNotEmpty()) {
                musicController.playQueue(mediaItems, startIndex = 0)
            }
        }
    }

    fun addSelectedToQueue() {
        val selected = songs.filter { it.id in selectedSongIds }
        if (selected.isEmpty()) return
        
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val mediaItems = buildMediaItems(selected, server)
            if (mediaItems.isNotEmpty()) {
                musicController.addToQueue(mediaItems)
            }
        }
    }

    fun addSelectedToPlaylist() {
        // Opción actualmente stub debido a que la funcionalidad requiere un sheet para seleccionar playlists.
    }

    // ---------- Reproducción ----------

    fun playSong(song: SongDto) {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val mediaItems = buildMediaItems(songs, server)
            
            val startIndex = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    private suspend fun buildMediaItems(songList: List<SongDto>, server: com.example.neosynth.data.local.entities.ServerEntity): List<MediaItem> {
        val connectionType = networkHelper.getConnectionType()
        val audioSettings = settingsPreferences.audioSettings.first()
        val streamQuality = when (connectionType) {
            ConnectionType.WIFI   -> audioSettings.streamWifiQuality
            ConnectionType.MOBILE -> audioSettings.streamMobileQuality
            ConnectionType.NONE   -> StreamQuality.MEDIUM
        }

        return songList.map { s ->
            val effectiveBitrate = if (streamQuality != StreamQuality.LOSSLESS) {
                streamQuality.bitrate
            } else {
                s.bitRate ?: 0
            }
            
            val effectiveFormat = if (streamQuality != StreamQuality.LOSSLESS) {
                streamQuality.format.uppercase()
            } else {
                s.suffix?.uppercase() ?: "MP3"
            }

            val streamUrl = StreamUrlBuilder.buildStreamUrl(server, s.id, streamQuality)
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
                        .setExtras(
                            android.os.Bundle().apply {
                                putInt("bitRate", effectiveBitrate)
                                putString("suffix", effectiveFormat)
                                putString("metadata", """{"bitRate":$effectiveBitrate,"format":"$effectiveFormat","suffix":"$effectiveFormat"}""")
                                putLong("duration", s.duration * 1000L)
                                putInt("originalBitRate", s.bitRate ?: 0)
                                putString("originalSuffix", s.suffix ?: "MP3")
                            }
                        )
                        .build()
                )
                .build()
        }
    }

    fun getCoverUrl(coverArt: String?): String? {
        val server = cachedServer ?: return null
        return buildCoverArtUrl(server, coverArt)
    }
}
