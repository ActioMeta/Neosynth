package com.example.neosynth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.buildCoverArtUrl
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.player.MusicController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.core.net.toUri
import android.util.Log

/**
 * Activity que maneja los deep links de Google Assistant
 * Prioriza canciones locales cuando están disponibles
 */
@AndroidEntryPoint
class DeepLinkActivity : ComponentActivity() {

    @Inject
    lateinit var musicController: MusicController

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var serverDao: ServerDao
    
    @Inject
    lateinit var api: NavidromeApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        Log.d("DeepLinkActivity", "Received deep link: $data")

        if (data != null) {
            handleDeepLink(data)
        } else {
            finish()
        }
    }

    private fun handleDeepLink(uri: Uri) {
        lifecycleScope.launch {
            try {
                val path = uri.pathSegments
                if (path.isEmpty()) {
                    finish()
                    return@launch
                }

                when (path[0]) {
                    "song" -> playSong(uri)
                    "playlist" -> playPlaylist(uri)
                    "album" -> playAlbum(uri)
                    "artist" -> playArtist(uri)
                    "shuffle" -> playShuffle()
                    "favorites" -> playFavorites()
                    "continue" -> continuePlayback()
                    "downloads" -> playDownloads()
                    else -> Log.w("DeepLinkActivity", "Unknown path: ${path[0]}")
                }

                // Solo abrir MainActivity si NO viene de un widget (para que widgets trabajen en segundo plano)
                val fromWidget = intent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0
                if (!fromWidget) {
                    val mainIntent = Intent(this@DeepLinkActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(mainIntent)
                }
                finish()

            } catch (e: Exception) {
                Log.e("DeepLinkActivity", "Error handling deep link", e)
                finish()
            }
        }
    }

    private suspend fun playSong(uri: Uri) {
        val songName = uri.getQueryParameter("name") ?: return
        val artistName = uri.getQueryParameter("artist")

        Log.d("DeepLinkActivity", "Playing song: $songName by $artistName")

        // 1. Buscar primero en canciones descargadas (local)
        val downloadedSongs = musicRepository.getDownloadedSongs().first()
        val localSong = downloadedSongs.find { song ->
            song.title.equals(songName, ignoreCase = true) &&
            (artistName == null || song.artist.equals(artistName, ignoreCase = true))
        }

        if (localSong != null) {
            Log.d("DeepLinkActivity", "Found local song: ${localSong.title}")
            // Reproducir desde archivo local
            val mediaItem = MediaItem.Builder()
                .setMediaId(localSong.id)
                .setUri(localSong.path.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localSong.title)
                        .setArtist(localSong.artist)
                        .setAlbumTitle(localSong.album)
                        .setArtworkUri(localSong.imageUrl?.toUri())
                        .build()
                )
                .build()
            musicController.playQueue(listOf(mediaItem), 0)
        } else {
            // 2. Si no está local, buscar en servidor y hacer streaming
            Log.d("DeepLinkActivity", "Song not found locally, searching server...")
            val server = serverDao.getActiveServer() ?: return
            
            try {
                // Buscar en el servidor
                val searchQuery = if (artistName != null) "$songName $artistName" else songName
                val response = api.searchSongs(
                    query = searchQuery,
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                
                val songs = response.response.searchResult3?.song ?: emptyList()
                val matchingSong = songs.find { song ->
                    song.title.equals(songName, ignoreCase = true) &&
                    (artistName == null || song.artist.equals(artistName, ignoreCase = true))
                }
                
                if (matchingSong != null) {
                    Log.d("DeepLinkActivity", "Found song on server: ${matchingSong.title}")
                    // Reproducir por streaming
                    val streamUrl = "${server.url.removeSuffix("/")}/rest/stream?id=${matchingSong.id}&u=${server.username}&t=${server.token}&s=${server.salt}&v=1.16.1&c=NeoSynth"
                    val coverUrl = buildCoverArtUrl(server, matchingSong.coverArt)
                    
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(matchingSong.id)
                        .setUri(streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(matchingSong.title)
                                .setArtist(matchingSong.artist)
                                .setAlbumTitle(matchingSong.album)
                                .setArtworkUri(coverUrl?.toUri())
                                .build()
                        )
                        .build()
                    musicController.playQueue(listOf(mediaItem), 0)
                } else {
                    Log.w("DeepLinkActivity", "Song not found on server: $songName")
                }
            } catch (e: Exception) {
                Log.e("DeepLinkActivity", "Error searching server for song", e)
            }
        }
    }

    private suspend fun playPlaylist(uri: Uri) {
        val playlistName = uri.getQueryParameter("name") ?: return
        Log.d("DeepLinkActivity", "Playing playlist: $playlistName")

        // 1. Buscar en playlists descargadas
        val server = serverDao.getActiveServer() ?: return
        val playlists = musicRepository.getPlaylistsWithSongs(server.id).first()
        
        val playlist = playlists.find { it.playlist.name.equals(playlistName, ignoreCase = true) }
        
        if (playlist != null && playlist.songs.any { it.isDownloaded }) {
            Log.d("DeepLinkActivity", "Found local playlist: ${playlist.playlist.name}")
            val downloadedSongs = playlist.songs.filter { it.isDownloaded }
            
            val mediaItems = downloadedSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.path.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.imageUrl?.toUri())
                            .build()
                    )
                    .build()
            }
            musicController.playQueue(mediaItems, 0)
        } else {
            Log.w("DeepLinkActivity", "Playlist not found locally: $playlistName")
        }
    }

    private suspend fun playAlbum(uri: Uri) {
        val albumName = uri.getQueryParameter("name") ?: return
        val artistName = uri.getQueryParameter("artist")
        
        Log.d("DeepLinkActivity", "Playing album: $albumName by $artistName")

        // Buscar canciones del álbum en descargas
        val downloadedSongs = musicRepository.getDownloadedSongs().first()
        val albumSongs = downloadedSongs.filter { song ->
            song.album.equals(albumName, ignoreCase = true) &&
            (artistName == null || song.artist.equals(artistName, ignoreCase = true))
        }

        if (albumSongs.isNotEmpty()) {
            Log.d("DeepLinkActivity", "Found ${albumSongs.size} local songs from album")
            val mediaItems = albumSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.path.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.imageUrl?.toUri())
                            .build()
                    )
                    .build()
            }
            musicController.playQueue(mediaItems, 0)
        } else {
            Log.w("DeepLinkActivity", "Album not found locally: $albumName")
        }
    }

    private suspend fun playArtist(uri: Uri) {
        val artistName = uri.getQueryParameter("name") ?: return
        Log.d("DeepLinkActivity", "Playing artist: $artistName")

        // Buscar canciones del artista en descargas
        val downloadedSongs = musicRepository.getDownloadedSongs().first()
        val artistSongs = downloadedSongs.filter { song ->
            song.artist.equals(artistName, ignoreCase = true)
        }.shuffled() // Shuffle para variedad

        if (artistSongs.isNotEmpty()) {
            Log.d("DeepLinkActivity", "Found ${artistSongs.size} local songs from artist")
            val mediaItems = artistSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.path.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.imageUrl?.toUri())
                            .build()
                    )
                    .build()
            }
            musicController.playQueue(mediaItems, 0)
        } else {
            Log.w("DeepLinkActivity", "Artist not found locally: $artistName")
        }
    }

    private suspend fun playShuffle() {
        Log.d("DeepLinkActivity", "Playing shuffle all")

        // Reproducir todas las canciones descargadas en modo aleatorio
        val downloadedSongs = musicRepository.getDownloadedSongs().first()
        
        if (downloadedSongs.isNotEmpty()) {
            val shuffledSongs = downloadedSongs.shuffled()
            val mediaItems = shuffledSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.path.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.imageUrl?.toUri())
                            .build()
                    )
                    .build()
            }
            musicController.playQueue(mediaItems, 0)
        } else {
            Log.w("DeepLinkActivity", "No downloaded songs to shuffle")
        }
    }
    
    private suspend fun playFavorites() {
        Log.d("DeepLinkActivity", "Playing favorites (using downloaded songs)")
        
        // Como no hay API de getStarred, reproducir canciones descargadas shuffled
        // En el futuro se puede filtrar por favoritos si se implementa el tracking local
        val downloadedSongs = musicRepository.getDownloadedSongs().first()
        
        if (downloadedSongs.isNotEmpty()) {
            val shuffledSongs = downloadedSongs.shuffled()
            Log.d("DeepLinkActivity", "Playing ${shuffledSongs.size} downloaded songs as favorites")
            
            val mediaItems = shuffledSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.path.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.imageUrl?.toUri())
                            .build()
                    )
                    .build()
            }
            
            musicController.playQueue(mediaItems, 0)
        } else {
            Log.w("DeepLinkActivity", "No downloaded songs to play as favorites")
        }
    }
    
    private fun continuePlayback() {
        Log.d("DeepLinkActivity", "Continuing playback")
        // Simplemente reanudar la reproducción actual
        musicController.play()
    }
    
    private suspend fun playDownloads() {
        Log.d("DeepLinkActivity", "Playing all downloads")
        
        // Reproducir todas las canciones descargadas
        val downloadedSongs = musicRepository.getDownloadedSongs().first()
        
        if (downloadedSongs.isNotEmpty()) {
            Log.d("DeepLinkActivity", "Found ${downloadedSongs.size} downloaded songs")
            val mediaItems = downloadedSongs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.path.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.imageUrl?.toUri())
                            .build()
                    )
                    .build()
            }
            musicController.playQueue(mediaItems, 0)
        } else {
            Log.w("DeepLinkActivity", "No downloaded songs found")
        }
    }
}
