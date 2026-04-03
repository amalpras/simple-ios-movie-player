import Foundation

// MARK: - SubtitleService
/// Searches and downloads subtitles from OpenSubtitles.com (free REST API).
/// API documentation: https://opensubtitles.stoplight.io/docs/opensubtitles-api
/// Registration: https://www.opensubtitles.com/en/users/sign_up (free account)
/// Users must register for a free API key at: https://www.opensubtitles.com/en/consumers
final class SubtitleService {
    static let shared = SubtitleService()

    private let baseURL = "https://api.opensubtitles.com/api/v1"
    private let session = URLSession.shared

    private var bearerToken: String?
    private var tokenExpiry: Date?

    private var apiKey: String { AppSettings.shared.openSubtitlesAPIKey }
    private var username: String { AppSettings.shared.openSubtitlesUsername }
    private var password: String { AppSettings.shared.openSubtitlesPassword }

    private init() {}

    // MARK: - Public API

    /// Auto-searches and downloads the best subtitle for a media item.
    /// Uses OpenSubtitles hash search first, then falls back to title search.
    func findAndDownloadSubtitle(for item: MediaItem) async throws -> SubtitleTrack? {
        guard !apiKey.isEmpty else { throw SubtitleError.noAPIKey }

        let language = AppSettings.shared.preferredSubtitleLanguage

        // Try hash-based search first (most accurate)
        if let result = try? await searchByHash(fileURL: item.fileURL, language: language) {
            return try await downloadSubtitle(result, for: item)
        }

        // Fall back to title + year search
        let query: String
        if item.mediaType == .episode,
           let seriesName = item.seriesName,
           let season = item.seasonNumber,
           let episode = item.episodeNumber {
            let results = try await searchByEpisode(
                query: seriesName,
                season: season,
                episode: episode,
                language: language
            )
            if let best = results.first {
                return try await downloadSubtitle(best, for: item)
            }
            return nil
        } else {
            query = item.title
            let year = item.releaseYear
            let results = try await searchByTitle(query: query, year: year, language: language)
            guard let best = results.first else { return nil }
            return try await downloadSubtitle(best, for: item)
        }
    }

    // MARK: - Hash Search

    /// Compute OpenSubtitles file hash and search for matching subtitles
    private func searchByHash(fileURL: URL, language: String) async throws -> SubtitleSearchResult? {
        let hash = try computeHash(fileURL: fileURL)
        let size = try fileSize(fileURL: fileURL)

        var components = URLComponents(string: baseURL + "/subtitles")!
        components.queryItems = [
            URLQueryItem(name: "moviehash", value: hash),
            URLQueryItem(name: "moviehash_match", value: "only"),
            URLQueryItem(name: "languages", value: language),
        ]

        let results = try await getAuthenticated(url: components.url!, as: SubtitleSearchResponse.self)
        _ = size // hash search implicitly uses file size
        return results.data.first
    }

    // MARK: - Title Search

    func searchByTitle(query: String, year: Int? = nil, language: String) async throws -> [SubtitleSearchResult] {
        var components = URLComponents(string: baseURL + "/subtitles")!
        var queryItems: [URLQueryItem] = [
            URLQueryItem(name: "query", value: query),
            URLQueryItem(name: "type", value: "movie"),
            URLQueryItem(name: "languages", value: language),
        ]
        if let y = year {
            queryItems.append(URLQueryItem(name: "year", value: String(y)))
        }
        components.queryItems = queryItems
        let results = try await getAuthenticated(url: components.url!, as: SubtitleSearchResponse.self)
        return results.data
    }

    func searchByEpisode(
        query: String,
        season: Int,
        episode: Int,
        language: String
    ) async throws -> [SubtitleSearchResult] {
        var components = URLComponents(string: baseURL + "/subtitles")!
        components.queryItems = [
            URLQueryItem(name: "query", value: query),
            URLQueryItem(name: "type", value: "episode"),
            URLQueryItem(name: "season_number", value: String(season)),
            URLQueryItem(name: "episode_number", value: String(episode)),
            URLQueryItem(name: "languages", value: language),
        ]
        let results = try await getAuthenticated(url: components.url!, as: SubtitleSearchResponse.self)
        return results.data
    }

    // MARK: - Download

