import Foundation

// MARK: - FilenameParser
/// Parses video filenames to extract movie/series title, year, season, and episode information.
/// Handles common patterns used in media naming conventions (Scene naming, Plex naming, etc.)
struct FilenameParser {

    struct ParsedMediaInfo {
        var title: String
        var year: Int?
        var mediaType: MediaType
        var seasonNumber: Int?
        var episodeNumber: Int?
        var episodeTitle: String?
        var quality: String?      // e.g. "1080p", "4K", "720p"
        var releaseGroup: String? // e.g. "YIFY", "BluRay"
    }

    // MARK: - Public API

    static func parse(filename: String) -> ParsedMediaInfo {
        // Strip the file extension
        let nameWithoutExt = stripExtension(filename)

        // Attempt TV episode patterns first (more specific)
        if let tvInfo = parseTVEpisode(nameWithoutExt) {
            return tvInfo
        }

        // Then try movie patterns
        return parseMovie(nameWithoutExt)
    }

    // MARK: - TV Episode Parsing

    private static func parseTVEpisode(_ name: String) -> ParsedMediaInfo? {
        let normalized = name.replacingOccurrences(of: ".", with: " ")
                              .replacingOccurrences(of: "_", with: " ")

        // Pattern list: (regex, titleGroup, seasonGroup, episodeGroup)
        let patterns: [(String, Int, Int, Int)] = [
            // S01E01 or S01E01E02 (standard)
            (#"^(.*?)[.\s_](?:[Ss](\d{1,2})[Ee](\d{1,2}))"#, 1, 2, 3),
            // 1x01
            (#"^(.*?)[.\s_](\d{1,2})x(\d{2})"#, 1, 2, 3),
            // Season 1 Episode 1
            (#"^(.*?)[.\s_][Ss]eason[.\s_](\d{1,2})[.\s_][Ee]pisode[.\s_](\d{1,2})"#, 1, 2, 3),
            // 101 (no separator, e.g. Show.101)
            (#"^(.*?)[.\s_](\d)(\d{2})[.\s_]"#, 1, 2, 3),
        ]

        for (pattern, titleGroup, seasonGroup, episodeGroup) in patterns {
            if let match = normalized.range(of: pattern, options: .regularExpression) {
                let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive)
                let range = NSRange(normalized.startIndex..., in: normalized)
                if let result = regex?.firstMatch(in: normalized, range: range) {
                    let titleRange = result.range(at: titleGroup)
                    let seasonRange = result.range(at: seasonGroup)
                    let episodeRange = result.range(at: episodeGroup)

                    guard let titleR = Range(titleRange, in: normalized),
                          let seasonR = Range(seasonRange, in: normalized),
                          let episodeR = Range(episodeRange, in: normalized) else { continue }

                    let rawTitle = String(normalized[titleR])
                    let title = cleanTitle(rawTitle)
                    let season = Int(normalized[seasonR]) ?? 1
                    let episode = Int(normalized[episodeR]) ?? 1
                    let quality = extractQuality(normalized)

                    if title.isEmpty { continue }

                    return ParsedMediaInfo(
                        title: title,
                        year: extractYear(normalized),
                        mediaType: .episode,
                        seasonNumber: season,
                        episodeNumber: episode,
                        episodeTitle: nil,
                        quality: quality,
                        releaseGroup: extractReleaseGroup(normalized)
                    )
                }
                // Avoid unused variable warning
                _ = match
            }
        }

        return nil
    }

    // MARK: - Movie Parsing

    private static func parseMovie(_ name: String) -> ParsedMediaInfo {
        let normalized = name.replacingOccurrences(of: ".", with: " ")
                              .replacingOccurrences(of: "_", with: " ")

        let year = extractYear(normalized)
        let quality = extractQuality(normalized)
        let releaseGroup = extractReleaseGroup(normalized)

        // Extract title: everything before the year or quality marker
        var title = normalized
        let qualityKeywords = ["1080p", "720p", "480p", "2160p", "4K", "UHD",
                               "BluRay", "BRRip", "WEB-DL", "WEBRip", "HDRip",
                               "DVDRip", "HDTV", "REMUX", "x264", "x265",
                               "HEVC", "H264", "H265", "AAC", "AC3", "DTS",
                               "PROPER", "REPACK", "EXTENDED", "THEATRICAL"]

        if let y = year {
            let yearPattern = #"\b"# + String(y) + #"\b"#
            if let yearRange = title.range(of: yearPattern, options: .regularExpression) {
                title = String(title[..<yearRange.lowerBound])
            }
        } else {
            // Try to find quality marker position
            for keyword in qualityKeywords {
                if let range = title.range(of: keyword, options: .caseInsensitive) {
                    let candidate = String(title[..<range.lowerBound])
                    if !candidate.trimmingCharacters(in: .whitespaces).isEmpty {
                        title = candidate
                        break
                    }
                }
            }
        }

        return ParsedMediaInfo(
            title: cleanTitle(title),
            year: year,
            mediaType: .movie,
            seasonNumber: nil,
            episodeNumber: nil,
            episodeTitle: nil,
            quality: quality,
            releaseGroup: releaseGroup
        )
    }

    // MARK: - Helpers

    static func extractYear(_ text: String) -> Int? {
        // Match 4-digit years between 1900 and 2099
        let pattern = #"\b(19[0-9]{2}|20[0-9]{2})\b"#
        let regex = try? NSRegularExpression(pattern: pattern)
        let range = NSRange(text.startIndex..., in: text)
        if let match = regex?.firstMatch(in: text, range: range),
           let r = Range(match.range(at: 1), in: text) {
            return Int(text[r])
        }
        return nil
    }

    static func extractQuality(_ text: String) -> String? {
        let qualities = ["2160p", "4K", "UHD", "1080p", "720p", "480p", "360p"]
        let lowered = text.lowercased()
        for q in qualities {
            if lowered.contains(q.lowercased()) {
                return q
            }
        }
        return nil
    }

    private static func extractReleaseGroup(_ text: String) -> String? {
        // Release group is usually after the last '-' at the end
        let components = text.components(separatedBy: "-")
        guard let last = components.last else { return nil }
        let cleaned = last.trimmingCharacters(in: .whitespaces)
        // Must be short and alphanumeric
        if cleaned.count > 1 && cleaned.count <= 15 &&
           cleaned.range(of: #"^[A-Za-z0-9]+"#, options: .regularExpression) != nil {
            return cleaned
        }
        return nil
    }

    static func cleanTitle(_ raw: String) -> String {
        var title = raw.trimmingCharacters(in: .whitespaces)
        // Remove trailing/leading punctuation and spaces
        title = title.trimmingCharacters(in: CharacterSet(charactersIn: ".-_() "))
        // Replace multiple spaces with single
        while title.contains("  ") {
            title = title.replacingOccurrences(of: "  ", with: " ")
        }
        // Capitalize words
        return title.split(separator: " ")
            .map { word -> String in
                let w = String(word)
                // Keep articles lowercase unless first word
                let articles = ["a", "an", "the", "of", "in", "on", "at", "to", "for", "and", "or", "but"]
                if articles.contains(w.lowercased()) && title.split(separator: " ").first.map(String.init) != w {
                    return w.lowercased()
                }
                return w.prefix(1).uppercased() + w.dropFirst().lowercased()
            }
            .joined(separator: " ")
    }

    private static func stripExtension(_ filename: String) -> String {
        let videoExtensions = ["mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm",
                               "ts", "m2ts", "mpg", "mpeg", "3gp", "ogv"]
        let url = URL(fileURLWithPath: filename)
        if videoExtensions.contains(url.pathExtension.lowercased()) {
            return url.deletingPathExtension().lastPathComponent
        }
        return url.lastPathComponent
    }
}
