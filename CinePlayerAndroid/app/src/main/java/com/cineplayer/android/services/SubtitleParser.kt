package com.cineplayer.android.services

data class SubtitleCue(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

object SubtitleParser {
    fun parse(content: String, filePath: String): List<SubtitleCue> {
        val ext = filePath.substringAfterLast(".").lowercase()
        return when (ext) {
            "vtt" -> parseVTT(content)
            "ass", "ssa" -> parseASS(content)
            else -> parseSRT(content)
        }
    }

    fun parseSRT(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.trim().split(Regex("""\r?\n\r?\n+"""))
        for (block in blocks) {
            val lines = block.trim().split(Regex("""\r?\n"""))
            if (lines.size < 2) continue
            val id = lines[0].trim().toIntOrNull() ?: continue
            val timeLine = lines.getOrNull(1) ?: continue
            val times = parseTimeRange(timeLine, "-->", ::parseSRTTime) ?: continue
            val text = lines.drop(2).joinToString("\n").trim()
            if (text.isNotEmpty()) cues.add(SubtitleCue(id, times.first, times.second, stripHtmlTags(text)))
        }
        return cues
    }

    fun parseVTT(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = content.lines().toMutableList()
        if (lines.firstOrNull()?.startsWith("WEBVTT") == true) lines.removeAt(0)
        val blocks = lines.joinToString("\n").split(Regex("""\n\n+"""))
        var idCounter = 1
        for (block in blocks) {
            val blockLines = block.trim().lines()
            if (blockLines.isEmpty()) continue
            val timeLineIdx = blockLines.indexOfFirst { "-->" in it }
            if (timeLineIdx < 0) continue
            val times = parseTimeRange(blockLines[timeLineIdx], "-->", ::parseVTTTime) ?: continue
            val text = blockLines.drop(timeLineIdx + 1).joinToString("\n").trim()
            if (text.isNotEmpty()) cues.add(SubtitleCue(idCounter++, times.first, times.second, stripHtmlTags(text)))
        }
        return cues
    }

    fun parseASS(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var inEvents = false
        var formatLine: List<String>? = null
        var idCounter = 1
        for (line in content.lines()) {
            if (line.trim() == "[Events]") { inEvents = true; continue }
            if (!inEvents) continue
            if (line.startsWith("Format:")) {
                formatLine = line.removePrefix("Format:").split(",").map { it.trim() }
                continue
            }
            if (!line.startsWith("Dialogue:")) continue
            val fmt = formatLine ?: continue
            val parts = line.removePrefix("Dialogue:").split(",", limit = fmt.size)
            val startIdx = fmt.indexOf("Start")
            val endIdx = fmt.indexOf("End")
            val textIdx = fmt.indexOf("Text")
            if (startIdx < 0 || endIdx < 0 || textIdx < 0) continue
            if (parts.size <= maxOf(startIdx, endIdx, textIdx)) continue
            val startMs = parseASSTime(parts[startIdx].trim()) ?: continue
            val endMs = parseASSTime(parts[endIdx].trim()) ?: continue
            val text = parts[textIdx].trim()
                .replace(Regex("""\{[^}]*\}"""), "")
                .replace("""\N""", "\n").trim()
            if (text.isNotEmpty()) cues.add(SubtitleCue(idCounter++, startMs, endMs, text))
        }
        return cues.sortedBy { it.startTimeMs }
    }

    private fun parseTimeRange(line: String, sep: String, parser: (String) -> Long?): Pair<Long, Long>? {
        val parts = line.split(sep)
        if (parts.size < 2) return null
        val start = parser(parts[0].trim()) ?: return null
        val end = parser(parts[1].trim().substringBefore(" ")) ?: return null
        return Pair(start, end)
    }

    private fun parseSRTTime(time: String): Long? {
        val normalized = time.replace(',', '.')
        val parts = normalized.split(":")
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val sParts = parts[2].split(".")
        val s = sParts[0].toLongOrNull() ?: return null
        val ms = sParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
        return h * 3600000L + m * 60000L + s * 1000L + ms
    }

    private fun parseVTTTime(time: String): Long? {
        val parts = time.split(":")
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: return null
                val m = parts[1].toLongOrNull() ?: return null
                val sParts = parts[2].split(".")
                val s = sParts[0].toLongOrNull() ?: return null
                val ms = sParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
                h * 3600000L + m * 60000L + s * 1000L + ms
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: return null
                val sParts = parts[1].split(".")
                val s = sParts[0].toLongOrNull() ?: return null
                val ms = sParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
                m * 60000L + s * 1000L + ms
            }
            else -> null
        }
    }

    private fun parseASSTime(time: String): Long? {
        val parts = time.split(":")
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val sParts = parts[2].split(".")
        val s = sParts[0].toLongOrNull() ?: return null
        val cs = sParts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
        return h * 3600000L + m * 60000L + s * 1000L + cs * 10L
    }

    private fun stripHtmlTags(text: String): String = text.replace(Regex("""<[^>]+>"""), "")
}
