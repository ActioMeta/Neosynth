package com.example.neosynth.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONObject

@HiltWorker
class PlaylistSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicRepository: MusicRepository,
    private val api: NavidromeApiService,
    private val serverDao: ServerDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val server = serverDao.getActiveServer() ?: return Result.failure()
            
            // 1. Verify Internet & Server Reachability via Ping
            try {
                val pingResponse = api.ping(
                    user = server.username,
                    token = server.token,
                    salt = server.salt
                )
                if (pingResponse.response.status != "ok") {
                    return Result.retry() // Server active but not ok
                }
            } catch (e: Exception) {
                // Server is unreachable, retry later
                return Result.retry()
            }

            // 2. Fetch Pending Queue
            val pendingActions = musicRepository.getPendingSyncActions()
            if (pendingActions.isEmpty()) return Result.success()

            android.util.Log.d("PlaylistSyncWorker", "Found ${pendingActions.size} pending actions.")

            var idRemappings = mutableMapOf<String, String>() // localUuid -> realServerId

            // 3. Process Actions
            for (action in pendingActions) {
                try {
                    musicRepository.updatePendingSyncActionState(action.id, isProcessing = true)
                    val payload = JSONObject(action.payload)

                    when (action.actionType) {
                        "CREATE_PLAYLIST" -> {
                            val name = payload.getString("name")
                            val localId = payload.getString("localId")

                            // Subsonic API doesn't return the ID on creation directly via standard spec, 
                            // we create it, then fetch playlists to find the new ID.
                            api.createPlaylist(
                                name = name,
                                u = server.username,
                                t = server.token,
                                s = server.salt
                            )
                            
                            // Let's refetch playlists to find real ID
                            val remotePlaylists = api.getPlaylists(
                                user = server.username,
                                token = server.token,
                                salt = server.salt
                            ).response.playlistsContainer?.playlist ?: emptyList()
                            
                            val newlyCreated = remotePlaylists.find { it.name == name }
                            if (newlyCreated != null) {
                                idRemappings[localId] = newlyCreated.id
                                
                                // Update Local DB
                                val localPlaylist = musicRepository.getPlaylistById(localId)
                                if (localPlaylist != null) {
                                    musicRepository.insertPlaylist(localPlaylist.copy(id = newlyCreated.id))
                                    musicRepository.deletePlaylist(localId) // remove old local
                                }
                            }
                        }

                        "UPDATE_PLAYLIST" -> {
                            var playlistId = payload.getString("playlistId")
                            val newName = payload.getString("newName")
                            
                            // If it's a local playlist, map it to the real ID
                            if (idRemappings.containsKey(playlistId)) {
                                playlistId = idRemappings[playlistId]!!
                            }

                            api.updatePlaylist(
                                playlistId = playlistId,
                                name = newName,
                                u = server.username,
                                t = server.token,
                                s = server.salt
                            )
                        }

                        "DELETE_PLAYLIST" -> {
                            var playlistId = payload.getString("playlistId")
                            if (idRemappings.containsKey(playlistId)) {
                                playlistId = idRemappings[playlistId]!!
                            }

                            if (!playlistId.startsWith("local-")) {
                                api.deletePlaylist(
                                    id = playlistId,
                                    u = server.username,
                                    t = server.token,
                                    s = server.salt
                                )
                            }
                        }

                        "ADD_SONG" -> {
                            var playlistId = payload.getString("playlistId")
                            val songIds = payload.getJSONArray("songIds")
                            val songIdList = mutableListOf<String>()
                            for (i in 0 until songIds.length()) {
                                songIdList.add(songIds.getString(i))
                            }
                            
                            if (idRemappings.containsKey(playlistId)) {
                                playlistId = idRemappings[playlistId]!!
                            }
                            
                            // Call API
                            api.addToPlaylist(
                                playlistId = playlistId,
                                songIds = songIdList,
                                u = server.username,
                                t = server.token,
                                s = server.salt
                            )
                        }

                        "REMOVE_SONG" -> {
                            var playlistId = payload.getString("playlistId")
                            val songIndexToRemove = payload.getInt("songIndexToRemove")
                            if (idRemappings.containsKey(playlistId)) {
                                playlistId = idRemappings[playlistId]!!
                            }
                            
                            if (!playlistId.startsWith("local-")) {
                                api.updatePlaylist(
                                    playlistId = playlistId,
                                    songIndexToRemove = songIndexToRemove,
                                    u = server.username,
                                    t = server.token,
                                    s = server.salt
                                )
                            }
                        }
                    }

                    // Success => delete the pending action
                    musicRepository.deletePendingSyncAction(action.id)

                } catch (e: Exception) {
                    android.util.Log.e("PlaylistSyncWorker", "Error processing action ${action.id}: ${e.message}")
                    musicRepository.updatePendingSyncActionState(action.id, isProcessing = false)
                    return Result.retry() 
                }
            }

            android.util.Log.d("PlaylistSyncWorker", "Sync complete.")
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
