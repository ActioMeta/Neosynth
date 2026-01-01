package com.example.neosynth.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.player.MusicController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VoiceCommandReceiver : BroadcastReceiver() {

    @Inject
    lateinit var musicController: MusicController

    @Inject
    lateinit var musicRepository: MusicRepository

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            Log.d("VoiceCommandReceiver", "Comando de voz recibido")
            handleVoiceSearch(intent)
        }
    }

    private fun handleVoiceSearch(intent: Intent) {
        val searchQuery = intent.getStringExtra(android.app.SearchManager.QUERY)
        val title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
        val artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
        val album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)

        Log.d("VoiceCommandReceiver", "Query: $searchQuery, Title: $title, Artist: $artist, Album: $album")

        // Usar corrutina para búsqueda asíncrona
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val downloadedSongs = musicRepository.getDownloadedSongs().first()
                
                when {
                    !title.isNullOrEmpty() -> {
                        val song = downloadedSongs.find { 
                            it.title.contains(title, ignoreCase = true) 
                        }
                        if (song != null) {
                            playSongs(downloadedSongs, downloadedSongs.indexOf(song))
                            Log.d("VoiceCommandReceiver", "Reproduciendo: ${song.title}")
                        }
                    }
                    !artist.isNullOrEmpty() -> {
                        val artistSongs = downloadedSongs.filter { 
                            it.artist.contains(artist, ignoreCase = true) 
                        }
                        if (artistSongs.isNotEmpty()) {
                            playSongs(artistSongs.shuffled(), 0)
                            Log.d("VoiceCommandReceiver", "Reproduciendo artista: $artist")
                        }
                    }
                    !album.isNullOrEmpty() -> {
                        val albumSongs = downloadedSongs.filter { 
                            it.album?.contains(album, ignoreCase = true) == true 
                        }
                        if (albumSongs.isNotEmpty()) {
                            playSongs(albumSongs, 0)
                            Log.d("VoiceCommandReceiver", "Reproduciendo álbum: $album")
                        }
                    }
                    !searchQuery.isNullOrEmpty() -> {
                        val song = downloadedSongs.find { 
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true)
                        }
                        if (song != null) {
                            playSongs(downloadedSongs, downloadedSongs.indexOf(song))
                            Log.d("VoiceCommandReceiver", "Reproduciendo búsqueda: $searchQuery")
                        }
                    }
                    else -> {
                        // Sin parámetros: shuffle todo
                        if (downloadedSongs.isNotEmpty()) {
                            playSongs(downloadedSongs.shuffled(), 0)
                            Log.d("VoiceCommandReceiver", "Reproduciendo todo (shuffle)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceCommandReceiver", "Error procesando comando de voz", e)
            }
        }
    }

    private fun playSongs(songs: List<SongEntity>, startIndex: Int) {
        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.path.toUri())
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.imageUrl?.toUri())
                        .build()
                )
                .build()
        }
        musicController.playQueue(mediaItems, startIndex)
    }
}
