import Foundation
import Combine

// MARK: - PlaybackStateManager
/// Persists and manages playback positions for all media items
final class PlaybackStateManager: ObservableObject {
    static let shared = PlaybackStateManager()

    private let storageKey = "CinePlayer.PlaybackStates"
    @Published private(set) var states: [UUID: PlaybackState] = [:]

    private init() {
        load()
    }

    func state(for itemID: UUID) -> PlaybackState {
        states[itemID] ?? PlaybackState(itemID: itemID)
    }

    func update(itemID: UUID, position: TimeInterval, duration: TimeInterval) {
        var state = states[itemID] ?? PlaybackState(itemID: itemID)
        state.position = position
        state.duration = duration
        state.lastUpdated = Date()
        if duration > 0 {
            state.isCompleted = (position / duration) >= 0.90
        }
        states[itemID] = state
        save()
    }

    func markWatched(_ itemID: UUID) {
        var state = states[itemID] ?? PlaybackState(itemID: itemID)
        state.isCompleted = true
        state.lastUpdated = Date()
        states[itemID] = state
        save()
    }

    func resetProgress(_ itemID: UUID) {
        states[itemID] = PlaybackState(itemID: itemID)
        save()
    }

    private func save() {
        if let data = try? JSONEncoder().encode(states) {
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }

    private func load() {
        guard let data = UserDefaults.standard.data(forKey: storageKey),
              let decoded = try? JSONDecoder().decode([UUID: PlaybackState].self, from: data) else {
            return
        }
        states = decoded
    }
}

// MARK: - PlaybackState
struct PlaybackState: Codable {
    let itemID: UUID
    var position: TimeInterval = 0
    var duration: TimeInterval = 0
    var isCompleted: Bool = false
    var lastUpdated: Date = Date()

    var progressPercentage: Double {
        guard duration > 0 else { return 0 }
        return min(position / duration, 1.0)
    }

    var remainingTime: TimeInterval {
        max(duration - position, 0)
    }

    var shouldResume: Bool {
        position > 5 && !isCompleted && progressPercentage < 0.95
    }
}

// MARK: - AppSettings
/// User preferences for the player
final class AppSettings: ObservableObject {
    static let shared = AppSettings()

    // MARK: API Keys
    @Published var tmdbAPIKey: String {
        didSet { UserDefaults.standard.set(tmdbAPIKey, forKey: "tmdbAPIKey") }
    }
    @Published var openSubtitlesAPIKey: String {
        didSet { UserDefaults.standard.set(openSubtitlesAPIKey, forKey: "openSubtitlesAPIKey") }
    }
    @Published var openSubtitlesUsername: String {
        didSet { UserDefaults.standard.set(openSubtitlesUsername, forKey: "openSubtitlesUsername") }
    }
    @Published var openSubtitlesPassword: String {
        didSet { UserDefaults.standard.set(openSubtitlesPassword, forKey: "openSubtitlesPassword") }
    }

    // MARK: Subtitle Preferences
    @Published var preferredSubtitleLanguage: String {
        didSet { UserDefaults.standard.set(preferredSubtitleLanguage, forKey: "preferredSubtitleLanguage") }
    }
    @Published var subtitleFontSize: Double {
        didSet { UserDefaults.standard.set(subtitleFontSize, forKey: "subtitleFontSize") }
    }
    @Published var subtitleTextColor: String {
        didSet { UserDefaults.standard.set(subtitleTextColor, forKey: "subtitleTextColor") }
    }
    @Published var subtitleBackgroundOpacity: Double {
        didSet { UserDefaults.standard.set(subtitleBackgroundOpacity, forKey: "subtitleBackgroundOpacity") }
    }
    @Published var subtitleDelay: Double {
        didSet { UserDefaults.standard.set(subtitleDelay, forKey: "subtitleDelay") }
    }

