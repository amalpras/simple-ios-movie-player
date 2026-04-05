package com.cineplayer.android.services

import android.content.Context
import com.cineplayer.android.data.AppSettings
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.TVSeries
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.io.FileOutputStream

data class TMDBMovieResult(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("overview") val overview: String = "",
    @SerializedName("release_date") val releaseDate: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList()
)

data class TMDBTVResult(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("overview") val overview: String = "",
    @SerializedName("first_air_date") val firstAirDate: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0
)

data class TMDBMovieDetail(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("overview") val overview: String = "",
    @SerializedName("release_date") val releaseDate: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("genres") val genres: List<TMDBGenre> = emptyList()
)

data class TMDBTVDetail(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("overview") val overview: String = "",
    @SerializedName("first_air_date") val firstAirDate: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double = 0.0,
    @SerializedName("genres") val genres: List<TMDBGenre> = emptyList(),
    @SerializedName("status") val status: String? = null,
    @SerializedName("networks") val networks: List<TMDBNetwork> = emptyList()
)

data class TMDBEpisodeDetail(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("overview") val overview: String = "",
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
    @SerializedName("air_date") val airDate: String = "",
    @SerializedName("still_path") val stillPath: String? = null
)

data class TMDBGenre(@SerializedName("id") val id: Int = 0, @SerializedName("name") val name: String = "")
data class TMDBNetwork(@SerializedName("id") val id: Int = 0, @SerializedName("name") val name: String = "")
data class TMDBSearchMovieResponse(@SerializedName("results") val results: List<TMDBMovieResult> = emptyList())
data class TMDBSearchTVResponse(@SerializedName("results") val results: List<TMDBTVResult> = emptyList())
data class TMDBGenreListResponse(@SerializedName("genres") val genres: List<TMDBGenre> = emptyList())

interface TMDBApi {
    @GET("search/movie")
    suspend fun searchMovies(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("year") year: Int? = null): TMDBSearchMovieResponse
    @GET("search/tv")
    suspend fun searchTV(@Query("api_key") apiKey: String, @Query("query") query: String, @Query("first_air_date_year") year: Int? = null): TMDBSearchTVResponse
    @GET("movie/{id}")
    suspend fun getMovieDetail(@Path("id") id: Int, @Query("api_key") apiKey: String): TMDBMovieDetail
    @GET("tv/{id}")
    suspend fun getTVDetail(@Path("id") id: Int, @Query("api_key") apiKey: String): TMDBTVDetail
    @GET("tv/{id}/season/{season}/episode/{episode}")
    suspend fun getEpisodeDetail(@Path("id") seriesId: Int, @Path("season") season: Int, @Path("episode") episode: Int, @Query("api_key") apiKey: String): TMDBEpisodeDetail
    @GET("genre/movie/list")
    suspend fun getMovieGenres(@Query("api_key") apiKey: String): TMDBGenreListResponse
    @GET("genre/tv/list")
    suspend fun getTVGenres(@Query("api_key") apiKey: String): TMDBGenreListResponse
}

