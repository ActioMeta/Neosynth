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
}