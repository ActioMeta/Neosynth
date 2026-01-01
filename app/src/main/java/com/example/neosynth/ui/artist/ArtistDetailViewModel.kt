package com.example.neosynth.ui.artist

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
import com.example.neosynth.data.remote.responses.AlbumDto
import com.example.neosynth.data.remote.responses.ArtistDto
import com.example.neosynth.data.remote.responses.ArtistInfo
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.player.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    val musicController: MusicController,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _artist = MutableStateFlow<ArtistDto?>(null)
    val artist: StateFlow<ArtistDto?> = _artist

    private val _artistInfo = MutableStateFlow<ArtistInfo?>(null)
    val artistInfo: StateFlow<ArtistInfo?> = _artistInfo

    private val _albums = MutableStateFlow<List<AlbumDto>>(emptyList())
    val albums: StateFlow<List<AlbumDto>> = _albums

    private val _topSongs = MutableStateFlow<List<SongDto>>(emptyList())
    val topSongs: StateFlow<List<SongDto>> = _topSongs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var cachedServer: ServerEntity? = null

    fun loadArtist(artistId: String, artistName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                cachedServer = server
                urlInterceptor.setBaseUrl(server.url)

                // Get artist with albums
                val artistResponse = api.getArtist(
                    artistId = artistId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                _artist.value = artistResponse.response.artist
                _albums.value = artistResponse.response.artist?.album ?: emptyList()

                // Get artist info (biography, images)
                try {
                    val infoResponse = api.getArtistInfo(
                        artistId = artistId,
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                    _artistInfo.value = infoResponse.response.artistInfo
                } catch (e: Exception) {
                    // Artist info is optional
                    e.printStackTrace()
                }

                // Get top songs by searching for artist name
                val songsResponse = api.searchSongs(
                    query = artistName,
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                _topSongs.value = songsResponse.response.searchResult3?.song
                    ?.filter { it.artistId == artistId || it.artist.equals(artistName, ignoreCase = true) }
                    ?.take(10) ?: emptyList()

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

            val mediaItems = _topSongs.value.map { s ->
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

            val startIndex = _topSongs.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    fun shufflePlay() {
        viewModelScope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            val baseUrl = server.url.removeSuffix("/")

            val shuffledSongs = _topSongs.value.shuffled()
            if (shuffledSongs.isEmpty()) return@launch

            val mediaItems = shuffledSongs.map { s ->
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

    fun getCoverUrl(coverArt: String?): String? {
        val server = cachedServer ?: return null
        return buildCoverArtUrl(server, coverArt)
    }
}
