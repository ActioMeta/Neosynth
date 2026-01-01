package com.example.neosynth.ui.discover

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
import com.example.neosynth.data.remote.responses.AlbumDto
import com.example.neosynth.data.remote.responses.ArtistDto
import com.example.neosynth.data.remote.responses.GenreDto
import com.example.neosynth.data.remote.responses.SongDto
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.player.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

data class SearchResults(
    val songs: List<SongDto> = emptyList(),
    val artists: List<ArtistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList()
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val urlInterceptor: DynamicUrlInterceptor,
    private val musicRepository: MusicRepository,
    val musicController: MusicController,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var searchResults by mutableStateOf(SearchResults())
    
    var genres by mutableStateOf<List<GenreDto>>(emptyList())
    var isLoadingGenres by mutableStateOf(false)
    
    var selectedGenre by mutableStateOf<String?>(null)
    var genreSongs by mutableStateOf<List<SongDto>>(emptyList())
    var isLoadingGenreSongs by mutableStateOf(false)
    
    var showAllGenres by mutableStateOf(false)
    
    var selectedDecade by mutableStateOf<Pair<String, IntRange>?>(null)
    var decadeSongs by mutableStateOf<List<SongDto>>(emptyList())
    var isLoadingDecadeSongs by mutableStateOf(false)
    
    var error by mutableStateOf<String?>(null)
    
    private var cachedServer: ServerEntity? = null
    
    // IDs de canciones descargadas (para mostrar badge)
    val downloadedSongIds = musicRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())
    
    // Décadas predefinidas
    val decades = listOf(
        "2020s" to 2020..2029,
        "2010s" to 2010..2019,
        "2000s" to 2000..2009,
        "90s" to 1990..1999,
        "80s" to 1980..1989,
        "70s" to 1970..1979,
        "60s" to 1960..1969
    )

    private var searchJob: Job? = null

    init {
        loadGenres()
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        searchJob?.cancel()
        error = null // Limpiar error al buscar
        
        if (query.isBlank()) {
            searchResults = SearchResults()
            isSearching = false
            return
        }
        
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            search(query)
        }
    }

    private suspend fun search(query: String) {
        isSearching = true
        error = null
        try {
            val server = serverDao.getActiveServer() ?: run {
                error = "No hay servidor configurado"
                isSearching = false
                return
            }
            urlInterceptor.setBaseUrl(server.url)
            
            val response = api.searchSongs(
                query = query,
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            
            val songs = response.response.searchResult3?.song ?: emptyList()
            
            // También buscar artistas y álbumes
            val artistsResponse = api.getArtists(
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            
            val allArtists = artistsResponse.response.artistsContainer?.indices
                ?.flatMap { it.artist ?: emptyList() } ?: emptyList()
            
            val matchingArtists = allArtists.filter { 
                it.name.contains(query, ignoreCase = true) 
            }.take(5)
            
            val albumsResponse = api.getAlbumList(
                type = "alphabeticalByName",
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            
            val allAlbums = albumsResponse.response.albumList?.album 
                ?: albumsResponse.response.albumList2?.album ?: emptyList()
            
            val matchingAlbums = allAlbums.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
            }.take(5)
            
            searchResults = SearchResults(
                songs = songs.take(10),
                artists = matchingArtists,
                albums = matchingAlbums
            )
        } catch (e: Exception) {
            e.printStackTrace()
            error = e.localizedMessage ?: "Error de conexión"
        } finally {
            isSearching = false
        }
    }

    fun loadGenres() {
        viewModelScope.launch {
            isLoadingGenres = true
            error = null
            try {
                val server = serverDao.getActiveServer() ?: run {
                    error = "No hay servidor configurado"
                    isLoadingGenres = false
                    return@launch
                }
                cachedServer = server
                urlInterceptor.setBaseUrl(server.url)
                
                val response = api.getGenres(
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                
                genres = response.response.genres?.genre
                    ?.filter { (it.songCount ?: 0) > 0 }
                    ?.sortedByDescending { it.songCount }
                    ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                error = e.localizedMessage ?: "Error de conexión"
            } finally {
                isLoadingGenres = false
            }
        }
    }

    fun loadSongsByGenre(genre: String) {
        selectedGenre = genre
        viewModelScope.launch {
            isLoadingGenreSongs = true
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                
                val response = api.getSongsByGenre(
                    genre = genre,
                    count = 50,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
                
                genreSongs = response.response.songsByGenre?.song ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingGenreSongs = false
            }
        }
    }

    fun clearGenreSelection() {
        selectedGenre = null
        genreSongs = emptyList()
    }

    fun loadSongsByDecade(decade: Pair<String, IntRange>) {
        selectedDecade = decade
        viewModelScope.launch {
            isLoadingDecadeSongs = true
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                
                // Obtener canciones aleatorias
                val response = api.getRandomSongs(
                    size = 500,
                    u = server.username,
                    t = server.token,
                    s = server.salt,
                    v = "1.16.1",
                    c = "NeoSynth"
                )
                
                val allSongs = response.response.randomSongs?.song ?: emptyList()
                
                // Filtrar canciones que tienen el año en el rango de la década
                val songsWithYear = allSongs.filter { song -> 
                    song.year != null && song.year in decade.second 
                }
                
                // Si no hay canciones con año directo, buscar por álbumes
                if (songsWithYear.isEmpty()) {
                    val albumsResponse = api.getAlbumList(
                        type = "alphabeticalByName",
                        user = server.username,
                        token = server.token,
                        salt = server.salt
                    )
                    
                    val albumsInDecade = (albumsResponse.response.albumList?.album 
                        ?: albumsResponse.response.albumList2?.album ?: emptyList())
                        .filter { album -> album.year != null && album.year in decade.second }
                        .map { it.id }
                        .toSet()
                    
                    decadeSongs = allSongs
                        .filter { song -> song.albumId in albumsInDecade }
                        .take(50)
                } else {
                    decadeSongs = songsWithYear.take(50)
                }
                    
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingDecadeSongs = false
            }
        }
    }

    fun clearDecadeSelection() {
        selectedDecade = null
        decadeSongs = emptyList()
    }

    fun loadArtistSongs(artistId: String, artistName: String) {
        // Usar búsqueda por artista
        viewModelScope.launch {
            isSearching = true
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                
                val response = api.searchSongs(
                    query = artistName,
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                
                val artistSongs = response.response.searchResult3?.song
                    ?.filter { it.artistId == artistId || it.artist.equals(artistName, ignoreCase = true) }
                    ?: emptyList()
                
                searchResults = SearchResults(
                    songs = artistSongs,
                    artists = emptyList(),
                    albums = emptyList()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSearching = false
            }
        }
    }

    fun playSong(song: SongDto, allSongs: List<SongDto>) {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val baseUrl = server.url.removeSuffix("/")
            
            val mediaItems = allSongs.map { s: SongDto ->
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
            
            val startIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    fun getCoverUrl(coverArt: String?): String? {
        val server = cachedServer ?: return null
        return buildCoverArtUrl(server, coverArt)
    }
    
    fun downloadSong(song: SongDto) {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            
            // Verificar si ya está descargada
            if (song.id in downloadedSongIds.value) return@launch
            
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
    
    fun isSongDownloaded(songId: String): Boolean {
        return songId in downloadedSongIds.value
    }
    
    // State para manejar las playlists disponibles (para agregar canciones)
    var availablePlaylists by mutableStateOf<List<com.example.neosynth.data.remote.responses.PlaylistDto>>(emptyList())
        private set
    
    var showPlaylistPicker by mutableStateOf(false)
    var songsToAddToPlaylist by mutableStateOf<List<SongDto>>(emptyList())
    
    fun loadPlaylistsForPicker(songs: List<SongDto>) {
        songsToAddToPlaylist = songs
        showPlaylistPicker = true
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                val response = api.getPlaylists(server.username, server.token, server.salt)
                availablePlaylists = response.response.playlistsContainer?.playlist ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun addSongsToPlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                for (song in songsToAddToPlaylist) {
                    api.updatePlaylist(
                        playlistId = playlistId,
                        songIdToAdd = song.id,
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                }
                showPlaylistPicker = false
                songsToAddToPlaylist = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun addSongsToFavorites(songs: List<SongDto>) {
        viewModelScope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                for (song in songs) {
                    val response = api.star(
                        id = song.id,
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                    android.util.Log.d("DiscoverViewModel", "Star response for ${song.title}: ${response.response.status}")
                }
                android.util.Log.d("DiscoverViewModel", "Successfully starred ${songs.size} songs")
            } catch (e: Exception) {
                android.util.Log.e("DiscoverViewModel", "Error starring songs", e)
                e.printStackTrace()
            }
        }
    }
    
    fun playSelectedSongs(songs: List<SongDto>) {
        if (songs.isEmpty()) return
        playSong(songs.first(), songs)
    }
}
