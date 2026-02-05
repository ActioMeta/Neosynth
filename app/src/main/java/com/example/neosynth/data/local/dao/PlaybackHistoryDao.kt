package com.example.neosynth.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.neosynth.data.local.entities.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Insert
    suspend fun insert(playbackHistory: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<PlaybackHistoryEntity>>

    // Get Top Artists
    @Query("SELECT artist, COUNT(*) as playCount FROM playback_history GROUP BY artist ORDER BY playCount DESC LIMIT :limit")
    fun getTopArtists(limit: Int): Flow<List<ArtistPlayCount>>

    // Get Top Songs
    @Query("SELECT songId, title, artist, COUNT(*) as playCount FROM playback_history GROUP BY songId ORDER BY playCount DESC LIMIT :limit")
    fun getTopSongs(limit: Int): Flow<List<SongPlayCount>>

    // Get Top Genres (Requires JOIN with songs table)
    @Query("""
        SELECT s.genre, COUNT(ph.id) as playCount 
        FROM playback_history ph
        INNER JOIN songs s ON ph.songId = s.id
        WHERE s.genre IS NOT NULL AND s.genre != ''
        GROUP BY s.genre 
        ORDER BY playCount DESC 
        LIMIT :limit
    """)
    fun getTopGenres(limit: Int): Flow<List<GenrePlayCount>>

    // Get songs never listened to (Songs NOT IN history)
    @Query("""
        SELECT * FROM songs 
        WHERE id NOT IN (SELECT DISTINCT songId FROM playback_history) 
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    fun getNeverListenedSongs(limit: Int): Flow<List<com.example.neosynth.data.local.entities.SongEntity>>

    // Recommendations based on Top Genres
    @Query("""
        SELECT * FROM songs 
        WHERE genre IN (
            SELECT s.genre 
            FROM playback_history ph
            INNER JOIN songs s ON ph.songId = s.id
            WHERE s.genre IS NOT NULL
            GROUP BY s.genre 
            ORDER BY COUNT(ph.id) DESC 
            LIMIT 3
        )
        AND id NOT IN (SELECT DISTINCT songId FROM playback_history)
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    fun getRecommendationsByTopGenres(limit: Int): Flow<List<com.example.neosynth.data.local.entities.SongEntity>>

    // Recommendations based on Top Artists
    @Query("""
        SELECT * FROM songs 
        WHERE artist IN (
            SELECT artist 
            FROM playback_history 
            GROUP BY artist 
            ORDER BY COUNT(id) DESC 
            LIMIT 5
        )
        AND id NOT IN (SELECT DISTINCT songId FROM playback_history)
        ORDER BY RANDOM() 
        LIMIT :limit
    """)
    fun getRecommendationsByTopArtists(limit: Int): Flow<List<com.example.neosynth.data.local.entities.SongEntity>>
}

data class ArtistPlayCount(val artist: String, val playCount: Int)
data class SongPlayCount(val songId: String, val title: String, val artist: String, val playCount: Int)
data class GenrePlayCount(val genre: String, val playCount: Int)
