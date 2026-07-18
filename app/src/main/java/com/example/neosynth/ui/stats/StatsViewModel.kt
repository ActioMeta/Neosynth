package com.example.neosynth.ui.stats

import androidx.lifecycle.ViewModel
import com.example.neosynth.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

enum class TimeFilter {
    DAY, WEEK, MONTH, YEAR, ALL
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(TimeFilter.WEEK)
    val activeFilter = _activeFilter.asStateFlow()

    fun setActiveFilter(filter: TimeFilter) {
        _activeFilter.value = filter
    }

    // Weekly minutes listened (starts Monday)
    val weeklyMinutesListened: Flow<Int> = statsRepository.getMinutesListened(
        getStartOfWeekTimestamp()
    ).map { durationMs ->
        if (durationMs != null) (durationMs / 60000).toInt() else 0
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val topSongsWithTime = activeFilter.flatMapLatest { filter ->
        statsRepository.getTopSongsWithTime(
            sinceTimestamp = getSinceTimestamp(filter),
            limit = 10
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val topArtistsWithTime = activeFilter.flatMapLatest { filter ->
        statsRepository.getTopArtistsWithTime(
            sinceTimestamp = getSinceTimestamp(filter),
            limit = 10
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val topGenresWithTime = activeFilter.flatMapLatest { filter ->
        statsRepository.getTopGenresWithTime(
            sinceTimestamp = getSinceTimestamp(filter),
            limit = 10
        )
    }

    fun getSinceTimestamp(filter: TimeFilter): Long {
        val now = LocalDateTime.now()
        return when (filter) {
            TimeFilter.DAY -> {
                now.withHour(0).withMinute(0).withSecond(0).withNano(0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
            TimeFilter.WEEK -> {
                getStartOfWeekTimestamp()
            }
            TimeFilter.MONTH -> {
                now.withDayOfMonth(1)
                    .withHour(0).withMinute(0).withSecond(0).withNano(0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
            TimeFilter.YEAR -> {
                now.withDayOfYear(1)
                    .withHour(0).withMinute(0).withSecond(0).withNano(0)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
            TimeFilter.ALL -> 0L
        }
    }

    private fun getStartOfWeekTimestamp(): Long {
        val now = LocalDateTime.now()
        val daysToSubtract = now.dayOfWeek.value - 1
        return now.minusDays(daysToSubtract.toLong())
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