    private func downloadSubtitle(_ result: SubtitleSearchResult, for item: MediaItem) async throws -> SubtitleTrack {
        // Request download link
        let requestBody = ["file_id": result.attributes.files.first?.fileID ?? 0]
        let downloadInfo = try await postAuthenticated(
            endpoint: "/download",
            body: requestBody,
            as: SubtitleDownloadResponse.self
        )

        // Download the subtitle file
        let (data, _) = try await session.data(from: URL(string: downloadInfo.link)!)

        // Save to disk alongside the video file (or in subtitles cache)
        let subtitleDir = item.fileURL.deletingLastPathComponent()
            .appendingPathComponent("Subtitles", isDirectory: true)
        try FileManager.default.createDirectory(at: subtitleDir, withIntermediateDirectories: true)

        let lang = result.attributes.language ?? AppSettings.shared.preferredSubtitleLanguage
        let ext = downloadInfo.fileName.components(separatedBy: ".").last ?? "srt"
        let baseName = item.fileURL.deletingPathExtension().lastPathComponent
        let saveURL = subtitleDir.appendingPathComponent("\(baseName).\(lang).\(ext)")
        try data.write(to: saveURL)

        return SubtitleTrack(
            language: languageName(from: lang),
            languageCode: lang,
            fileURL: saveURL,
            isEmbedded: false,
            source: .downloaded
        )
    }

    // MARK: - Authentication

    private func ensureAuthenticated() async throws {
        // Check if we have a valid token
        if let token = bearerToken,
           let expiry = tokenExpiry, expiry > Date() {
            return
        }
        // If no username/password, use API key only (guest mode for OpenSubtitles v3)
        guard !username.isEmpty && !password.isEmpty else {
            // API key only (limited downloads per day, no login needed)
            return
        }
        try await login()
    }

    private func login() async throws {
        let body: [String: Any] = ["username": username, "password": password]
        let response = try await post(
            endpoint: "/login",
            body: body,
            headers: ["Api-Key": apiKey],
            as: LoginResponse.self
        )
        bearerToken = response.token
        tokenExpiry = Date().addingTimeInterval(24 * 60 * 60) // 24 hours
    }

    func logout() async throws {
        guard bearerToken != nil else { return }
        _ = try? await deleteAuthenticated(endpoint: "/logout")
        bearerToken = nil
        tokenExpiry = nil
    }

    // MARK: - OpenSubtitles Hash Algorithm
    // Reference: https://trac.opensubtitles.org/projects/opensubtitles/wiki/HashSourceCodes

    private func computeHash(fileURL: URL) throws -> String {
        let file = try FileHandle(forReadingFrom: fileURL)
        defer { file.closeFile() }

        let fileSize = try self.fileSize(fileURL: fileURL)
        guard fileSize >= 131072 else { throw SubtitleError.fileTooSmall }

        var hash: UInt64 = UInt64(fileSize)

        // Read first 64KB
        let chunkSize = 65536
        let firstChunk = file.readData(ofLength: chunkSize)
        hash = xorHash(data: firstChunk, initial: hash)

        // Read last 64KB
        file.seek(toFileOffset: UInt64(fileSize) - UInt64(chunkSize))
        let lastChunk = file.readData(ofLength: chunkSize)
        hash = xorHash(data: lastChunk, initial: hash)

        return String(format: "%016llx", hash)
    }

    private func xorHash(data: Data, initial: UInt64) -> UInt64 {
        var hash = initial
        let count = data.count / 8
        data.withUnsafeBytes { ptr in
            let words = ptr.bindMemory(to: UInt64.self)
            for i in 0..<count {
                hash = hash &+ words[i].littleEndian
            }
        }
        return hash
    }

    private func fileSize(fileURL: URL) throws -> Int64 {
        let attrs = try FileManager.default.attributesOfItem(atPath: fileURL.path)
        return (attrs[.size] as? Int64) ?? 0
    }

    // MARK: - Network Helpers

    private func getAuthenticated<T: Decodable>(url: URL, as type: T.Type) async throws -> T {
        try await ensureAuthenticated()
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "Api-Key")
        request.setValue("CinePlayer v1.0", forHTTPHeaderField: "User-Agent")
        if let token = bearerToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        try validateResponse(response)
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func postAuthenticated<T: Decodable>(
        endpoint: String,
        body: [String: Any],
        as type: T.Type
    ) async throws -> T {
        try await ensureAuthenticated()
        return try await post(
            endpoint: endpoint,
            body: body,
            headers: buildAuthHeaders(),
            as: T.self
        )
    }

