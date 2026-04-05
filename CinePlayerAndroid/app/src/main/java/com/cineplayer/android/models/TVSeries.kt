package com.cineplayer.android.models

import java.util.UUID

data class TVSeries(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var normalizedName: String = "",
    var tmdbId: Int? = null,
    var overview: String? = null,
    var firstAirYear: Int? = null,
    var posterPath: String? = null,
    var backdropPath: String? = null,
    var rating: Double? = null,
    var genres: List<String> = emptyList(),
    var status: String? = null,
    var network: String? = null,
    var seasons: Map<Int, List<String>> = emptyMap(),
    var episodeIds: List<String> = emptyList(),
    var dateAdded: Long = System.currentTimeMillis(),
    var lastWatchedDate: Long? = null,
    var nextEpisodeId: String? = null
) {
    val totalEpisodeCount: Int get() = episodeIds.size
    val seasonCount: Int get() = seasons.keys.size
}
