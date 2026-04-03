import Foundation
import CoreMedia

// MARK: - SubtitleParser
/// Parses subtitle files (.srt, .vtt, .ass/.ssa) into a list of timed subtitle cues.
struct SubtitleParser {

    struct Cue: Identifiable {
        let id: Int
        let startTime: TimeInterval
        let endTime: TimeInterval
        let text: String
    }

    static func parse(fileURL: URL) -> [Cue] {
        guard let content = try? String(contentsOf: fileURL, encoding: .utf8) ??
                                  String(contentsOf: fileURL, encoding: .isoLatin1) else {
            return []
        }
        let ext = fileURL.pathExtension.lowercased()
        switch ext {
        case "srt": return parseSRT(content)
        case "vtt": return parseVTT(content)
        case "ass", "ssa": return parseASS(content)
        default: return parseSRT(content) // Try SRT as default
        }
    }

    // MARK: - SRT Parser

    static func parseSRT(_ content: String) -> [Cue] {
        var cues: [Cue] = []
        // Normalize line endings
        let normalized = content.replacingOccurrences(of: "\r\n", with: "\n")
                                 .replacingOccurrences(of: "\r", with: "\n")
        // Split by blank lines between cues
        let blocks = normalized.components(separatedBy: "\n\n")

        for block in blocks {
            let lines = block.components(separatedBy: "\n").filter { !$0.isEmpty }
            guard lines.count >= 2 else { continue }

            // Find the sequence number line
            var timeLineIndex = 0
            if let first = lines.first, first.trimmingCharacters(in: .whitespaces).allSatisfy({ $0.isNumber }) {
                timeLineIndex = 1
            }
            guard timeLineIndex < lines.count else { continue }

            let timeLine = lines[timeLineIndex]
            guard let (start, end) = parseSRTTimecode(timeLine) else { continue }

            let textLines = lines[(timeLineIndex + 1)...]
            let text = textLines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
            let cleaned = stripHTMLTags(text)

            if !cleaned.isEmpty {
                cues.append(Cue(id: cues.count, startTime: start, endTime: end, text: cleaned))
            }
        }
        return cues
    }

    // MARK: - VTT Parser

    static func parseVTT(_ content: String) -> [Cue] {
        var cues: [Cue] = []
        let normalized = content.replacingOccurrences(of: "\r\n", with: "\n")
                                 .replacingOccurrences(of: "\r", with: "\n")
        let blocks = normalized.components(separatedBy: "\n\n")

        for block in blocks {
            let lines = block.components(separatedBy: "\n").filter { !$0.isEmpty }
            // Skip WEBVTT header and NOTE blocks
            if lines.first?.hasPrefix("WEBVTT") == true { continue }
            if lines.first?.hasPrefix("NOTE") == true { continue }

            var timeLineIndex = 0
            // Skip optional cue identifier
            if let first = lines.first, !first.contains("-->") {
                timeLineIndex = 1
            }
            guard timeLineIndex < lines.count else { continue }
            let timeLine = lines[timeLineIndex]
            guard timeLine.contains("-->"),
                  let (start, end) = parseVTTTimecode(timeLine) else { continue }

            let textLines = lines[(timeLineIndex + 1)...]
            let text = textLines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
            let cleaned = stripHTMLTags(text)
            if !cleaned.isEmpty {
                cues.append(Cue(id: cues.count, startTime: start, endTime: end, text: cleaned))
            }
        }
        return cues
    }

    // MARK: - ASS/SSA Parser

    static func parseASS(_ content: String) -> [Cue] {
        var cues: [Cue] = []
        let normalized = content.replacingOccurrences(of: "\r\n", with: "\n")
                                 .replacingOccurrences(of: "\r", with: "\n")
        let lines = normalized.components(separatedBy: "\n")

        // Find the format line to know column indices
        var formatColumns: [String] = []
        var inEventsSection = false

        for line in lines {
            if line.hasPrefix("[Events]") {
                inEventsSection = true
                continue
            }
            if inEventsSection && line.hasPrefix("Format:") {
                let cols = String(line.dropFirst("Format:".count))
                    .components(separatedBy: ",")
                    .map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
                formatColumns = cols
                continue
            }
            if inEventsSection && line.hasPrefix("Dialogue:") {
                guard !formatColumns.isEmpty else { continue }
                let data = String(line.dropFirst("Dialogue:".count))
                let parts = splitCSV(data, maxParts: formatColumns.count)
                guard parts.count >= formatColumns.count else { continue }

                let colMap = Dictionary(uniqueKeysWithValues: zip(formatColumns, parts))
                guard let startStr = colMap["start"],
                      let endStr = colMap["end"],
                      let text = colMap["text"] else { continue }

                guard let start = parseASSTimecode(startStr),
                      let end = parseASSTimecode(endStr) else { continue }

                let cleaned = stripASSFormatting(text)
                if !cleaned.isEmpty {
                    cues.append(Cue(id: cues.count, startTime: start, endTime: end, text: cleaned))
                }
            }
        }
        return cues.sorted { $0.startTime < $1.startTime }
    }

