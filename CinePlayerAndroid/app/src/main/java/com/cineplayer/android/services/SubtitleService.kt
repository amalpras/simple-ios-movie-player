package com.cineplayer.android.services

import android.content.Context
import android.net.Uri
import com.cineplayer.android.data.AppSettings
import com.cineplayer.android.models.MediaItem
import com.cineplayer.android.models.MediaType
import com.cineplayer.android.models.SubtitleSource
import com.cineplayer.android.models.SubtitleTrack
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URL

data class OSLoginRequest(@SerializedName("username") val username: String, @SerializedName("password") val password: String)
data class OSLoginResponse(@SerializedName("token") val token: String? = null)
data class OSSearchResponse(@SerializedName("data") val data: List<OSSubtitleResult> = emptyList())
data class OSSubtitleResult(
    @SerializedName("id") val id: String = "",
    @SerializedName("attributes") val attributes: OSSubtitleAttributes = OSSubtitleAttributes()
)
data class OSSubtitleAttributes(
    @SerializedName("language") val language: String = "",
    @SerializedName("download_count") val downloadCount: Int = 0,
    @SerializedName("moviehash_match") val moviehashMatch: Boolean = false,
    @SerializedName("files") val files: List<OSSubtitleFile> = emptyList()
)
data class OSSubtitleFile(@SerializedName("file_id") val fileId: Int = 0, @SerializedName("file_name") val fileName: String = "")
data class OSDownloadRequest(@SerializedName("file_id") val fileId: Int)
data class OSDownloadResponse(@SerializedName("link") val link: String = "", @SerializedName("file_name") val fileName: String = "", @SerializedName("remaining") val remaining: Int = 0)

interface OpenSubtitlesApi {
    @POST("login")
    suspend fun login(@Body request: OSLoginRequest, @Header("Api-Key") apiKey: String): OSLoginResponse
    @GET("subtitles")
    suspend fun searchByHash(@Query("moviehash") hash: String, @Query("languages") language: String, @Header("Api-Key") apiKey: String, @Header("Authorization") token: String = ""): OSSearchResponse
    @GET("subtitles")
    suspend fun searchByTitle(@Query("query") query: String, @Query("languages") language: String, @Query("year") year: Int? = null, @Header("Api-Key") apiKey: String, @Header("Authorization") token: String = ""): OSSearchResponse
    @GET("subtitles")
    suspend fun searchByEpisode(@Query("query") query: String, @Query("season_number") season: Int, @Query("episode_number") episode: Int, @Query("languages") language: String, @Header("Api-Key") apiKey: String, @Header("Authorization") token: String = ""): OSSearchResponse
    @POST("download")
    suspend fun download(@Body request: OSDownloadRequest, @Header("Api-Key") apiKey: String, @Header("Authorization") token: String = ""): OSDownloadResponse
}

class SubtitleService private constructor(private val context: Context) {
    /** Thrown when the OpenSubtitles daily download quota (HTTP 429) is exceeded. */
    class RateLimitException : Exception("OpenSubtitles daily download limit reached")

    private val settings = AppSettings.getInstance(context)
    private var authToken: String = ""

    private val api: OpenSubtitlesApi = Retrofit.Builder()
        .baseUrl("https://api.opensubtitles.com/api/v1/")
        .client(OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenSubtitlesApi::class.java)

    companion object {
        @Volatile private var instance: SubtitleService? = null
        fun getInstance(context: Context): SubtitleService =
            instance ?: synchronized(this) { instance ?: SubtitleService(context.applicationContext).also { instance = it } }
    }

    suspend fun login(): Boolean {
        val apiKey = settings.openSubtitlesApiKey.ifBlank { return false }
        val username = settings.openSubtitlesUsername
        val password = settings.openSubtitlesPassword
        if (username.isBlank() || password.isBlank()) return false
        return try {
            val response = api.login(OSLoginRequest(username, password), apiKey)
            authToken = response.token?.let { "Bearer $it" } ?: ""
            authToken.isNotBlank()
        } catch (_: Exception) { false }
    }

