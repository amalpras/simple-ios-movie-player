import Foundation

// MARK: - MediaItem
/// Represents a single video file (movie or TV episode)
struct MediaItem: Identifiable, Codable, Hashable {
    let id: UUID
    var fileURL: URL
    var title: String
    var mediaType: MediaType
    var duration: TimeInterval
    var fileSize: Int64

    // TV Series info
    var seriesName: String?
    var seasonNumber: Int?
    var episodeNumber: Int?
    var episodeTitle: String?

    // Metadata (from TMDB)
    var tmdbID: Int?
    var overview: String?
    var releaseYear: Int?
    var posterPath: String?
    var backdropPath: String?
    var rating: Double?
    var genres: [String]

    // Subtitle info
    var subtitleFiles: [SubtitleTrack]
    var selectedSubtitleIndex: Int?

    // Playback
    var lastPlaybackPosition: TimeInterval
    var isWatched: Bool
    var dateAdded: Date
    var lastWatchedDate: Date?

    // Audio
    var selectedAudioTrackIndex: Int?

    // Persistent access: security-scoped URL bookmark stored when the file is first imported
    var bookmarkData: Data?

    init(fileURL: URL, title: String, mediaType: MediaType) {
        self.id = UUID()
        self.fileURL = fileURL
        self.title = title
        self.mediaType = mediaType
        self.duration = 0
        self.fileSize = 0
        self.genres = []
        self.subtitleFiles = []
        self.lastPlaybackPosition = 0
        self.isWatched = false
        self.dateAdded = Date()
    }

    var displayTitle: String {
        if mediaType == .episode, let ep = episodeNumber {
            let epStr = String(format: "E%02d", ep)
            if let season = seasonNumber {
                let sStr = String(format: "S%02d", season)
                return "\(sStr)\(epStr) - \(episodeTitle ?? title)"
            }
            return "\(epStr) - \(episodeTitle ?? title)"
        }
        return title
    }

    var progressPercentage: Double {
        guard duration > 0 else { return 0 }
        return min(lastPlaybackPosition / duration, 1.0)
    }

    var localPosterURL: URL? {
        guard let posterPath = posterPath else { return nil }
        let cachesDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        return cachesDir.appendingPathComponent("posters").appendingPathComponent(posterPath)
    }
}

// MARK: - MediaType
enum MediaType: String, Codable, CaseIterable {
    case movie = "Movie"
    case episode = "TV Episode"
    case unknown = "Unknown"
}

// MARK: - SubtitleTrack
struct SubtitleTrack: Identifiable, Codable, Hashable {
    let id: UUID
    var language: String
    var languageCode: String
    var fileURL: URL?
    var isEmbedded: Bool
    var source: SubtitleSource

    enum SubtitleSource: String, Codable {
        case embedded   // Embedded in the video file
        case local      // Local .srt/.ass file
        case downloaded // Downloaded from OpenSubtitles
    }

    init(language: String, languageCode: String, fileURL: URL? = nil, isEmbedded: Bool = false, source: SubtitleSource = .local) {
        self.id = UUID()
        self.language = language
        self.languageCode = languageCode
        self.fileURL = fileURL
        self.isEmbedded = isEmbedded
        self.source = source
    }
}

// MARK: - AudioTrack
struct AudioTrack: Identifiable, Hashable {
    let id: UUID
    var language: String
    var displayName: String
    var trackIndex: Int

    init(language: String, displayName: String, trackIndex: Int) {
        self.id = UUID()
        self.language = language
        self.displayName = displayName
        self.trackIndex = trackIndex
    }
}
