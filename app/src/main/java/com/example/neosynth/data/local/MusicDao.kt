package com.example.neosynth.data.local

import androidx.room.*
import com.example.neosynth.data.local.entities.AlbumEntity
import com.example.neosynth.data.local.entities.ArtistEntity
import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.local.entities.PlaylistEntity
import com.example.neosynth.data.local.entities.PlaylistSongCrossRef
import com.example.neosynth.data.local.entities.PlaylistWithSongs
import com.example.neosynth.data.local.entities.PendingSyncActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1")
    fun getDownloadedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY downloadedAt DESC LIMIT :limit")
    fun getRecentlyDownloadedSongs(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomDownloadedSongs(limit: Int): List<SongEntity>

    @Query("SELECT * FROM songs WHERE albumID = :albumId AND isDownloaded = 1")
    suspend fun getDownloadedSongsByAlbum(albumId: String): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    suspend fun getSongById(songId: String): SongEntity?

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)
    
    @Query("DELETE FROM songs WHERE isDownloaded = 1")
    suspend fun deleteAllDownloadedSongs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Query("SELECT * FROM songs WHERE albumID = :albumId")
    fun getSongsByAlbum(albumId: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists WHERE serverId = :serverId")
    fun getArtistsByServer(serverId: Long): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Query("SELECT * FROM albums WHERE artistId = :artistId")
    fun getAlbumsByArtist(artistId: String): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    
    @Query("SELECT * FROM playlists WHERE serverId = :serverId")
    fun getPlaylistsByServer(serverId: Long): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?
    
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistByIdFlow(playlistId: String): Flow<PlaylistEntity?>
    
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithSongs(playlistId: String): PlaylistWithSongs?
    
    @Transaction
    @Query("SELECT * FROM playlists WHERE serverId = :serverId")
    fun getPlaylistsWithSongs(serverId: Long): Flow<List<PlaylistWithSongs>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongCrossRefs(crossRefs: List<PlaylistSongCrossRef>)
    
    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: String)
    
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)
    
    @Query("SELECT * FROM songs WHERE id IN (SELECT songId FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY position)")
    fun getSongsInPlaylist(playlistId: String): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId WHERE playlist_song_cross_ref.playlistId = :playlistId AND songs.isDownloaded = 1")
    suspend fun getPlaylistDownloadedCount(playlistId: String): Int
    
    // Favorites
    @Query("UPDATE songs SET isFavorite = 1 WHERE id = :songId")
    suspend fun addToFavorites(songId: String)
    
    @Query("UPDATE songs SET isFavorite = 0 WHERE id = :songId")
    suspend fun removeFromFavorites(songId: String)
    
    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>
    
    @Query("SELECT isFavorite FROM songs WHERE id = :songId")
    suspend fun isFavorite(songId: String): Boolean?

    @Query("UPDATE songs SET path = :path, imageUrl = :imageUrl, isDownloaded = :isDownloaded, downloadedAt = :downloadedAt, metadata = :metadata WHERE id = :songId")
    suspend fun updateSongDownloadState(songId: String, path: String, imageUrl: String?, isDownloaded: Boolean, downloadedAt: Long?, metadata: String?)

    // Sync Actions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSyncAction(action: PendingSyncActionEntity)

    @Query("SELECT * FROM pending_sync_actions WHERE isProcessing = 0 ORDER BY createdAt ASC")
    suspend fun getPendingSyncActions(): List<PendingSyncActionEntity>

    @Query("UPDATE pending_sync_actions SET isProcessing = :isProcessing WHERE id = :actionId")
    suspend fun updatePendingSyncActionState(actionId: Int, isProcessing: Boolean)

    @Query("DELETE FROM pending_sync_actions WHERE id = :actionId")
    suspend fun deletePendingSyncAction(actionId: Int)
}