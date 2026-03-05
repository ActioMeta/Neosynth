package com.example.neosynth.ui.playlist.logic

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.PendingSyncActionEntity
import com.example.neosynth.data.local.entities.PlaylistSongCrossRef
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.repository.MusicRepository
import com.example.neosynth.data.worker.PlaylistSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistFavoritesHandler @Inject constructor(
    private val api: NavidromeApiService,
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context
) {

    fun addToFavorites(
        songIds: Set<String>,
        playlistId: String,
        cachedServer: ServerEntity?,
        scope: CoroutineScope,
        onComplete: suspend () -> Unit
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch

            // Add to local database
            songIds.forEach { songId ->
                try {
                    musicRepository.addToFavorites(songId)
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistFavoritesHandler", "Failed to add to favorites: $songId", e)
                    e.printStackTrace()
                }
            }

            // Sync with Navidrome server (batch operation)
            if (songIds.isNotEmpty()) {
                try {
                    android.util.Log.d("PlaylistFavoritesHandler", "Starring songs on server - IDs: ${songIds.joinToString(", ")}")
                    val response = api.star(
                        id = songIds.toList(),
                        u = server.username,
                        t = server.token,
                        s = server.salt
                    )
                    android.util.Log.d("PlaylistFavoritesHandler", "Starred ${songIds.size} songs on server - Status: ${response.response.status}")

                    // Callback to reload playlist data
                    onComplete()
                } catch (e: Exception) {
                    android.util.Log.e("PlaylistFavoritesHandler", "Failed to star songs on server", e)
                }
            }

            android.util.Log.d("PlaylistFavoritesHandler", "Successfully added ${songIds.size} songs to favorites")
        }
    }

    fun addToPlaylist(
        songIds: Set<String>,
        targetPlaylistId: String,
        cachedServer: ServerEntity?,
        scope: CoroutineScope
    ) {
        scope.launch {
            val server = cachedServer ?: serverDao.getActiveServer() ?: return@launch
            
            // 1. Calculate new Offline insertions
            val crossRefsFlow = musicRepository.getSongsInPlaylist(targetPlaylistId)
            val existingSongs = crossRefsFlow.firstOrNull() ?: emptyList()
            var nextPosition = existingSongs.size
            
            val newCrossRefs = songIds.map { songId ->
                PlaylistSongCrossRef(
                    playlistId = targetPlaylistId,
                    songId = songId,
                    position = nextPosition++
                )
            }
            musicRepository.insertPlaylistSongCrossRefs(newCrossRefs)
            
            // 2. Queue action
            val payloadObj = JSONObject().apply {
                put("playlistId", targetPlaylistId)
                
                val songsArray = JSONArray()
                songIds.forEach { songsArray.put(it) }
                put("songIds", songsArray)
            }
            
            val pendingAction = PendingSyncActionEntity(
                serverId = server.id,
                actionType = "ADD_SONG",
                payload = payloadObj.toString()
            )
            musicRepository.insertPendingSyncAction(pendingAction)

            try {
                api.addToPlaylist(
                    playlistId = targetPlaylistId,
                    songIds = songIds.toList(),
                    u = server.username,
                    t = server.token,
                    s = server.salt
                )
            } catch (e: Exception) {
                android.util.Log.e("PlaylistFavoritesHandler", "Direct sync failed, offline queued instead: ${e.message}")
            }
            
            // Trigger SyncWorker
            val syncRequest = OneTimeWorkRequestBuilder<PlaylistSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(syncRequest)
        }
    }
}