    suspend fun findAndDownloadSubtitle(item: MediaItem): SubtitleTrack? = withContext(Dispatchers.IO) {
        val apiKey = settings.openSubtitlesApiKey.ifBlank { return@withContext null }
        val language = settings.preferredSubtitleLanguage

        findLocalSubtitle(item)?.let { return@withContext it }

        if (item.mediaType == MediaType.MOVIE) {
            val path = Uri.parse(item.fileUri).path
            if (path != null) {
                computeHash(File(path))?.let { hash ->
                    try {
                        val results = api.searchByHash(hash, language, apiKey, authToken)
                        val best = results.data.maxByOrNull { (if (it.attributes.moviehashMatch) 1000 else 0) + it.attributes.downloadCount }
                        if (best != null) downloadAndSave(best, item, apiKey)?.let { return@withContext it }
                    } catch (_: Exception) {}
                }
            }
        }

        if (item.mediaType == MediaType.EPISODE && item.seriesName != null && item.seasonNumber != null && item.episodeNumber != null) {
            try {
                val results = api.searchByEpisode(item.seriesName!!, item.seasonNumber!!, item.episodeNumber!!, language, apiKey, authToken)
                results.data.maxByOrNull { it.attributes.downloadCount }?.let { downloadAndSave(it, item, apiKey)?.let { t -> return@withContext t } }
            } catch (_: Exception) {}
        }

        try {
            val results = api.searchByTitle(item.title, language, item.releaseYear, apiKey, authToken)
            results.data.maxByOrNull { it.attributes.downloadCount }?.let { downloadAndSave(it, item, apiKey) }
        } catch (_: Exception) { null }
    }

    private fun findLocalSubtitle(item: MediaItem): SubtitleTrack? {
        val filePath = Uri.parse(item.fileUri).path ?: return null
        val dir = File(filePath).parentFile ?: return null
        val baseName = File(filePath).nameWithoutExtension
        val exts = listOf("srt", "vtt", "ass", "ssa")
        val candidate = dir.listFiles { f ->
            val name = f.nameWithoutExtension
            f.extension.lowercase() in exts && (name == baseName || name.startsWith("$baseName.") || name.startsWith("${baseName}_"))
        }?.firstOrNull() ?: return null
        val langCode = candidate.nameWithoutExtension.removePrefix(baseName).trimStart('.', '_').lowercase().take(3).ifBlank { "en" }
        return SubtitleTrack(language = langCode.uppercase(), languageCode = langCode, filePath = candidate.absolutePath, source = SubtitleSource.LOCAL)
    }

    private suspend fun downloadAndSave(result: OSSubtitleResult, item: MediaItem, apiKey: String): SubtitleTrack? {
        val fileId = result.attributes.files.firstOrNull()?.fileId ?: return null
        return try {
            val resp = api.download(OSDownloadRequest(fileId), apiKey, authToken)
            val link = resp.link.ifBlank { return null }
            val videoPath = Uri.parse(item.fileUri).path ?: return null
            val subtitlesDir = File(File(videoPath).parentFile, "Subtitles").also { it.mkdirs() }
            val fileName = resp.fileName.ifBlank { "${item.title}.${result.attributes.language}.srt" }
            val dest = File(subtitlesDir, fileName)
            FileOutputStream(dest).use { it.write(URL(link).readBytes()) }
            SubtitleTrack(language = result.attributes.language.uppercase(), languageCode = result.attributes.language.lowercase(), filePath = dest.absolutePath, source = SubtitleSource.DOWNLOADED)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 429) throw RateLimitException()
            null
        } catch (_: Exception) { null }
    }

    private fun computeHash(file: File): String? {
        if (!file.exists()) return null
        return try {
            val fileSize = file.length()
            val chunkSize = minOf(65536L, fileSize).toInt()
            var hash = fileSize
            RandomAccessFile(file, "r").use { raf ->
                val buf = ByteArray(chunkSize)
                raf.seek(0); raf.read(buf)
                for (i in buf.indices step 8) { hash = (hash + readLong(buf, i)) and Long.MAX_VALUE }
                raf.seek(maxOf(0, fileSize - chunkSize)); raf.read(buf)
                for (i in buf.indices step 8) { hash = (hash + readLong(buf, i)) and Long.MAX_VALUE }
            }
            "%016x".format(hash)
        } catch (_: Exception) { null }
    }

    private fun readLong(bytes: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until minOf(8, bytes.size - offset)) v = v or ((bytes[offset + i].toLong() and 0xFF) shl (i * 8))
        return v
    }
}
