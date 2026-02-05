package com.example.neosynth.data.repository

import com.example.neosynth.data.local.dao.ArtistPlayCount
import com.example.neosynth.data.local.dao.GenrePlayCount
import com.example.neosynth.data.local.dao.PlaybackHistoryDao
import com.example.neosynth.data.local.dao.SongPlayCount
import com.example.neosynth.data.local.entities.PlaybackHistoryEntity
import com.example.neosynth.data.mappers.toDomain
import com.example.neosynth.domain.model.Song
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class StatsRepository @Inject constructor(
    private val playbackHistoryDao: PlaybackHistoryDao
) {
    suspend fun recordPlayback(
        songId: String,
        title: String,
        artist: String,
        durationListened: Long
    ) {
        val history = PlaybackHistoryEntity(
            songId = songId,
            title = title,
            artist = artist,
            timestamp = System.currentTimeMillis(),
            durationListened = durationListened
        )
        playbackHistoryDao.insert(history)
    }

    fun getRecentHistory(limit: Int = 20): Flow<List<PlaybackHistoryEntity>> {
        return playbackHistoryDao.getRecentHistory(limit)
    }

    fun getTopArtists(limit: Int = 5): Flow<List<ArtistPlayCount>> {
        return playbackHistoryDao.getTopArtists(limit)
    }

    fun getTopSongs(limit: Int = 5): Flow<List<SongPlayCount>> {
        return playbackHistoryDao.getTopSongs(limit)
    }

    fun getTopGenres(limit: Int = 5): Flow<List<GenrePlayCount>> {
        return playbackHistoryDao.getTopGenres(limit)
    }

    fun getNeverListenedSongs(limit: Int): Flow<List<Song>> {
        return playbackHistoryDao.getNeverListenedSongs(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getRecommendationsByTopGenres(limit: Int): Flow<List<Song>> {
        return playbackHistoryDao.getRecommendationsByTopGenres(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getRecommendationsByTopArtists(limit: Int): Flow<List<Song>> {
        return playbackHistoryDao.getRecommendationsByTopArtists(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
