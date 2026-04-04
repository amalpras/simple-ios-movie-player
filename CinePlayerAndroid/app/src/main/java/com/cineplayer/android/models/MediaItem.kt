package com.cineplayer.android.models

import java.util.UUID

data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    var fileUri: String = "",
    var title: String = "",
    var mediaType: MediaType = MediaType.UNKNOWN,
    var seriesName: String? = null,
    var seasonNumber: Int? = null,
    var episodeNumber: Int? = null,
    var episodeTitle: String? = null,
    var tmdbId: Int? = null,
    var overview: String? = null,
    var releaseYear: Int? = null,
    var posterPath: String? = null,
    var backdropPath: String? = null,
    var rating: Double? = null,
    var genres: List<String> = emptyList(),
    var subtitleTracks: List<SubtitleTrack> = emptyList(),
    var selectedSubtitleIndex: Int? = null,
    var lastPlaybackPosition: Long = 0L,
    var isWatched: Boolean = false,
    var dateAdded: Long = System.currentTimeMillis(),
    var lastWatchedDate: Long? = null,
    var selectedAudioTrackIndex: Int? = null,
    var duration: Long = 0L,
    var fileSize: Long = 0L
) {
    val displayTitle: String
        get() = when {
            mediaType == MediaType.EPISODE && seasonNumber != null && episodeNumber != null -> {
                val epLabel = "S%02dE%02d".format(seasonNumber, episodeNumber)
                if (!episodeTitle.isNullOrBlank()) "$epLabel - $episodeTitle" else epLabel
            }
            else -> title
        }

    val progressPercentage: Double
        get() = if (duration > 0) lastPlaybackPosition.toDouble() / duration.toDouble() else 0.0
}

enum class MediaType { MOVIE, EPISODE, UNKNOWN }

data class SubtitleTrack(
    val id: String = UUID.randomUUID().toString(),
    var language: String = "Unknown",
    var languageCode: String = "en",
    var filePath: String? = null,
    var isEmbedded: Boolean = false,
    var source: SubtitleSource = SubtitleSource.LOCAL
)

enum class SubtitleSource { EMBEDDED, LOCAL, DOWNLOADED }
