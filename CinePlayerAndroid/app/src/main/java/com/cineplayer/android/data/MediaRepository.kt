package com.cineplayer.android.data

import android.content.Context
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.MediaType
import com.cineplayer.android.models.TVSeries
import com.cineplayer.android.services.FilenameParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MediaRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("cineplayer_library", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        @Volatile private var instance: MediaRepository? = null
        fun getInstance(context: Context): MediaRepository =
            instance ?: synchronized(this) {
                instance ?: MediaRepository(context.applicationContext).also { instance = it }
            }
    }

    fun getAllMovies(): List<MediaItem> =
        getAllItems().filter { it.mediaType == MediaType.MOVIE }

    fun getAllEpisodes(): List<MediaItem> =
        getAllItems().filter { it.mediaType == MediaType.EPISODE }

    fun getAllItems(): List<MediaItem> {
        val json = prefs.getString("items", null) ?: return emptyList()
        val type = object : TypeToken<List<MediaItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun getItemById(id: String): MediaItem? = getAllItems().find { it.id == id }

    fun saveItem(item: MediaItem) {
        val items = getAllItems().toMutableList()
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx >= 0) items[idx] = item else items.add(item)
        persistItems(items)
    }

    fun deleteItem(id: String) {
        val items = getAllItems().filter { it.id != id }
        persistItems(items)
    }

    fun updatePlaybackPosition(id: String, position: Long, duration: Long) {
        val item = getItemById(id) ?: return
        item.lastPlaybackPosition = position
        item.duration = duration
        item.lastWatchedDate = System.currentTimeMillis()
        if (duration > 0 && position.toDouble() / duration.toDouble() >= 0.95) {
            item.isWatched = true
        }
        saveItem(item)
    }

    private fun persistItems(items: List<MediaItem>) {
        prefs.edit().putString("items", gson.toJson(items)).apply()
    }

    fun getAllSeries(): List<TVSeries> {
        val json = prefs.getString("series", null) ?: return emptyList()
        val type = object : TypeToken<List<TVSeries>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun getSeriesById(id: String): TVSeries? = getAllSeries().find { it.id == id }

    fun saveSeries(series: TVSeries) {
        val all = getAllSeries().toMutableList()
        val idx = all.indexOfFirst { it.id == series.id }
        if (idx >= 0) all[idx] = series else all.add(series)
        prefs.edit().putString("series", gson.toJson(all)).apply()
    }

    fun addMediaItem(uri: String, filename: String): MediaItem {
        val parsed = FilenameParser.parse(filename)
        val item = MediaItem(
            fileUri = uri,
            title = parsed.title,
            mediaType = parsed.mediaType,
            seasonNumber = parsed.seasonNumber,
            episodeNumber = parsed.episodeNumber,
            releaseYear = parsed.year
        )
        if (item.mediaType == MediaType.EPISODE) {
            linkEpisodeToSeries(item)
        }
        saveItem(item)
        return item
    }

    private fun linkEpisodeToSeries(episode: MediaItem) {
        val seriesName = episode.title
        val normalizedName = FilenameParser.normalizeName(seriesName)
        val existingSeries = getAllSeries().find { it.normalizedName == normalizedName }
        if (existingSeries != null) {
            episode.seriesName = existingSeries.name
            val updated = existingSeries.copy(
                episodeIds = (existingSeries.episodeIds + episode.id).distinct()
            )
            saveSeries(updated)
        } else {
            episode.seriesName = seriesName
            val newSeries = TVSeries(
                name = seriesName,
                normalizedName = normalizedName,
                episodeIds = listOf(episode.id)
            )
            saveSeries(newSeries)
        }
    }
}
