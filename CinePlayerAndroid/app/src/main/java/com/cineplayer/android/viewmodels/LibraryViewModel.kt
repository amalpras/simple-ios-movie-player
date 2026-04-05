package com.cineplayer.android.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cineplayer.android.data.AppSettings
import com.cineplayer.android.data.LibraryViewStyle
import com.cineplayer.android.data.SortOrder
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.PlaybackState
import com.cineplayer.android.models.TVSeries
import com.cineplayer.android.services.MediaLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val library = MediaLibrary.getInstance(application)
    private val settings = AppSettings.getInstance(application)

    val isScanning: StateFlow<Boolean> = library.isScanning
    val continueWatching: StateFlow<List<MediaItem>> = library.continueWatching
    val recentlyAdded: StateFlow<List<MediaItem>> = library.recentlyAdded

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _viewStyle = MutableStateFlow(settings.libraryViewStyle)
    val viewStyle: StateFlow<LibraryViewStyle> = _viewStyle.asStateFlow()

    private val _sortOrder = MutableStateFlow(settings.sortOrder)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val filteredMovies: StateFlow<List<MediaItem>> = combine(
        library.movies, _searchQuery, _sortOrder
    ) { movies, query, sort ->
        val filtered = if (query.isBlank()) movies
        else movies.filter { it.title.contains(query, ignoreCase = true) }
        filtered.sortedWith(sort.comparator())
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredSeries: StateFlow<List<TVSeries>> = combine(
        library.series, _searchQuery
    ) { series, query ->
        if (query.isBlank()) series
        else series.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setViewStyle(style: LibraryViewStyle) {
        settings.libraryViewStyle = style
        _viewStyle.value = style
    }

    fun setSortOrder(order: SortOrder) {
        settings.sortOrder = order
        _sortOrder.value = order
    }

    fun addMedia(uris: List<Uri>) {
        viewModelScope.launch { library.addMedia(uris) }
    }

    fun deleteItem(itemId: String) {
        library.removeItem(itemId)
    }

    fun markWatched(itemId: String) {
        library.markWatched(itemId)
    }

    fun resetProgress(itemId: String) {
        library.resetProgress(itemId)
    }

    fun refreshMetadata(itemId: String) {
        viewModelScope.launch { library.refreshMetadata(itemId) }
    }

    fun refreshSubtitle(itemId: String) {
        viewModelScope.launch { library.fetchSubtitleForItem(itemId) }
    }

    fun getPlaybackState(itemId: String): com.cineplayer.android.models.PlaybackState? = library.getPlaybackState(itemId)

    fun getSeries(seriesId: String): com.cineplayer.android.models.TVSeries? = library.seriesItem(seriesId)

    fun getEpisodes(seriesId: String, season: Int): List<MediaItem> =
        library.episodes(seriesId, season)

    private fun SortOrder.comparator(): Comparator<MediaItem> = when (this) {
        SortOrder.TITLE -> compareBy { it.title }
        SortOrder.RECENTLY_WATCHED -> compareByDescending { it.lastWatchedDate ?: 0L }
        SortOrder.RECENTLY_ADDED -> compareByDescending { it.dateAdded }
    }
}
