package com.cineplayer.android.models

data class PlaybackState(
    val itemId: String,
    var position: Long = 0L,
    var duration: Long = 0L,
    var isCompleted: Boolean = false,
    var lastUpdated: Long = System.currentTimeMillis()
) {
    val progressPercentage: Double
        get() = if (duration > 0) position.toDouble() / duration.toDouble() else 0.0

    val remainingTime: Long
        get() = if (duration > position) duration - position else 0L

    val shouldResume: Boolean
        get() = position > 5000L && !isCompleted && progressPercentage < 0.95
}