    // MARK: Playback Preferences
    @Published var defaultPlaybackSpeed: Float {
        didSet { UserDefaults.standard.set(defaultPlaybackSpeed, forKey: "defaultPlaybackSpeed") }
    }
    @Published var skipForwardSeconds: Int {
        didSet { UserDefaults.standard.set(skipForwardSeconds, forKey: "skipForwardSeconds") }
    }
    @Published var skipBackwardSeconds: Int {
        didSet { UserDefaults.standard.set(skipBackwardSeconds, forKey: "skipBackwardSeconds") }
    }
    @Published var autoPlayNextEpisode: Bool {
        didSet { UserDefaults.standard.set(autoPlayNextEpisode, forKey: "autoPlayNextEpisode") }
    }
    @Published var autoFetchSubtitles: Bool {
        didSet { UserDefaults.standard.set(autoFetchSubtitles, forKey: "autoFetchSubtitles") }
    }
    @Published var autoFetchMetadata: Bool {
        didSet { UserDefaults.standard.set(autoFetchMetadata, forKey: "autoFetchMetadata") }
    }
    @Published var resumePlayback: Bool {
        didSet { UserDefaults.standard.set(resumePlayback, forKey: "resumePlayback") }
    }
    @Published var countdownToNextEpisode: Int {
        didSet { UserDefaults.standard.set(countdownToNextEpisode, forKey: "countdownToNextEpisode") }
    }

    // MARK: Library Preferences
    @Published var libraryViewStyle: LibraryViewStyle {
        didSet { UserDefaults.standard.set(libraryViewStyle.rawValue, forKey: "libraryViewStyle") }
    }

    private init() {
        tmdbAPIKey = UserDefaults.standard.string(forKey: "tmdbAPIKey") ?? ""
        openSubtitlesAPIKey = UserDefaults.standard.string(forKey: "openSubtitlesAPIKey") ?? ""
        openSubtitlesUsername = UserDefaults.standard.string(forKey: "openSubtitlesUsername") ?? ""
        openSubtitlesPassword = UserDefaults.standard.string(forKey: "openSubtitlesPassword") ?? ""
        preferredSubtitleLanguage = UserDefaults.standard.string(forKey: "preferredSubtitleLanguage") ?? "en"
        subtitleFontSize = UserDefaults.standard.object(forKey: "subtitleFontSize") as? Double ?? 18.0
        subtitleTextColor = UserDefaults.standard.string(forKey: "subtitleTextColor") ?? "white"
        subtitleBackgroundOpacity = UserDefaults.standard.object(forKey: "subtitleBackgroundOpacity") as? Double ?? 0.5
        subtitleDelay = UserDefaults.standard.object(forKey: "subtitleDelay") as? Double ?? 0.0
        defaultPlaybackSpeed = UserDefaults.standard.object(forKey: "defaultPlaybackSpeed") as? Float ?? 1.0
        skipForwardSeconds = UserDefaults.standard.object(forKey: "skipForwardSeconds") as? Int ?? 30
        skipBackwardSeconds = UserDefaults.standard.object(forKey: "skipBackwardSeconds") as? Int ?? 10
        autoPlayNextEpisode = UserDefaults.standard.object(forKey: "autoPlayNextEpisode") as? Bool ?? true
        autoFetchSubtitles = UserDefaults.standard.object(forKey: "autoFetchSubtitles") as? Bool ?? true
        autoFetchMetadata = UserDefaults.standard.object(forKey: "autoFetchMetadata") as? Bool ?? true
        resumePlayback = UserDefaults.standard.object(forKey: "resumePlayback") as? Bool ?? true
        countdownToNextEpisode = UserDefaults.standard.object(forKey: "countdownToNextEpisode") as? Int ?? 5
        let styleRaw = UserDefaults.standard.string(forKey: "libraryViewStyle") ?? LibraryViewStyle.grid.rawValue
        libraryViewStyle = LibraryViewStyle(rawValue: styleRaw) ?? .grid
    }
}

enum LibraryViewStyle: String, CaseIterable {
    case grid = "grid"
    case list = "list"
}
