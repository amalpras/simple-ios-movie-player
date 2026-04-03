import Foundation

// MARK: - TVSeries
/// Represents a TV series (a group of episodes)
struct TVSeries: Identifiable, Codable, Hashable {
    let id: UUID
    var name: String
    var normalizedName: String

    // Metadata
    var tmdbID: Int?
    var overview: String?
    var firstAirYear: Int?
    var posterPath: String?
    var backdropPath: String?
    var rating: Double?
    var genres: [String]
    var status: String?   // e.g. "Ended", "Continuing"
    var network: String?

    // Episodes
    var seasons: [Int: [UUID]]  // seasonNumber -> [MediaItem IDs]
    var episodeIDs: [UUID]       // All episode IDs (flat)

    var dateAdded: Date
    var lastWatchedDate: Date?
    var nextEpisodeID: UUID?     // Next episode to watch

    init(name: String) {
        self.id = UUID()
        self.name = name
        self.normalizedName = TVSeries.normalize(name)
        self.genres = []
        self.seasons = [:]
        self.episodeIDs = []
        self.dateAdded = Date()
    }

    // Normalize series name for matching (strip punctuation, lowercase)
    static func normalize(_ name: String) -> String {
        var result = name.lowercased()
        // Remove common suffixes that might differ
        let stripWords = ["the ", "a ", "an "]
        for word in stripWords {
            if result.hasPrefix(word) {
                result = String(result.dropFirst(word.count))
            }
        }
        // Remove special characters
        result = result.components(separatedBy: CharacterSet.alphanumerics.union(.whitespaces).inverted).joined()
        return result.trimmingCharacters(in: .whitespaces)
    }

    var localPosterURL: URL? {
        guard let posterPath = posterPath else { return nil }
        let cachesDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        return cachesDir.appendingPathComponent("posters").appendingPathComponent(posterPath)
    }

    var totalEpisodeCount: Int {
        episodeIDs.count
    }

    var seasonCount: Int {
        seasons.keys.count
    }
}

// MARK: - TMDBMovieResult
/// TMDB API response model for a movie
struct TMDBMovieResult: Codable {
    let id: Int
    let title: String
    let originalTitle: String?
    let overview: String?
    let releaseDate: String?
    let posterPath: String?
    let backdropPath: String?
    let voteAverage: Double?
    let genreIds: [Int]?

    enum CodingKeys: String, CodingKey {
        case id, title, overview
        case originalTitle = "original_title"
        case releaseDate = "release_date"
        case posterPath = "poster_path"
        case backdropPath = "backdrop_path"
        case voteAverage = "vote_average"
        case genreIds = "genre_ids"
    }

    var year: Int? {
        guard let date = releaseDate, date.count >= 4 else { return nil }
        return Int(date.prefix(4))
    }
}

// MARK: - TMDBTVResult
/// TMDB API response model for a TV series
struct TMDBTVResult: Codable {
    let id: Int
    let name: String
    let originalName: String?
    let overview: String?
    let firstAirDate: String?
    let posterPath: String?
    let backdropPath: String?
    let voteAverage: Double?
    let genreIds: [Int]?
    let originCountry: [String]?

    enum CodingKeys: String, CodingKey {
        case id, name, overview
        case originalName = "original_name"
        case firstAirDate = "first_air_date"
        case posterPath = "poster_path"
        case backdropPath = "backdrop_path"
        case voteAverage = "vote_average"
        case genreIds = "genre_ids"
        case originCountry = "origin_country"
    }

    var year: Int? {
        guard let date = firstAirDate, date.count >= 4 else { return nil }
        return Int(date.prefix(4))
    }
}

// MARK: - TMDBGenre
struct TMDBGenre: Codable {
    let id: Int
    let name: String
}

// MARK: - TMDBSearchResponse
struct TMDBSearchResponse<T: Codable>: Codable {
    let page: Int
    let results: [T]
    let totalPages: Int
    let totalResults: Int

    enum CodingKeys: String, CodingKey {
        case page, results
        case totalPages = "total_pages"
        case totalResults = "total_results"
    }
}

// MARK: - TMDBEpisodeDetail
struct TMDBEpisodeDetail: Codable {
    let id: Int
    let name: String
    let overview: String?
    let episodeNumber: Int
    let seasonNumber: Int
    let stillPath: String?
    let voteAverage: Double?
    let airDate: String?

    enum CodingKeys: String, CodingKey {
        case id, name, overview
        case episodeNumber = "episode_number"
        case seasonNumber = "season_number"
        case stillPath = "still_path"
        case voteAverage = "vote_average"
        case airDate = "air_date"
    }
}
