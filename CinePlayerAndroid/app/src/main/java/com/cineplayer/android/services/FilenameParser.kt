package com.cineplayer.android.services

import com.cineplayer.android.models.MediaType

data class ParsedMediaInfo(
    val title: String,
    val year: Int? = null,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val quality: String? = null
)

object FilenameParser {
    private val QUALITY_MARKERS = listOf(
        "2160p", "4K", "UHD", "1080p", "1080i", "720p", "480p", "360p",
        "BluRay", "BRRip", "BDRip", "WEB-DL", "WEBRip", "HDTV", "DVDRip",
        "x264", "x265", "HEVC", "H264", "H265", "AAC", "AC3", "DTS", "HDR"
    )

    private val SE_PATTERN = Regex("""[Ss](\d{1,2})[Ee](\d{1,2})""")
    private val X_PATTERN = Regex("""(\d{1,2})x(\d{2})""", RegexOption.IGNORE_CASE)
    private val SEASON_EP_PATTERN = Regex("""[Ss]eason\s*(\d+)\s*[Ee]pisode\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val YEAR_PATTERN = Regex("""(?<!\d)(19|20)\d{2}(?!\d)""")

    fun parse(filename: String): ParsedMediaInfo {
        val name = filename.substringBeforeLast(".")
        val quality = extractQuality(name)

        SE_PATTERN.find(name)?.let { match ->
            val season = match.groupValues[1].toInt()
            val episode = match.groupValues[2].toInt()
            val title = cleanTitle(name.substring(0, match.range.first))
            return ParsedMediaInfo(title = title, mediaType = MediaType.EPISODE,
                seasonNumber = season, episodeNumber = episode, quality = quality)
        }

        X_PATTERN.find(name)?.let { match ->
            val season = match.groupValues[1].toInt()
            val episode = match.groupValues[2].toInt()
            val title = cleanTitle(name.substring(0, match.range.first))
            return ParsedMediaInfo(title = title, mediaType = MediaType.EPISODE,
                seasonNumber = season, episodeNumber = episode, quality = quality)
        }

        SEASON_EP_PATTERN.find(name)?.let { match ->
            val season = match.groupValues[1].toInt()
            val episode = match.groupValues[2].toInt()
            val title = cleanTitle(name.substring(0, match.range.first))
            return ParsedMediaInfo(title = title, mediaType = MediaType.EPISODE,
                seasonNumber = season, episodeNumber = episode, quality = quality)
        }

        val year = extractYear(name)
        val title = if (year != null) cleanTitle(name.substringBefore(year.toString()))
                    else cleanTitle(name)
        return ParsedMediaInfo(title = title.ifBlank { name }, year = year,
            mediaType = MediaType.MOVIE, quality = quality)
    }

    private fun extractYear(text: String): Int? = YEAR_PATTERN.find(text)?.value?.toIntOrNull()

    private fun extractQuality(text: String): String? =
        QUALITY_MARKERS.firstOrNull { text.contains(it, ignoreCase = true) }

    fun cleanTitle(raw: String): String {
        var cleaned = raw
        for (marker in QUALITY_MARKERS) {
            cleaned = cleaned.replace(Regex("""\b${Regex.escape(marker)}\b""", RegexOption.IGNORE_CASE), " ")
        }
        cleaned = cleaned.replace(Regex("""[._\-]+"""), " ")
        cleaned = cleaned.replace(Regex("""\[.*?]|\(.*?\)"""), " ")
        cleaned = cleaned.trim().replace(Regex("""\s+"""), " ")
        return toTitleCase(cleaned)
    }

    private fun toTitleCase(text: String): String {
        val articles = setOf("a", "an", "the", "and", "but", "or", "in", "on", "at", "to", "for", "of", "with")
        return text.split(" ").mapIndexed { index, word ->
            if (index == 0 || word.lowercase() !in articles)
                word.replaceFirstChar { it.uppercaseChar() }
            else word.lowercase()
        }.joinToString(" ")
    }

    fun normalizeName(name: String): String =
        name.lowercase().replace(Regex("""^the\s+"""), "").replace(Regex("""[^a-z0-9]"""), "")
}