    private func deleteAuthenticated(endpoint: String) async throws {
        try await ensureAuthenticated()
        var request = URLRequest(url: URL(string: baseURL + endpoint)!)
        request.httpMethod = "DELETE"
        for (key, value) in buildAuthHeaders() {
            request.setValue(value, forHTTPHeaderField: key)
        }
        _ = try await session.data(for: request)
    }

    private func post<T: Decodable>(
        endpoint: String,
        body: [String: Any],
        headers: [String: String],
        as type: T.Type
    ) async throws -> T {
        var request = URLRequest(url: URL(string: baseURL + endpoint)!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await session.data(for: request)
        try validateResponse(response)
        return try JSONDecoder().decode(T.self, from: data)
    }

    private func buildAuthHeaders() -> [String: String] {
        var headers: [String: String] = [
            "Api-Key": apiKey,
            "User-Agent": "CinePlayer v1.0",
        ]
        if let token = bearerToken {
            headers["Authorization"] = "Bearer \(token)"
        }
        return headers
    }

    private func validateResponse(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else { return }
        switch http.statusCode {
        case 200...299: return
        case 401: throw SubtitleError.unauthorized
        case 429: throw SubtitleError.rateLimited
        default: throw SubtitleError.serverError(http.statusCode)
        }
    }

    private func languageName(from code: String) -> String {
        let locale = Locale(identifier: "en")
        return locale.localizedString(forLanguageCode: code) ?? code
    }

    // MARK: - Error

    enum SubtitleError: LocalizedError {
        case noAPIKey
        case fileTooSmall
        case unauthorized
        case rateLimited
        case serverError(Int)

        var errorDescription: String? {
            switch self {
            case .noAPIKey: return "OpenSubtitles API key not configured. Add it in Settings."
            case .fileTooSmall: return "File too small for hash-based search."
            case .unauthorized: return "Invalid OpenSubtitles credentials."
            case .rateLimited: return "OpenSubtitles rate limit reached. Try again later."
            case .serverError(let code): return "Server error: \(code)"
            }
        }
    }
}

// MARK: - OpenSubtitles API Models

struct SubtitleSearchResponse: Codable {
    let data: [SubtitleSearchResult]
}

struct SubtitleSearchResult: Codable, Identifiable {
    let id: String
    let attributes: SubtitleAttributes
}

struct SubtitleAttributes: Codable {
    let language: String?
    let downloadCount: Int?
    let newDownloadCount: Int?
    let hearingImpaired: Bool?
    let hd: Bool?
    let fps: Double?
    let votes: Int?
    let ratings: Double?
    let fromTrusted: Bool?
    let foreignParts: Bool?
    let uploadDate: String?
    let files: [SubtitleFile]

    enum CodingKeys: String, CodingKey {
        case language
        case downloadCount = "download_count"
        case newDownloadCount = "new_download_count"
        case hearingImpaired = "hearing_impaired"
        case hd, fps, votes, ratings
        case fromTrusted = "from_trusted"
        case foreignParts = "foreign_parts"
        case uploadDate = "upload_date"
        case files = "files"
    }
}

struct SubtitleFile: Codable {
    let fileID: Int
    let fileName: String?

    enum CodingKeys: String, CodingKey {
        case fileID = "file_id"
        case fileName = "file_name"
    }
}

struct SubtitleDownloadResponse: Codable {
    let link: String
    let fileName: String
    let requests: Int
    let allowed: Int
    let remaining: Int
    let message: String?
    let resetTime: String?

    enum CodingKeys: String, CodingKey {
        case link, requests, allowed, remaining, message
        case fileName = "file_name"
        case resetTime = "reset_time"
    }
}

struct LoginResponse: Codable {
    let token: String
    let status: Int?
    let user: LoginUser?

    struct LoginUser: Codable {
        let allowedDownloads: Int
        let level: String
        let userId: Int
        let extInstalled: Bool
        let vip: Bool

        enum CodingKeys: String, CodingKey {
            case allowedDownloads = "allowed_downloads"
            case level
            case userId = "user_id"
            case extInstalled = "ext_installed"
            case vip
        }
    }
}
