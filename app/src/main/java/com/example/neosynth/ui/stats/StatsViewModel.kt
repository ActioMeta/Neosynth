package com.example.neosynth.ui.stats

import androidx.lifecycle.ViewModel
import com.example.neosynth.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {
    val topArtists = statsRepository.getTopArtists(limit = 10)
    val topSongs = statsRepository.getTopSongs(limit = 10)
    val recentHistory = statsRepository.getRecentHistory(limit = 20)
}