    // MARK: - Timecode Parsers

    private static func parseSRTTimecode(_ line: String) -> (TimeInterval, TimeInterval)? {
        // Format: 00:00:00,000 --> 00:00:00,000
        let parts = line.components(separatedBy: " --> ")
        guard parts.count == 2 else { return nil }
        guard let start = parseSRTTime(parts[0].trimmingCharacters(in: .whitespaces)),
              let end = parseSRTTime(parts[1].trimmingCharacters(in: .whitespaces)) else { return nil }
        return (start, end)
    }

    private static func parseSRTTime(_ str: String) -> TimeInterval? {
        // 00:00:00,000 or 00:00:00.000
        let normalized = str.replacingOccurrences(of: ",", with: ".")
        let parts = normalized.components(separatedBy: ":")
        guard parts.count == 3,
              let hours = Double(parts[0]),
              let minutes = Double(parts[1]),
              let seconds = Double(parts[2]) else { return nil }
        return hours * 3600 + minutes * 60 + seconds
    }

    private static func parseVTTTimecode(_ line: String) -> (TimeInterval, TimeInterval)? {
        // Strip cue settings (everything after the timestamp pair)
        let timeStr = line.components(separatedBy: " ").prefix(3).joined(separator: " ")
        let parts = timeStr.components(separatedBy: " --> ")
        guard parts.count == 2 else { return nil }
        guard let start = parseVTTTime(parts[0].trimmingCharacters(in: .whitespaces)),
              let end = parseVTTTime(parts[1].trimmingCharacters(in: .whitespaces)) else { return nil }
        return (start, end)
    }

    private static func parseVTTTime(_ str: String) -> TimeInterval? {
        // mm:ss.mmm or hh:mm:ss.mmm
        let parts = str.components(separatedBy: ":")
        if parts.count == 2 {
            guard let minutes = Double(parts[0]),
                  let seconds = Double(parts[1]) else { return nil }
            return minutes * 60 + seconds
        } else if parts.count == 3 {
            guard let hours = Double(parts[0]),
                  let minutes = Double(parts[1]),
                  let seconds = Double(parts[2]) else { return nil }
            return hours * 3600 + minutes * 60 + seconds
        }
        return nil
    }

    private static func parseASSTimecode(_ str: String) -> TimeInterval? {
        // H:MM:SS.cc
        let parts = str.components(separatedBy: ":")
        guard parts.count == 3,
              let hours = Double(parts[0]),
              let minutes = Double(parts[1]),
              let seconds = Double(parts[2]) else { return nil }
        return hours * 3600 + minutes * 60 + seconds
    }

    // MARK: - Text Cleanup

    private static func stripHTMLTags(_ text: String) -> String {
        // Remove <b>, <i>, <u>, <font>, etc.
        var result = text
        let tagPattern = "<[^>]+>"
        result = result.replacingOccurrences(of: tagPattern, with: "", options: .regularExpression)
        result = result.replacingOccurrences(of: "&amp;", with: "&")
        result = result.replacingOccurrences(of: "&lt;", with: "<")
        result = result.replacingOccurrences(of: "&gt;", with: ">")
        result = result.replacingOccurrences(of: "&nbsp;", with: " ")
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func stripASSFormatting(_ text: String) -> String {
        // Remove ASS override codes like {\an8}, {\b1}, {\i1}, etc.
        var result = text.replacingOccurrences(of: #"\{[^}]*\}"#, with: "", options: .regularExpression)
        // Replace \N and \n with newline
        result = result.replacingOccurrences(of: "\\N", with: "\n")
        result = result.replacingOccurrences(of: "\\n", with: "\n")
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - CSV Split (for ASS format line)

    private static func splitCSV(_ str: String, maxParts: Int) -> [String] {
        var parts: [String] = []
        var current = ""
        var count = 0
        for char in str {
            if char == "," && count < maxParts - 1 {
                parts.append(current.trimmingCharacters(in: .whitespaces))
                current = ""
                count += 1
            } else {
                current.append(char)
            }
        }
        parts.append(current.trimmingCharacters(in: .whitespaces))
        return parts
    }
}
