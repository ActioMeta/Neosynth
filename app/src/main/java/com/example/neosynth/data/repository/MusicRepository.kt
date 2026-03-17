package com.example.neosynth.data.repository
import com.example.neosynth.data.local.MusicDao
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.local.entities.PlaylistEntity
import com.example.neosynth.data.local.entities.PlaylistSongCrossRef
import com.example.neosynth.data.local.entities.PlaylistWithSongs
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.mappers.toSongEntities
import com.example.neosynth.data.remote.mappers.toArtistEntities
import com.example.neosynth.data.remote.mappers.toAlbumEntities
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: NavidromeApiService,
    private val musicDao: MusicDao
) {
    fun getAllSongs(): Flow<List<SongEntity>> = musicDao.getAllSongs()

    fun getDownloadedSongs(): Flow<List<SongEntity>> = musicDao.getDownloadedSongs()

    suspend fun getRandomDownloadedSongs(limit: Int): List<SongEntity> = musicDao.getRandomDownloadedSongs(limit)

    suspend fun getDownloadedSongsByAlbum(albumId: String): List<SongEntity> = musicDao.getDownloadedSongsByAlbum(albumId)

    fun getRecentlyDownloadedSongs(limit: Int): Flow<List<SongEntity>> = musicDao.getRecentlyDownloadedSongs(limit)

    suspend fun getSongById(songId: String): SongEntity? {
        return musicDao.getSongById(songId)
    }

    suspend fun deleteSong(songId: String) {
        musicDao.deleteSong(songId)
    }
    
    suspend fun deleteAllDownloadedSongs() {
        musicDao.deleteAllDownloadedSongs()
    }

    suspend fun insertSong(song: SongEntity) {
        musicDao.insertSong(song)
    }
    // Sync playlist from server (registers playlist + songs as references, does NOT download audio)
    suspend fun syncPlaylist(playlistId: String, username: String, token: String, salt: String, serverId: Long) {
        try {
            // 1. Fetch playlist details from server
            val response = api.getPlaylist(
                playlistId = playlistId,
                u = username,
                t = token,
                s = salt
            )
            
            val playlist = response.response.playlistDetails
            if (playlist == null) {
                android.util.Log.e("MusicRepository", "Playlist not found in server response")
                return
            }
            
            // 2. Insert playlist entity
            val playlistEntity = PlaylistEntity(
                id = playlist.id,
                serverId = serverId,
                name = playlist.name,
                songCount = playlist.entry?.size ?: 0,
                coverArt = playlist.entry?.firstOrNull()?.coverArt
            )
            musicDao.insertPlaylist(playlistEntity)
            
            // 3. Insert songs as references (isDownloaded = false, path = "")
            // Check existing songs to preserve downloaded state
            val songs = playlist.entry ?: emptyList()
            val newSongs = mutableListOf<SongEntity>()
            
            for (song in songs) {
                val existingSong = musicDao.getSongById(song.id)
                if (existingSong == null) {
                    // New song - insert as reference (not downloaded)
                    newSongs.add(
                        SongEntity(
                            id = song.id,
                            title = song.title,
                            serverID = 0L,
                            sourceType = "SUBSONIC",
                            sourceId = serverId.toString(),
                            artistID = song.artistId ?: "",
                            artist = song.artist,
                            albumID = song.albumId ?: "",
                            album = song.album,
                            duration = song.duration.toLong(),
                            imageUrl = song.coverArt,
                            path = "",
                            isDownloaded = false
                        )
                    )
                }
                // If song exists, skip (preserve its current state)
            }
            
            if (newSongs.isNotEmpty()) {
                musicDao.insertSongs(newSongs)
            }
            
            // 4. Insert cross references to maintain playlist order
            val crossRefs = songs.mapIndexed { index, song ->
                PlaylistSongCrossRef(
                    playlistId = playlist.id,
                    songId = song.id,
                    position = index
                )
            }
            musicDao.insertPlaylistSongCrossRefs(crossRefs)
            
            android.util.Log.d("MusicRepository", "✅ Playlist '${playlist.name}' synced (${songs.size} songs, ${newSongs.size} new)")
            
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "Failed to sync playlist", e)
            throw e
        }
    }

    suspend fun fetchSongs(query: String, user: String, token: String, salt: String, serverId: Long) {
        try {
            val resp = api.searchSongs(query, user, token, salt)

            val songEntities = resp.toSongEntities(serverId)
            val artistEntities = resp.toArtistEntities(serverId)
            val albumEntities = resp.toAlbumEntities(serverId)

            musicDao.insertArtists(artistEntities)
            musicDao.insertAlbums(albumEntities)
            musicDao.insertSongs(songEntities)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Playlist methods
    suspend fun insertPlaylist(playlist: com.example.neosynth.data.local.entities.PlaylistEntity) {
        musicDao.insertPlaylist(playlist)
    }

    fun getPlaylistByIdFlow(playlistId: String): Flow<com.example.neosynth.data.local.entities.PlaylistEntity?> {
        return musicDao.getPlaylistByIdFlow(playlistId)
    }
    
    suspend fun insertPlaylists(playlists: List<com.example.neosynth.data.local.entities.PlaylistEntity>) {
        musicDao.insertPlaylists(playlists)
    }
    
    fun getPlaylistsByServer(serverId: Long) = musicDao.getPlaylistsByServer(serverId)
    
    suspend fun getPlaylistById(playlistId: String) = musicDao.getPlaylistById(playlistId)
    
    suspend fun getPlaylistWithSongs(playlistId: String) = musicDao.getPlaylistWithSongs(playlistId)
    
    fun getPlaylistsWithSongs(serverId: Long) = musicDao.getPlaylistsWithSongs(serverId)
    
    suspend fun insertPlaylistSongCrossRef(crossRef: com.example.neosynth.data.local.entities.PlaylistSongCrossRef) {
        musicDao.insertPlaylistSongCrossRef(crossRef)
    }
    
    suspend fun insertPlaylistSongCrossRefs(crossRefs: List<com.example.neosynth.data.local.entities.PlaylistSongCrossRef>) {
        musicDao.insertPlaylistSongCrossRefs(crossRefs)
    }
    
    suspend fun deletePlaylistSongs(playlistId: String) {
        musicDao.deletePlaylistSongs(playlistId)
    }
    
    suspend fun deletePlaylist(playlistId: String) {
        musicDao.deletePlaylist(playlistId)
    }
    
    fun getSongsInPlaylist(playlistId: String) = musicDao.getSongsInPlaylist(playlistId)

    suspend fun getPlaylistDownloadedCount(playlistId: String): Int {
        return musicDao.getPlaylistDownloadedCount(playlistId)
    }
    
    // Favorites methods
    suspend fun addToFavorites(songId: String) {
        musicDao.addToFavorites(songId)
    }
    
    suspend fun removeFromFavorites(songId: String) {
        musicDao.removeFromFavorites(songId)
    }
    
    fun getFavoriteSongs(): Flow<List<SongEntity>> = musicDao.getFavoriteSongs()
    
    suspend fun isFavorite(songId: String): Boolean {
        return musicDao.isFavorite(songId) ?: false
    }

    suspend fun updateSongDownloadState(songId: String, path: String, imageUrl: String?, isDownloaded: Boolean, downloadedAt: Long?, metadata: String? = null) {
        musicDao.updateSongDownloadState(songId, path, imageUrl, isDownloaded, downloadedAt, metadata)
    }

    // Sync Actions
    suspend fun insertPendingSyncAction(action: com.example.neosynth.data.local.entities.PendingSyncActionEntity) {
        musicDao.insertPendingSyncAction(action)
    }

    suspend fun getPendingSyncActions(): List<com.example.neosynth.data.local.entities.PendingSyncActionEntity> {
        return musicDao.getPendingSyncActions()
    }

    suspend fun updatePendingSyncActionState(actionId: Int, isProcessing: Boolean) {
        musicDao.updatePendingSyncActionState(actionId, isProcessing)
    }

    suspend fun deletePendingSyncAction(actionId: Int) {
        musicDao.deletePendingSyncAction(actionId)
    }
}