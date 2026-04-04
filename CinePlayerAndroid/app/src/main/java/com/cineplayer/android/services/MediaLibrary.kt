package com.cineplayer.android.services

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.edit
import com.cineplayer.android.data.AppSettings
import com.cineplayer.android.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MediaLibrary private constructor(private val context: Context) {
    private val settings = AppSettings.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("cineplayer_library", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _movies = MutableStateFlow<List<MediaItem>>(emptyList())
    val movies: StateFlow<List<MediaItem>> = _movies.asStateFlow()

    private val _series = MutableStateFlow<List<TVSeries>>(emptyList())
    val series: StateFlow<List<TVSeries>> = _series.asStateFlow()

    private val _allItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val allItems: StateFlow<List<MediaItem>> = _allItems.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<MediaItem>>(emptyList())
    val continueWatching: StateFlow<List<MediaItem>> = _continueWatching.asStateFlow()

    private val _recentlyAdded = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentlyAdded: StateFlow<List<MediaItem>> = _recentlyAdded.asStateFlow()

    private val itemsMap = mutableMapOf<String, MediaItem>()
    private val seriesMap = mutableMapOf<String, TVSeries>()
    private val playbackStates = mutableMapOf<String, PlaybackState>()

    companion object {
        @Volatile private var instance: MediaLibrary? = null
        fun getInstance(context: Context): MediaLibrary =
            instance ?: synchronized(this) { instance ?: MediaLibrary(context.applicationContext).also { instance = it } }

        private const val KEY_ITEMS = "media_items"
        private const val KEY_SERIES = "tv_series"
        private const val KEY_STATES = "playback_states"
    }

    init { loadFromStorage() }

    private fun loadFromStorage() {
        prefs.getString(KEY_ITEMS, null)?.let { json ->
            try { gson.fromJson<List<MediaItem>>(json, object : TypeToken<List<MediaItem>>() {}.type)?.forEach { itemsMap[it.id] = it } } catch (_: Exception) {}
        }
        prefs.getString(KEY_SERIES, null)?.let { json ->
            try { gson.fromJson<List<TVSeries>>(json, object : TypeToken<List<TVSeries>>() {}.type)?.forEach { seriesMap[it.id] = it } } catch (_: Exception) {}
        }
        prefs.getString(KEY_STATES, null)?.let { json ->
            try { gson.fromJson<List<PlaybackState>>(json, object : TypeToken<List<PlaybackState>>() {}.type)?.forEach { playbackStates[it.itemId] = it } } catch (_: Exception) {}
        }
        updatePublishedState()
    }

    private fun saveToStorage() {
        prefs.edit {
            putString(KEY_ITEMS, gson.toJson(itemsMap.values.toList()))
            putString(KEY_SERIES, gson.toJson(seriesMap.values.toList()))
            putString(KEY_STATES, gson.toJson(playbackStates.values.toList()))
        }
    }

    private fun updatePublishedState() {
        val all = itemsMap.values.toList()
        _allItems.value = all
        _movies.value = all.filter { it.mediaType == MediaType.MOVIE || it.mediaType == MediaType.UNKNOWN }
        _series.value = seriesMap.values.sortedByDescending { it.dateAdded }
        _continueWatching.value = all.filter { item ->
            val s = playbackStates[item.id]
            s != null && s.position > 5000L && !s.isCompleted && s.progressPercentage < 0.95
        }.sortedByDescending { playbackStates[it.id]?.lastUpdated }.take(20)
        _recentlyAdded.value = all.sortedByDescending { it.dateAdded }.take(20)
    }

    suspend fun addMedia(uri: Uri) = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        if (itemsMap.values.any { it.fileUri == uriString }) return@withContext
        val displayName = getDisplayName(uri)
        val parsed = FilenameParser.parse(displayName)
        val item = MediaItem(
            fileUri = uriString,
            title = parsed.title.ifBlank { displayName.substringBeforeLast(".") },
            mediaType = parsed.mediaType,
            seriesName = if (parsed.mediaType == MediaType.EPISODE) parsed.title else null,
            seasonNumber = parsed.seasonNumber,
            episodeNumber = parsed.episodeNumber,
            episodeTitle = parsed.episodeTitle,
            releaseYear = parsed.year,
            dateAdded = System.currentTimeMillis(),
            fileSize = getFileSize(uri)
        )
        itemsMap[item.id] = item
        organizeSeries(item)
        updatePublishedState()
        saveToStorage()
        if (settings.autoFetchMetadata) scope.launch { fetchMetadataForItem(item.id) }
        if (settings.autoFetchSubtitles) scope.launch { fetchSubtitleForItem(item.id) }
    }

    suspend fun addMedia(uris: List<Uri>) {
        _isScanning.value = true
        uris.forEach { addMedia(it) }
        _isScanning.value = false
    }

    fun removeItem(itemId: String) {
        val item = itemsMap.remove(itemId) ?: return
        playbackStates.remove(itemId)
        if (item.mediaType == MediaType.EPISODE && item.seriesName != null) {
            val seriesId = findSeriesId(item.seriesName!!)
            seriesId?.let { sid ->
                val s = seriesMap[sid] ?: return@let
                val newIds = s.episodeIds.filter { it != itemId }
                if (newIds.isEmpty()) seriesMap.remove(sid)
                else seriesMap[sid] = s.copy(
                    episodeIds = newIds,
                    seasons = s.seasons.mapValues { (_, ids) -> ids.filter { it != itemId } }.filter { it.value.isNotEmpty() }
                )
            }
        }
        updatePublishedState()
        scope.launch { saveToStorage() }
    }

    private fun organizeSeries(item: MediaItem) {
        if (item.mediaType != MediaType.EPISODE || item.seriesName == null) return
        val normalized = FilenameParser.normalizeName(item.seriesName!!)
        val existing = seriesMap.values.find { it.normalizedName == normalized }
        val season = item.seasonNumber ?: 1
        if (existing != null) {
            val newSeasons = existing.seasons.toMutableMap()
            newSeasons[season] = (newSeasons.getOrDefault(season, emptyList()) + item.id).distinct()
            seriesMap[existing.id] = existing.copy(
                episodeIds = (existing.episodeIds + item.id).distinct(),
                seasons = newSeasons
            )
        } else {
            val s = TVSeries(name = item.seriesName!!, normalizedName = normalized, episodeIds = listOf(item.id), seasons = mapOf(season to listOf(item.id)))
            seriesMap[s.id] = s
        }
    }

    private fun findSeriesId(name: String): String? {
        val normalized = FilenameParser.normalizeName(name)
        return seriesMap.values.find { it.normalizedName == normalized }?.id
    }

    fun updatePlaybackPosition(itemId: String, positionMs: Long, durationMs: Long) {
        val isCompleted = durationMs > 0 && positionMs.toDouble() / durationMs >= 0.9
        playbackStates[itemId] = PlaybackState(itemId, positionMs, durationMs, isCompleted, System.currentTimeMillis())
        itemsMap[itemId]?.let { item ->
            itemsMap[itemId] = item.copy(
                lastPlaybackPosition = positionMs,
                isWatched = isCompleted,
                duration = if (durationMs > 0) durationMs else item.duration,
                lastWatchedDate = System.currentTimeMillis()
            )
        }
        updatePublishedState()
        scope.launch { saveToStorage() }
    }

    fun getPlaybackState(itemId: String): PlaybackState? = playbackStates[itemId]
    fun markWatched(itemId: String) {
        playbackStates[itemId] = PlaybackState(itemId, isCompleted = true)
        itemsMap[itemId]?.let { itemsMap[itemId] = it.copy(isWatched = true) }
        updatePublishedState()
        scope.launch { saveToStorage() }
    }
    fun resetProgress(itemId: String) {
        playbackStates[itemId] = PlaybackState(itemId)
        itemsMap[itemId]?.let { itemsMap[itemId] = it.copy(lastPlaybackPosition = 0L, isWatched = false) }
        updatePublishedState()
        scope.launch { saveToStorage() }
    }

    suspend fun fetchMetadataForItem(itemId: String) = withContext(Dispatchers.IO) {
        val item = itemsMap[itemId] ?: return@withContext
        val svc = MetadataService.getInstance(context)
        try {
            if (item.mediaType == MediaType.EPISODE) {
                val seriesId = findSeriesId(item.seriesName ?: return@withContext) ?: return@withContext
                val s = seriesMap[seriesId] ?: return@withContext
                val updatedSeries = if (s.tmdbId == null) svc.fetchSeriesMetadata(s).also { seriesMap[seriesId] = it } else s
                updatedSeries.tmdbId?.let { tmdbId ->
                    if (item.seasonNumber != null && item.episodeNumber != null) {
                        val ep = svc.fetchEpisodeDetail(tmdbId, item.seasonNumber!!, item.episodeNumber!!)
                        itemsMap[itemId] = item.copy(
                            episodeTitle = ep.name.ifBlank { null },
                            overview = ep.overview.ifBlank { null },
                            posterPath = ep.stillPath?.trimStart('/') ?: updatedSeries.posterPath,
                            backdropPath = updatedSeries.backdropPath,
                            rating = updatedSeries.rating,
                            genres = updatedSeries.genres
                        )
                    }
                }
            } else {
                itemsMap[itemId] = svc.fetchMovieMetadata(item)
            }
            updatePublishedState()
            saveToStorage()
        } catch (_: Exception) {}
    }

    suspend fun refreshMetadata(itemId: String) {
        itemsMap[itemId]?.let { itemsMap[itemId] = it.copy(tmdbId = null, overview = null, posterPath = null, backdropPath = null, rating = null, genres = emptyList()) }
        fetchMetadataForItem(itemId)
    }

    suspend fun refreshSeriesMetadata(seriesId: String) {
        val s = seriesMap[seriesId] ?: return
        seriesMap[seriesId] = s.copy(tmdbId = null, overview = null, posterPath = null, backdropPath = null, rating = null)
        val svc = MetadataService.getInstance(context)
        try {
            seriesMap[seriesId] = svc.fetchSeriesMetadata(seriesMap[seriesId]!!)
            s.episodeIds.forEach { fetchMetadataForItem(it) }
            updatePublishedState()
            saveToStorage()
        } catch (_: Exception) {}
    }

    suspend fun fetchSubtitleForItem(itemId: String) = withContext(Dispatchers.IO) {
        val item = itemsMap[itemId] ?: return@withContext
        val svc = SubtitleService.getInstance(context)
        try {
            val track = svc.findAndDownloadSubtitle(item) ?: return@withContext
            val updatedTracks = (item.subtitleTracks + track).distinctBy { it.filePath }
            itemsMap[itemId] = item.copy(
                subtitleTracks = updatedTracks,
                selectedSubtitleIndex = item.selectedSubtitleIndex ?: 0
            )
            updatePublishedState()
            saveToStorage()
        } catch (_: Exception) {}
    }

    fun item(id: String): MediaItem? = itemsMap[id]
    fun seriesItem(id: String): TVSeries? = seriesMap[id]

    fun episodes(seriesId: String, season: Int? = null): List<MediaItem> {
        val s = seriesMap[seriesId] ?: return emptyList()
        val ids = if (season != null) s.seasons[season] ?: emptyList() else s.episodeIds
        return ids.mapNotNull { itemsMap[it] }.sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
    }

    fun nextEpisode(itemId: String): MediaItem? {
        val item = itemsMap[itemId] ?: return null
        if (item.mediaType != MediaType.EPISODE || item.seriesName == null) return null
        val seriesId = findSeriesId(item.seriesName!!) ?: return null
        val all = episodes(seriesId).sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
        val idx = all.indexOfFirst { it.id == itemId }
        return if (idx >= 0 && idx < all.size - 1) all[idx + 1] else null
    }

    private fun getDisplayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Unknown"
    } catch (_: Exception) { uri.lastPathSegment ?: "Unknown" }

    private fun getFileSize(uri: Uri): Long = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        } ?: 0L
    } catch (_: Exception) { 0L }
}
