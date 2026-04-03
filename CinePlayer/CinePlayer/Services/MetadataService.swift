import Foundation
import UIKit

// MARK: - MetadataService
/// Fetches movie and TV series metadata (posters, descriptions, ratings) from TMDB.
/// TMDB (The Movie Database) is a free community-maintained database.
/// API documentation: https://developer.themoviedb.org/docs
/// Users must register at https://www.themoviedb.org/settings/api for a free API key.
final class MetadataService {
    static let shared = MetadataService()

    private let baseURL = "https://api.themoviedb.org/3"
    private let imageBaseURL = "https://image.tmdb.org/t/p"
    private let session = URLSession.shared

    // Genre maps (TMDB genre IDs → names)
    private var movieGenreMap: [Int: String] = [:]
    private var tvGenreMap: [Int: String] = [:]

    private var apiKey: String { AppSettings.shared.tmdbAPIKey }

    private init() {}

    // MARK: - Movie Metadata

    func fetchMovieMetadata(for item: MediaItem) async throws -> MediaItem {
        guard !apiKey.isEmpty else { throw MetadataError.noAPIKey }
        var updatedItem = item
        let searchTitle = item.title
        let year = item.releaseYear

        let results = try await searchMovies(query: searchTitle, year: year)
        guard let best = results.first else {
            throw MetadataError.notFound
        }

        updatedItem.tmdbID = best.id
        updatedItem.overview = best.overview
        updatedItem.releaseYear = best.year
        updatedItem.posterPath = best.posterPath.map { "movie_\(best.id)_poster.jpg" }
        updatedItem.backdropPath = best.backdropPath.map { "movie_\(best.id)_backdrop.jpg" }
        updatedItem.rating = best.voteAverage

        if let genreIds = best.genreIds {
            updatedItem.genres = genreIds.compactMap { movieGenreMap[$0] }
        }

        // Download poster
        if let path = best.posterPath {
            try? await downloadImage(
                path: path,
                size: "w342",
                saveName: updatedItem.posterPath ?? ""
            )
        }
        if let path = best.backdropPath {
            try? await downloadImage(
                path: path,
                size: "w780",
                saveName: updatedItem.backdropPath ?? ""
            )
        }

        return updatedItem
    }

    // MARK: - TV Series Metadata

    func fetchSeriesMetadata(for series: TVSeries) async throws -> TVSeries {
        guard !apiKey.isEmpty else { throw MetadataError.noAPIKey }
        var updatedSeries = series

        let results = try await searchTV(query: series.name, year: series.firstAirYear)
        guard let best = results.first else {
            throw MetadataError.notFound
        }

        updatedSeries.tmdbID = best.id
        updatedSeries.overview = best.overview
        updatedSeries.firstAirYear = best.year
        updatedSeries.posterPath = best.posterPath.map { "series_\(best.id)_poster.jpg" }
        updatedSeries.backdropPath = best.backdropPath.map { "series_\(best.id)_backdrop.jpg" }
        updatedSeries.rating = best.voteAverage

        if let genreIds = best.genreIds {
            updatedSeries.genres = genreIds.compactMap { tvGenreMap[$0] }
        }

        // Download poster/backdrop
        if let path = best.posterPath {
            try? await downloadImage(
                path: path,
                size: "w342",
                saveName: updatedSeries.posterPath ?? ""
            )
        }
        if let path = best.backdropPath {
            try? await downloadImage(
                path: path,
                size: "w780",
                saveName: updatedSeries.backdropPath ?? ""
            )
        }

        return updatedSeries
    }

    // MARK: - Episode Metadata

    func fetchEpisodeMetadata(
        tmdbSeriesID: Int,
        season: Int,
        episode: Int
    ) async throws -> TMDBEpisodeDetail {
        guard !apiKey.isEmpty else { throw MetadataError.noAPIKey }
        let endpoint = "/tv/\(tmdbSeriesID)/season/\(season)/episode/\(episode)"
        return try await get(endpoint: endpoint, as: TMDBEpisodeDetail.self)
    }

    // MARK: - Search

    func searchMovies(query: String, year: Int? = nil) async throws -> [TMDBMovieResult] {
        guard !apiKey.isEmpty else { throw MetadataError.noAPIKey }
        var params: [String: String] = ["query": query, "include_adult": "false"]
        if let y = year { params["year"] = String(y) }
        let response = try await get(endpoint: "/search/movie",
                                     params: params,
                                     as: TMDBSearchResponse<TMDBMovieResult>.self)
        return response.results
    }

    func searchTV(query: String, year: Int? = nil) async throws -> [TMDBTVResult] {
        guard !apiKey.isEmpty else { throw MetadataError.noAPIKey }
        var params: [String: String] = ["query": query, "include_adult": "false"]
        if let y = year { params["first_air_date_year"] = String(y) }
        let response = try await get(endpoint: "/search/tv",
                                     params: params,
                                     as: TMDBSearchResponse<TMDBTVResult>.self)
        return response.results
    }

    // MARK: - Genre Lists

    func loadGenres() async {
        guard !apiKey.isEmpty else { return }
        if let movieGenres = try? await get(endpoint: "/genre/movie/list",
                                             as: GenreListResponse.self) {
            movieGenreMap = Dictionary(uniqueKeysWithValues: movieGenres.genres.map { ($0.id, $0.name) })
        }
        if let tvGenres = try? await get(endpoint: "/genre/tv/list",
                                          as: GenreListResponse.self) {
            tvGenreMap = Dictionary(uniqueKeysWithValues: tvGenres.genres.map { ($0.id, $0.name) })
        }
    }

    // MARK: - Image Downloading

    private func downloadImage(path: String, size: String, saveName: String) async throws {
        guard !saveName.isEmpty else { return }
        let url = URL(string: "\(imageBaseURL)/\(size)\(path)")!
        let (data, _) = try await session.data(from: url)

        let cachesDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let postersDir = cachesDir.appendingPathComponent("posters")
        try FileManager.default.createDirectory(at: postersDir, withIntermediateDirectories: true)
        let saveURL = postersDir.appendingPathComponent(saveName)
        try data.write(to: saveURL)
    }

    func loadCachedPoster(named: String) -> UIImage? {
        let cachesDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        let url = cachesDir.appendingPathComponent("posters").appendingPathComponent(named)
        guard let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }

    // MARK: - Network Helper

    private func get<T: Decodable>(
        endpoint: String,
        params: [String: String] = [:],
        as type: T.Type
    ) async throws -> T {
        var components = URLComponents(string: baseURL + endpoint)!
        var queryItems = [URLQueryItem(name: "api_key", value: apiKey)]
        for (key, value) in params {
            queryItems.append(URLQueryItem(name: key, value: value))
        }
        components.queryItems = queryItems
        guard let url = components.url else { throw MetadataError.invalidURL }
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw MetadataError.serverError
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    // MARK: - Error

    enum MetadataError: LocalizedError {
        case noAPIKey
        case notFound
        case invalidURL
        case serverError

        var errorDescription: String? {
            switch self {
            case .noAPIKey: return "TMDB API key not configured. Add it in Settings."
            case .notFound: return "No metadata found for this title."
            case .invalidURL: return "Invalid URL."
            case .serverError: return "Server error. Check your API key and network."
            }
        }
    }
}

// MARK: - GenreListResponse
private struct GenreListResponse: Codable {
    let genres: [TMDBGenre]
}
