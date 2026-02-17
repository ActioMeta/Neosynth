package com.example.neosynth.ui.playlist.logic

import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.PlaylistEntity
import com.example.neosynth.data.local.entities.PlaylistSongCrossRef
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.responses.PlaylistDto
import com.example.neosynth.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistManagementHandler @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository
) {

    private val _availablePlaylists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    val availablePlaylists: StateFlow<List<PlaylistDto>> = _availablePlaylists

    fun loadAvailablePlaylists(
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
                val response = api.getPlaylists(
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                _availablePlaylists.value = response.response.playlistsContainer?.playlist ?: emptyList()
            } catch (e: Exception) {
                android.util.Log.e("PlaylistManagementHandler", "Failed to load playlists", e)
                e.printStackTrace()
            }
        }
    }

    fun removeSongFromPlaylist(
        songIndex: Int,
        playlistId: String,
        cachedServer: ServerEntity?,
        scope: CoroutineScope,
        onComplete: suspend () -> Unit
    ) {
        scope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch

                api.updatePlaylist(
                    playlistId = playlistId,
                    songIndexToRemove = songIndex,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )

                android.util.Log.d("PlaylistManagementHandler", "Removed song at index $songIndex from playlist $playlistId")

                // Callback to reload playlist
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("PlaylistManagementHandler", "Failed to remove song from playlist", e)
                e.printStackTrace()
            }
        }
    }

    fun syncPlaylist(
        playlistId: String,
        cachedServer: ServerEntity?,
        scope: CoroutineScope,
        onComplete: suspend (List<com.example.neosynth.data.remote.responses.SongDto>) -> Unit
    ) {
        scope.launch {
            try {
                val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch

                android.util.Log.d("PlaylistManagementHandler", "🔄 Syncing playlist $playlistId from server...")

                val response = api.getPlaylist(
                    playlistId = playlistId,
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )

                val playlistDetails = response.response.playlistDetails

                if (playlistDetails != null) {
                    val songs = playlistDetails.entry ?: emptyList()

                    // Update playlist entity in DB
                    val playlistEntity = PlaylistEntity(
                        id = playlistDetails.id,
                        name = playlistDetails.name,
                        serverId = server.id,
                        coverArt = songs.firstOrNull()?.coverArt,
                        songCount = songs.size
                    )
                    musicRepository.insertPlaylist(playlistEntity)

                    // First, insert or update all songs in the database
                    var newSongsCount = 0
                    var updatedSongsCount = 0

                    songs.forEach { song ->
                        val existingSong = musicRepository.getSongById(song.id)
                        if (existingSong == null) {
                            // New song: insert into DB
                            val songEntity = SongEntity(
                                id = song.id,
                                title = song.title,
                                serverID = 0L,
                                sourceType = "SUBSONIC",
                                sourceId = server.id.toString(),
                                artistID = song.artistId ?: "",
                                artist = song.artist ?: "Unknown Artist",
                                albumID = song.albumId ?: "",
                                album = song.album ?: "Unknown Album",
                                duration = song.duration.toLong(),
                                imageUrl = song.coverArt,
                                path = "",
                                isDownloaded = false
                            )
                            musicRepository.insertSong(songEntity)
                            newSongsCount++
                            android.util.Log.d("PlaylistManagementHandler", "  + Inserted new song: ${song.title}")
                        } else {
                            // Existing song: update metadata (preserves download state!)
                            val updatedSong = existingSong.copy(
                                title = song.title,
                                artist = song.artist ?: "Unknown Artist",
                                album = song.album ?: "Unknown Album",
                                duration = song.duration.toLong(),
                                imageUrl = song.coverArt
                            )
                            musicRepository.insertSong(updatedSong)
                            updatedSongsCount++
                            android.util.Log.d("PlaylistManagementHandler", "  ~ Updated existing song: ${song.title} (downloaded: ${existingSong.isDownloaded})")
                        }
                    }

                    android.util.Log.d("PlaylistManagementHandler", "✅ Inserted $newSongsCount new songs, updated $updatedSongsCount existing songs")

                    // Then, clear old cross-refs and insert new ones
                    musicRepository.deletePlaylistSongs(playlistId)

                    val crossRefs = songs.mapIndexed { index, song ->
                        PlaylistSongCrossRef(
                            playlistId = playlistId,
                            songId = song.id,
                            position = index
                        )
                    }
                    musicRepository.insertPlaylistSongCrossRefs(crossRefs)

                    android.util.Log.d("PlaylistManagementHandler", "✅ Synced ${songs.size} songs in playlist")
                    android.util.Log.d("PlaylistManagementHandler", "📊 Summary: $newSongsCount new, $updatedSongsCount updated, ${songs.size - newSongsCount - updatedSongsCount} unchanged")

                    // Callback with updated songs
                    onComplete(songs)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaylistManagementHandler", "Failed to sync playlist", e)
                e.printStackTrace()
            }
        }
    }
}