class MetadataService private constructor(private val context: Context) {
    private val settings = AppSettings.getInstance(context)
    private val posterCacheDir = File(context.cacheDir, "posters").also { it.mkdirs() }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val api: TMDBApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TMDBApi::class.java)

    companion object {
        @Volatile private var instance: MetadataService? = null
        fun getInstance(context: Context): MetadataService =
            instance ?: synchronized(this) { instance ?: MetadataService(context.applicationContext).also { instance = it } }

        const val IMAGE_BASE = "https://image.tmdb.org/t/p"
        const val POSTER_SIZE = "w342"
        const val BACKDROP_SIZE = "w780"
    }

    suspend fun fetchMovieMetadata(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey.ifBlank { throw Exception("TMDB API key not configured") }
        val results = api.searchMovies(apiKey, item.title, item.releaseYear)
        val top = bestMovieResult(results.results, item.title, item.releaseYear)
            ?: throw Exception("No results found for: ${item.title}")
        val detail = api.getMovieDetail(top.id, apiKey)
        detail.posterPath?.let { downloadImage("$IMAGE_BASE/$POSTER_SIZE$it", it.trimStart('/')) }
        detail.backdropPath?.let { downloadImage("$IMAGE_BASE/$BACKDROP_SIZE$it", "bd_${it.trimStart('/')}") }
        item.copy(
            tmdbId = detail.id,
            overview = detail.overview.ifBlank { null },
            releaseYear = detail.releaseDate.take(4).toIntOrNull(),
            posterPath = detail.posterPath?.trimStart('/'),
            backdropPath = detail.backdropPath?.trimStart('/'),
            rating = if (detail.voteAverage > 0) detail.voteAverage else null,
            genres = detail.genres.map { it.name }
        )
    }

    suspend fun fetchSeriesMetadata(series: TVSeries): TVSeries = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey.ifBlank { throw Exception("TMDB API key not configured") }
        val results = api.searchTV(apiKey, series.name, series.firstAirYear)
        val top = bestTVResult(results.results, series.name, series.firstAirYear)
            ?: throw Exception("No results found for: ${series.name}")
        val detail = api.getTVDetail(top.id, apiKey)
        detail.posterPath?.let { downloadImage("$IMAGE_BASE/$POSTER_SIZE$it", it.trimStart('/')) }
        detail.backdropPath?.let { downloadImage("$IMAGE_BASE/$BACKDROP_SIZE$it", "bd_${it.trimStart('/')}") }
        series.copy(
            tmdbId = detail.id,
            overview = detail.overview.ifBlank { null },
            firstAirYear = detail.firstAirDate.take(4).toIntOrNull(),
            posterPath = detail.posterPath?.trimStart('/'),
            backdropPath = detail.backdropPath?.trimStart('/'),
            rating = if (detail.voteAverage > 0) detail.voteAverage else null,
            genres = detail.genres.map { it.name },
            status = detail.status,
            network = detail.networks.firstOrNull()?.name
        )
    }

    suspend fun fetchEpisodeDetail(tmdbSeriesId: Int, season: Int, episode: Int): TMDBEpisodeDetail = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey.ifBlank { throw Exception("TMDB API key not configured") }
        api.getEpisodeDetail(tmdbSeriesId, season, episode, apiKey)
    }

    private suspend fun downloadImage(url: String, filename: String) = withContext(Dispatchers.IO) {
        val file = File(posterCacheDir, filename)
        if (file.exists()) return@withContext
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()?.let { bytes -> FileOutputStream(file).use { it.write(bytes) } }
                }
            }
        } catch (_: Exception) {}
    }

    fun getPosterUrl(posterPath: String?): String? {
        posterPath ?: return null
        return "$IMAGE_BASE/$POSTER_SIZE/${posterPath.trimStart('/')}"
    }

    fun getBackdropUrl(backdropPath: String?): String? {
        backdropPath ?: return null
        return "$IMAGE_BASE/$BACKDROP_SIZE/${backdropPath.trimStart('/')}"
    }

    fun getCachedPosterFile(posterPath: String): File? {
        val file = File(posterCacheDir, posterPath.trimStart('/'))
        return if (file.exists()) file else null
    }

    // MARK: - Best-match scoring

    private fun bestMovieResult(results: List<TMDBMovieResult>, query: String, year: Int?): TMDBMovieResult? =
        results.maxByOrNull { movieScore(it, query, year) }

    private fun movieScore(r: TMDBMovieResult, query: String, year: Int?): Int {
        var s = titleScore(r.title, query)
        val ry = r.releaseDate.take(4).toIntOrNull()
        if (year != null && ry != null) s += if (year == ry) 30 else if (kotlin.math.abs(year - ry) <= 1) 10 else 0
        return s
    }

    private fun bestTVResult(results: List<TMDBTVResult>, query: String, year: Int?): TMDBTVResult? =
        results.maxByOrNull { tvScore(it, query, year) }

    private fun tvScore(r: TMDBTVResult, query: String, year: Int?): Int {
        var s = titleScore(r.name, query)
        val ry = r.firstAirDate.take(4).toIntOrNull()
        if (year != null && ry != null) s += if (year == ry) 30 else if (kotlin.math.abs(year - ry) <= 1) 10 else 0
        return s
    }

    private fun titleScore(title: String, query: String): Int {
        val t = title.lowercase().trim()
        val q = query.lowercase().trim()
        if (t == q) return 100
        if (t.startsWith(q) || q.startsWith(t)) return 70
        if (t.contains(q) || q.contains(t)) return 40
        val tWords = t.split(" ").toSet()
        val qWords = q.split(" ").toSet()
        return tWords.intersect(qWords).size * 10
    }
}
