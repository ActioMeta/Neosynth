package com.example.neosynth.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.AlbumDto
import com.example.neosynth.data.remote.responses.ArtistDto
import com.example.neosynth.data.local.entities.PendingSyncActionEntity
import com.example.neosynth.data.local.entities.PlaylistEntity
import com.example.neosynth.data.remote.responses.PlaylistDto
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.utils.NetworkHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import com.example.neosynth.data.worker.PlaylistSyncWorker

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val musicRepository: MusicRepository,
    private val networkHelper: NetworkHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistDto>> = _playlists

    private val prefs = context.getSharedPreferences("neosynth_library_pins", Context.MODE_PRIVATE)

    private val _pinnedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedPlaylistIds: StateFlow<Set<String>> = _pinnedPlaylistIds

    private val _pinnedAlbumIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedAlbumIds: StateFlow<Set<String>> = _pinnedAlbumIds

    private val _pinnedArtistIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedArtistIds: StateFlow<Set<String>> = _pinnedArtistIds

    init {
        _pinnedPlaylistIds.value = prefs.getStringSet("playlists", emptySet()) ?: emptySet()
        _pinnedAlbumIds.value = prefs.getStringSet("albums", emptySet()) ?: emptySet()
        _pinnedArtistIds.value = prefs.getStringSet("artists", emptySet()) ?: emptySet()
    }

    fun togglePinPlaylist(id: String) {
        val current = _pinnedPlaylistIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _pinnedPlaylistIds.value = current
        prefs.edit().putStringSet("playlists", current).apply()
    }

    fun togglePinAlbum(id: String) {
        val current = _pinnedAlbumIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _pinnedAlbumIds.value = current
        prefs.edit().putStringSet("albums", current).apply()
    }

    fun togglePinArtist(id: String) {
        val current = _pinnedArtistIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _pinnedArtistIds.value = current
        prefs.edit().putStringSet("artists", current).apply()
    }

    val favoriteSongsCount: StateFlow<Int> = musicRepository.getFavoriteSongs()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _artists = MutableStateFlow<List<ArtistDto>>(emptyList())
    val artists: StateFlow<List<ArtistDto>> = _artists

    private val _albums = MutableStateFlow<List<AlbumDto>>(emptyList())
    val albums: StateFlow<List<AlbumDto>> = _albums

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMoreAlbums = MutableStateFlow(false)
    val isLoadingMoreAlbums: StateFlow<Boolean> = _isLoadingMoreAlbums

    private var albumOffset = 0
    private val ALBUM_PAGE_SIZE = 50
    private var isLastAlbumPage = false

    private var cachedServer: ServerEntity? = null

    fun loadLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Always fetch server so cachedServer is available even in offline mode
                val server = serverDao.getActiveServer() ?: return@launch
                cachedServer = server
                urlInterceptor.setBaseUrl(server.url)

                if (networkHelper.isCurrentConnectionOffline) {
                    // Offline: load playlists from local DB only
                    loadLocalPlaylists(server)
                    _isLoading.value = false
                    return@launch
                }

                // Online: load from server
                // Load playlists
                try {
                    val playlistsResponse = api.getPlaylists(
                        user = server.username,
                        token = server.token,
                        salt = server.salt
                    )
                    _playlists.value = playlistsResponse.response.playlistsContainer?.playlist ?: emptyList()
                } catch (e: Exception) {
                    // Fallback to local DB if API fails
                    loadLocalPlaylists(server)
                }

                // Load all artists
                try {
                    val artistsResponse = api.getArtists(
                        user = server.username,
                        token = server.token,
                        salt = server.salt
                    )
                    val allArtists = artistsResponse.response.artistsContainer?.indices
                        ?.flatMap { it.artist ?: emptyList() } ?: emptyList()
                    _artists.value = allArtists
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Load all albums (Initial load)
                try {
                    albumOffset = 0
                    isLastAlbumPage = false
                    val albumsResponse = api.getAlbumList(
                        type = "alphabeticalByName",
                        size = ALBUM_PAGE_SIZE,
                        offset = albumOffset,
                        user = server.username,
                        token = server.token,
                        salt = server.salt
                    )
                    val newAlbums = albumsResponse.response.albumList?.album 
                        ?: albumsResponse.response.albumList2?.album ?: emptyList()
                    
                    if (newAlbums.size < ALBUM_PAGE_SIZE) {
                        isLastAlbumPage = true
                    }
                    _albums.value = newAlbums
                    albumOffset += newAlbums.size
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadLocalPlaylists(server: ServerEntity) {
        try {
            val localPlaylists = musicRepository.getPlaylistsByServer(server.id).first()
            _playlists.value = localPlaylists.map { entity ->
                PlaylistDto(
                    id = entity.id,
                    name = entity.name,
                    songCount = entity.songCount,
                    duration = 0,
                    coverArt = entity.coverArt
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("LibraryViewModel", "Failed to load local playlists", e)
        }
    }

    fun loadMoreAlbums() {
        if (_isLoadingMoreAlbums.value || isLastAlbumPage || _isLoading.value) return

        viewModelScope.launch {
            _isLoadingMoreAlbums.value = true
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                val albumsResponse = api.getAlbumList(
                    type = "alphabeticalByName",
                    size = ALBUM_PAGE_SIZE,
                    offset = albumOffset,
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                val newAlbums = albumsResponse.response.albumList?.album 
                    ?: albumsResponse.response.albumList2?.album ?: emptyList()
                
                if (newAlbums.size < ALBUM_PAGE_SIZE) {
                    isLastAlbumPage = true
                }
                
                _albums.value = _albums.value + newAlbums
                albumOffset += newAlbums.size
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingMoreAlbums.value = false
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            urlInterceptor.setBaseUrl(server.url)

            // 1. Create a local temporary playlist ID
            val localId = "local-${UUID.randomUUID()}"
            
            // 2. Insert locally
            val localPlaylist = PlaylistEntity(
                id = localId,
                serverId = server.id,
                name = name,
                songCount = 0,
                coverArt = null
            )
            musicRepository.insertPlaylist(localPlaylist)

            // 3. Queue action
            val payloadObj = JSONObject().apply {
                put("name", name)
                put("localId", localId)
            }
            val pendingAction = PendingSyncActionEntity(
                serverId = server.id,
                actionType = "CREATE_PLAYLIST",
                payload = payloadObj.toString()
            )
            musicRepository.insertPendingSyncAction(pendingAction)

            try {
                // Attempt immediate
                api.createPlaylist(
                    name = name,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Direct sync failed, offline queued instead: ${e.message}")
            }
            
            // Trigger SyncWorker
            val syncRequest = OneTimeWorkRequestBuilder<PlaylistSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(syncRequest)
            
            loadPlaylists()
        }
    }

    fun updatePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            urlInterceptor.setBaseUrl(server.url)

            // 1. Update local UI entity
            val currentPlaylist = musicRepository.getPlaylistById(playlistId)
            if (currentPlaylist != null) {
                musicRepository.insertPlaylist(currentPlaylist.copy(name = newName))
            }

            // 2. Queue action
            val payloadObj = JSONObject().apply {
                put("playlistId", playlistId)
                put("newName", newName)
            }
            val pendingAction = PendingSyncActionEntity(
                serverId = server.id,
                actionType = "UPDATE_PLAYLIST",
                payload = payloadObj.toString()
            )
            musicRepository.insertPendingSyncAction(pendingAction)

            try {
                api.updatePlaylist(
                    playlistId = playlistId,
                    name = newName,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Direct sync failed, offline queued instead: ${e.message}")
            }
            
            // Trigger SyncWorker
            val syncRequest = OneTimeWorkRequestBuilder<PlaylistSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(syncRequest)
            
            loadPlaylists()
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            urlInterceptor.setBaseUrl(server.url)

            // 1. Delete local DB
            musicRepository.deletePlaylist(playlistId)
            musicRepository.deletePlaylistSongs(playlistId)

            // 2. Queue action
            val payloadObj = JSONObject().apply {
                put("playlistId", playlistId)
            }
            val pendingAction = PendingSyncActionEntity(
                serverId = server.id,
                actionType = "DELETE_PLAYLIST",
                payload = payloadObj.toString()
            )
            musicRepository.insertPendingSyncAction(pendingAction)

            try {
                api.deletePlaylist(
                    id = playlistId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Direct sync failed, offline queued instead: ${e.message}")
            }
            
            // Trigger SyncWorker
            val syncRequest = OneTimeWorkRequestBuilder<PlaylistSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(syncRequest)
            
            loadPlaylists()
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                if (networkHelper.isCurrentConnectionOffline) {
                    loadLocalPlaylists(server)
                    return@launch
                }
                val playlistsResponse = api.getPlaylists(
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                _playlists.value = playlistsResponse.response.playlistsContainer?.playlist ?: emptyList()
            } catch (e: Exception) {
                // Fallback to local on any network error
                val server = cachedServer ?: return@launch
                loadLocalPlaylists(server)
            }
        }
    }

    fun getCoverUrl(coverArt: String?): String? {
        val server = cachedServer ?: return null
        return buildCoverArtUrl(server, coverArt)
    }
}
