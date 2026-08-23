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
import com.example.neosynth.data.local.entities.SongEntity
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
import com.example.neosynth.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.neosynth.data.preferences.SettingsPreferences
import com.example.neosynth.utils.NetworkHelper
import com.example.neosynth.utils.ConnectionType
import com.example.neosynth.utils.StreamUrlBuilder
import com.example.neosynth.data.preferences.StreamQuality
import kotlinx.coroutines.flow.first
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
    private val settingsPreferences: SettingsPreferences,
    private val networkHelper: NetworkHelper,
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
    var isRefreshing by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
    
    private var cachedServer: ServerEntity? = null
    
    // IDs de canciones descargadas (para mostrar badge)
    val downloadedSongIds = musicRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())
    
    // Décadas dinámicas (se cargarán desde el servidor)
    var decades by mutableStateOf<List<Pair<String, IntRange>>>(emptyList())
        private set
    var isLoadingDecades by mutableStateOf(false)
        private set

    private var searchJob: Job? = null

    // --- Canciones recientes (preview para Discover) ---
    var recentSongsPreview by mutableStateOf<List<SongDto>>(emptyList())
        private set
    var isLoadingRecentSongs by mutableStateOf(false)
        private set

    init {
        loadGenres()
        loadDecades()
        loadRecentSongsPreview()
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            error = null
            try {
                loadGenresInternal()
                loadDecadesInternal()
                loadRecentSongsPreviewInternal()
                if (searchQuery.isNotBlank()) {
                    search(searchQuery)
                }
            } finally {
                isRefreshing = false
            }
        }
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
                error = appContext.getString(R.string.error_no_active_server)
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
            error = e.localizedMessage ?: appContext.getString(R.string.error_connection_failed_generic)
        } finally {
            isSearching = false
        }
    }

    fun loadGenres() {
        if (isLoadingGenres) return
        viewModelScope.launch {
            loadGenresInternal()
        }
    }

    private suspend fun loadGenresInternal() {
        isLoadingGenres = true
        error = null
        try {
            val server = serverDao.getActiveServer() ?: run {
                error = appContext.getString(R.string.error_no_active_server)
                isLoadingGenres = false
                return
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
            error = e.localizedMessage ?: appContext.getString(R.string.error_connection_failed_generic)
        } finally {
            isLoadingGenres = false
        }
    }

    fun loadRecentSongsPreview() {
        if (isLoadingRecentSongs) return
        viewModelScope.launch {
            loadRecentSongsPreviewInternal()
        }
    }

    private suspend fun loadRecentSongsPreviewInternal() {
        isLoadingRecentSongs = true
        try {
            val server = serverDao.getActiveServer() ?: return
            if (cachedServer == null) cachedServer = server
            urlInterceptor.setBaseUrl(server.url)

            // Obtener los 3 álbumes más recientes
            val albumResponse = api.getAlbumList(
                type = "newest",
                size = 3,
                user = server.username,
                token = server.token,
                salt = server.salt
            )
            val albums = albumResponse.response.albumList2?.album
                ?: albumResponse.response.albumList?.album
                ?: emptyList()

            // Obtener canciones de cada álbum en paralelo
            val songs = coroutineScope {
                albums.map { album ->
                    async {
                        try {
                            val detail = api.getAlbum(
                                albumId = album.id,
                                u = server.username,
                                t = server.token,
                                s = server.salt
                            )
                            detail.response.albumDetails?.song ?: emptyList()
                        } catch (e: Exception) { emptyList<SongDto>() }
                    }
                }.awaitAll()
            }.flatten()

            recentSongsPreview = songs
                .sortedByDescending { it.created ?: "" }
                .take(6)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingRecentSongs = false
        }
    }

    private fun loadDecades() {
        if (isLoadingDecades) return
        viewModelScope.launch {
            loadDecadesInternal()
        }
    }

    private suspend fun loadDecadesInternal() {
        isLoadingDecades = true
        try {
            val server = serverDao.getActiveServer() ?: run {
                isLoadingDecades = false
                return
            }
            urlInterceptor.setBaseUrl(server.url)

            // Obtener una muestra representativa de canciones para detectar años
            val response = api.getRandomSongs(
                size = 500,
                u = server.username,
                t = server.token,
                s = server.salt,
                v = "1.16.1",
                c = "NeoSynth"
            )

            val allSongs = response.response.randomSongs?.song ?: emptyList()
            val years = allSongs.mapNotNull { it.year }.distinct().sorted()

            val suffix = appContext.getString(R.string.decade_suffix)
            if (years.isEmpty()) {
                // Si no hay años, usar décadas predeterminadas
                decades = listOf(
                    "2020$suffix" to 2020..2029,
                    "2010$suffix" to 2010..2019,
                    "2000$suffix" to 2000..2009,
                    "90$suffix" to 1990..1999,
                    "80$suffix" to 1980..1989,
                    "70$suffix" to 1970..1979,
                    "60$suffix" to 1960..1969
                )
            } else {
                // Generar décadas dinámicamente basadas en los años disponibles
                val minYear = years.first()
                val maxYear = years.last()

                val decadesList = mutableListOf<Pair<String, IntRange>>()

                // Generar décadas desde la más reciente a la más antigua
                val currentDecade = (maxYear / 10) * 10
                val oldestDecade = (minYear / 10) * 10

                for (decadeStart in currentDecade downTo oldestDecade step 10) {
                    val decadeEnd = decadeStart + 9
                    val decadeLabel = if (decadeStart >= 2000) {
                        decadeStart.toString()
                    } else {
                        (decadeStart % 100).toString()
                    }
                    val suffix = appContext.getString(R.string.decade_suffix)
                    val label = "$decadeLabel$suffix"
                    decadesList.add(label to decadeStart..decadeEnd)
                }

                decades = decadesList
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val suffix = appContext.getString(R.string.decade_suffix)
            // En caso de error, usar décadas predeterminadas
            decades = listOf(
                "2020$suffix" to 2020..2029,
                "2010$suffix" to 2010..2019,
                "2000$suffix" to 2000..2009,
                "90$suffix" to 1990..1999,
                "80$suffix" to 1980..1989,
                "70$suffix" to 1970..1979,
                "60$suffix" to 1960..1969
            )
        } finally {
            isLoadingDecades = false
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

    fun quickPlayGenre(genre: String) {
        viewModelScope.launch {
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
                val songs = response.response.songsByGenre?.song ?: emptyList()
                if (songs.isNotEmpty()) {
                    val shuffled = songs.shuffled()
                    playSong(shuffled.first(), shuffled)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun quickPlayDecade(decade: Pair<String, IntRange>) {
        viewModelScope.launch {
            try {
                val server = serverDao.getActiveServer() ?: return@launch
                urlInterceptor.setBaseUrl(server.url)
                val response = api.getRandomSongs(
                    size = 500,
                    u = server.username,
                    t = server.token,
                    s = server.salt,
                    v = "1.16.1",
                    c = "NeoSynth"
                )
                val allSongs = response.response.randomSongs?.song ?: emptyList()
                val songsWithYear = allSongs.filter { song -> 
                    song.year != null && song.year in decade.second 
                }
                if (songsWithYear.isNotEmpty()) {
                    val shuffled = songsWithYear.shuffled()
                    playSong(shuffled.first(), shuffled)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
            
            // Obtener configuración de calidad según tipo de conexión
            val connectionType = networkHelper.getConnectionType()
            val audioSettings = settingsPreferences.audioSettings.first()
        
            val streamQuality = when (connectionType) {
                ConnectionType.WIFI -> audioSettings.streamWifiQuality
                ConnectionType.MOBILE -> audioSettings.streamMobileQuality
                ConnectionType.NONE -> StreamQuality.MEDIUM
            }

            val mediaItems = allSongs.map { s: SongDto ->
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
            
            val startIndex = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            musicController.playQueue(mediaItems, startIndex)
        }
    }

    fun playNext(songs: List<SongDto>) {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val connectionType = networkHelper.getConnectionType()
            val audioSettings = settingsPreferences.audioSettings.first()
            val streamQuality = when (connectionType) {
                ConnectionType.WIFI -> audioSettings.streamWifiQuality
                ConnectionType.MOBILE -> audioSettings.streamMobileQuality
                ConnectionType.NONE -> StreamQuality.MEDIUM
            }
            val mediaItems = songs.map { s ->
                val effectiveBitrate = if (streamQuality != StreamQuality.LOSSLESS) streamQuality.bitrate else s.bitRate ?: 0
                val effectiveFormat = if (streamQuality != StreamQuality.LOSSLESS) streamQuality.format.uppercase() else s.suffix?.uppercase() ?: "MP3"
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
                                    putLong("duration", s.duration * 1000L)
                                }
                            )
                            .build()
                    )
                    .build()
            }
            musicController.addAfterCurrent(mediaItems)
        }
    }

    fun addToQueue(songs: List<SongDto>) {
        viewModelScope.launch {
            val server = serverDao.getActiveServer() ?: return@launch
            val connectionType = networkHelper.getConnectionType()
            val audioSettings = settingsPreferences.audioSettings.first()
            val streamQuality = when (connectionType) {
                ConnectionType.WIFI -> audioSettings.streamWifiQuality
                ConnectionType.MOBILE -> audioSettings.streamMobileQuality
                ConnectionType.NONE -> StreamQuality.MEDIUM
            }
            val mediaItems = songs.map { s ->
                val effectiveBitrate = if (streamQuality != StreamQuality.LOSSLESS) streamQuality.bitrate else s.bitRate ?: 0
                val effectiveFormat = if (streamQuality != StreamQuality.LOSSLESS) streamQuality.format.uppercase() else s.suffix?.uppercase() ?: "MP3"
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
                                    putLong("duration", s.duration * 1000L)
                                }
                            )
                            .build()
                    )
                    .build()
            }
            musicController.addToQueue(mediaItems)
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

            val existing = musicRepository.getSongById(song.id)
            val metadataJson = """{"bitRate":${song.bitRate ?: 0},"format":"${song.suffix ?: "MP3"}","suffix":"${song.suffix ?: "MP3"}"}"""
            if (existing == null) {
                val songEntity = com.example.neosynth.data.local.entities.SongEntity(
                    id = song.id,
                    title = song.title,
                    serverID = server.id,
                    sourceType = "SUBSONIC",
                    sourceId = server.id.toString(),
                    artistID = song.artistId ?: "",
                    artist = song.artist ?: "Unknown Artist",
                    albumID = song.albumId ?: "",
                    album = song.album ?: "Unknown Album",
                    duration = song.duration.toLong(),
                    imageUrl = song.coverArt,
                    path = "",
                    isDownloaded = false,
                    metadata = metadataJson
                )
                musicRepository.insertSong(songEntity)
            }

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val inputData = androidx.work.Data.Builder()
                .putString("batch_id", "song_${song.id}")
                .putString("batch_type", "SONG_IDS")
                .putString("batch_name", song.title)
                .putStringArray("song_ids", arrayOf(song.id))
                .putLong("serverId", server.id)
                .putString("serverUrl", server.url)
                .putString("username", server.username)
                .putString("token", server.token)
                .putString("salt", server.salt)
                .build()
            
            val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.neosynth.data.worker.BatchDownloadWorker>()
                .setInputData(inputData)
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .addTag("batch_download")
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
                
                // Preparar lista de IDs para sync con servidor
                val songIds = mutableListOf<String>()
                
                for (song in songs) {
                    // Primero, asegurarse de que la canción existe en Room
                    val existingSong = musicRepository.getSongById(song.id)
                    if (existingSong == null) {
                        val newSong = com.example.neosynth.data.local.entities.SongEntity(
                            id = song.id,
                            title = song.title,
                            serverID = 0L,
                            sourceType = "SUBSONIC",
                            sourceId = server.id.toString(),
                            artistID = song.artistId ?: "",
                            artist = song.artist ?: "Unknown",
                            albumID = song.albumId ?: "",
                            album = song.album ?: "Unknown",
                            duration = song.duration.toLong() * 1000L,
                            imageUrl = song.coverArt,
                            path = "",
                            isDownloaded = false,
                            isFavorite = false
                        )
                        musicRepository.insertSong(newSong)
                        android.util.Log.d("DiscoverViewModel", "Created song entity for favoriting: ${song.id}")
                    }
                    
                    // Add to favorites
                    musicRepository.addToFavorites(song.id)
                    songIds.add(song.id)
                }
                
                // Sync con Navidrome server en una sola llamada (más eficiente)
                if (songIds.isNotEmpty()) {
                    try {
                        val response = api.star(
                            id = songIds, // ← Usar List directamente (batch operation)
                            u = server.username,
                            t = server.token,
                            s = server.salt
                        )
                        android.util.Log.d("DiscoverViewModel", "Starred ${songIds.size} songs on server - ${response.response.status}")
                    } catch (e: Exception) {
                        android.util.Log.e("DiscoverViewModel", "Failed to star songs on server", e)
                        // Continue even if server sync fails - we still have it locally
                    }
                }
                
                android.util.Log.d("DiscoverViewModel", "Successfully added ${songs.size} songs to favorites")
            } catch (e: Exception) {
                android.util.Log.e("DiscoverViewModel", "Error adding songs to favorites", e)
                e.printStackTrace()
            }
        }
    }
    
    fun playSelectedSongs(songs: List<SongDto>) {
        if (songs.isEmpty()) return
        playSong(songs.first(), songs)
    }
}
