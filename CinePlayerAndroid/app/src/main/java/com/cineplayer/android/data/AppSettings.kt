package com.cineplayer.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class LibraryViewStyle { GRID, LIST }
enum class SortOrder { RECENTLY_ADDED, TITLE, RECENTLY_WATCHED }

class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cineplayer_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var instance: AppSettings? = null
        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }

        const val KEY_TMDB_API_KEY = "tmdb_api_key"
        const val KEY_OPENSUBTITLES_API_KEY = "opensubtitles_api_key"
        const val KEY_OPENSUBTITLES_USERNAME = "opensubtitles_username"
        const val KEY_OPENSUBTITLES_PASSWORD = "opensubtitles_password"
        const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        const val KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size"
        const val KEY_SUBTITLE_COLOR = "subtitle_color"
        const val KEY_SUBTITLE_BG_OPACITY = "subtitle_bg_opacity"
        const val KEY_SUBTITLE_DELAY = "subtitle_delay"
        const val KEY_DEFAULT_SPEED = "default_speed"
        const val KEY_SKIP_FORWARD = "skip_forward"
        const val KEY_SKIP_BACKWARD = "skip_backward"
        const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        const val KEY_AUTO_FETCH_SUBTITLES = "auto_fetch_subtitles"
        const val KEY_AUTO_FETCH_METADATA = "auto_fetch_metadata"
        const val KEY_RESUME_PLAYBACK = "resume_playback"
        const val KEY_COUNTDOWN_NEXT = "countdown_next"
        const val KEY_LIBRARY_VIEW_STYLE = "library_view_style"
        const val KEY_SORT_ORDER = "sort_order"
    }

    var tmdbApiKey: String
        get() = prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_TMDB_API_KEY, value) }

    var openSubtitlesApiKey: String
        get() = prefs.getString(KEY_OPENSUBTITLES_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_OPENSUBTITLES_API_KEY, value) }

    var openSubtitlesUsername: String
        get() = prefs.getString(KEY_OPENSUBTITLES_USERNAME, "") ?: ""
        set(value) = prefs.edit { putString(KEY_OPENSUBTITLES_USERNAME, value) }

    var openSubtitlesPassword: String
        get() = prefs.getString(KEY_OPENSUBTITLES_PASSWORD, "") ?: ""
        set(value) = prefs.edit { putString(KEY_OPENSUBTITLES_PASSWORD, value) }

    var preferredSubtitleLanguage: String
        get() = prefs.getString(KEY_SUBTITLE_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit { putString(KEY_SUBTITLE_LANGUAGE, value) }

    var subtitleFontSize: Float
        get() = prefs.getFloat(KEY_SUBTITLE_FONT_SIZE, 18f)
        set(value) = prefs.edit { putFloat(KEY_SUBTITLE_FONT_SIZE, value) }

    var subtitleTextColor: String
        get() = prefs.getString(KEY_SUBTITLE_COLOR, "white") ?: "white"
        set(value) = prefs.edit { putString(KEY_SUBTITLE_COLOR, value) }

    var subtitleBackgroundOpacity: Float
        get() = prefs.getFloat(KEY_SUBTITLE_BG_OPACITY, 0.5f)
        set(value) = prefs.edit { putFloat(KEY_SUBTITLE_BG_OPACITY, value) }

    var subtitleDelayMs: Long
        get() = prefs.getLong(KEY_SUBTITLE_DELAY, 0L)
        set(value) = prefs.edit { putLong(KEY_SUBTITLE_DELAY, value) }

    var defaultPlaybackSpeed: Float
        get() = prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_DEFAULT_SPEED, value) }

    var skipForwardSeconds: Int
        get() = prefs.getInt(KEY_SKIP_FORWARD, 30)
        set(value) = prefs.edit { putInt(KEY_SKIP_FORWARD, value) }

    var skipBackwardSeconds: Int
        get() = prefs.getInt(KEY_SKIP_BACKWARD, 10)
        set(value) = prefs.edit { putInt(KEY_SKIP_BACKWARD, value) }

    var autoPlayNextEpisode: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_PLAY_NEXT, value) }

    var autoFetchSubtitles: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FETCH_SUBTITLES, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_FETCH_SUBTITLES, value) }

    var autoFetchMetadata: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FETCH_METADATA, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_FETCH_METADATA, value) }

    var resumePlayback: Boolean
        get() = prefs.getBoolean(KEY_RESUME_PLAYBACK, true)
        set(value) = prefs.edit { putBoolean(KEY_RESUME_PLAYBACK, value) }

    var countdownToNextEpisode: Int
        get() = prefs.getInt(KEY_COUNTDOWN_NEXT, 5)
        set(value) = prefs.edit { putInt(KEY_COUNTDOWN_NEXT, value) }

    var libraryViewStyle: LibraryViewStyle
        get() = LibraryViewStyle.valueOf(
            prefs.getString(KEY_LIBRARY_VIEW_STYLE, LibraryViewStyle.GRID.name) ?: LibraryViewStyle.GRID.name
        )
        set(value) = prefs.edit { putString(KEY_LIBRARY_VIEW_STYLE, value.name) }

    var sortOrder: SortOrder
        get() = SortOrder.valueOf(
            prefs.getString(KEY_SORT_ORDER, SortOrder.RECENTLY_ADDED.name) ?: SortOrder.RECENTLY_ADDED.name
        )
        set(value) = prefs.edit { putString(KEY_SORT_ORDER, value.name) }
}
