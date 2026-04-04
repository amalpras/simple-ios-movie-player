package com.cineplayer.android.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cineplayer.android.data.MediaRepository
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.TVSeries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SeriesDetailUiState(
    val series: TVSeries? = null,
    val episodesBySeason: Map<Int, List<MediaItem>> = emptyMap(),
    val isLoading: Boolean = true
)

class SeriesDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    fun loadSeries(seriesId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val series = repository.getSeriesById(seriesId)
            val episodes = series?.episodeIds
                ?.mapNotNull { repository.getItemById(it) }
                ?: emptyList()
            val bySeason = episodes
                .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                .groupBy { it.seasonNumber ?: 0 }
            _uiState.value = SeriesDetailUiState(
                series = series,
                episodesBySeason = bySeason,
                isLoading = false
            )
        }
    }
}
