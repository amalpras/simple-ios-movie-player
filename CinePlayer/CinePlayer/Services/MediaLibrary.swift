import Foundation
import AVFoundation
import Combine
import UIKit

// MARK: - MediaLibrary
/// Central store for all media items and TV series. Handles auto-organization and metadata fetching.
final class MediaLibrary: ObservableObject {
    static let shared = MediaLibrary()

    // MARK: - Published State
    @Published private(set) var movies: [MediaItem] = []
    @Published private(set) var series: [TVSeries] = []
    @Published private(set) var allItems: [MediaItem] = []
    @Published private(set) var recentlyAdded: [MediaItem] = []
    @Published private(set) var continueWatching: [MediaItem] = []
    @Published var isScanning: Bool = false
    @Published var scanProgress: Double = 0
    @Published var lastError: String?

    private let storageKey = "CinePlayer.MediaLibrary"
    private let seriesStorageKey = "CinePlayer.TVSeries"
    private var cancellables = Set<AnyCancellable>()

    private init() {
        load()
        setupContinueWatchingUpdates()
        // Load genres asynchronously (non-blocking)
        Task { await MetadataService.shared.loadGenres() }
    }

    // MARK: - Add Media

    /// Add a video file from a URL (e.g., document picker)
    @MainActor
    func addMedia(from url: URL) async {
        // Start accessing security-scoped resource
        let isAccessing = url.startAccessingSecurityScopedResource()
        defer {
            if isAccessing { url.stopAccessingSecurityScopedResource() }
        }

        // Check if already exists
        guard !allItems.contains(where: { $0.fileURL == url }) else { return }

        // Parse filename
        let filename = url.lastPathComponent
        let parsed = FilenameParser.parse(filename: filename)

        var item = MediaItem(fileURL: url, title: parsed.title, mediaType: parsed.mediaType)
        item.seriesName = parsed.title.isEmpty ? nil :
            (parsed.mediaType == .episode ? parsed.title : nil)
        item.seasonNumber = parsed.seasonNumber
        item.episodeNumber = parsed.episodeNumber
        item.releaseYear = parsed.year

        // Store security-scoped bookmark for persistent access across launches
        item.bookmarkData = try? url.bookmarkData(
            options: .minimalBookmark,
            includingResourceValuesForKeys: nil,
            relativeTo: nil
        )

        // Probe duration asynchronously
        if let duration = await probeDuration(url: url) {
            item.duration = duration
        }
        item.fileSize = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).flatMap { Int64($0) } ?? 0

        // Add to library
        allItems.append(item)
        organizeItem(item)
        updateDerivedLists()
        save()

        // Discover local subtitles (same folder)
        discoverLocalSubtitles(for: &allItems[allItems.count - 1])

        // Fetch metadata in background
        if AppSettings.shared.autoFetchMetadata {
            Task { await fetchMetadataForItem(id: item.id) }
        }
        if AppSettings.shared.autoFetchSubtitles {
            Task { await fetchSubtitleForItem(id: item.id) }
        }
    }

    /// Add multiple files at once
    @MainActor
    func addMedia(from urls: [URL]) async {
        for url in urls {
            await addMedia(from: url)
        }
    }

    // MARK: - Remove Media

    @MainActor
    func removeItem(_ item: MediaItem) {
        allItems.removeAll { $0.id == item.id }
        // Remove from series
        for idx in series.indices {
            series[idx].episodeIDs.removeAll { $0 == item.id }
            series[idx].seasons = series[idx].seasons.mapValues { ids in ids.filter { $0 != item.id } }
        }
        series.removeAll { $0.episodeIDs.isEmpty }
        movies.removeAll { $0.id == item.id }
        updateDerivedLists()
        save()
    }

    @MainActor
    func updatePlaybackPosition(itemID: UUID, position: TimeInterval, duration: TimeInterval) {
        guard let idx = allItems.firstIndex(where: { $0.id == itemID }) else { return }
        allItems[idx].lastPlaybackPosition = position
        allItems[idx].duration = duration
        if duration > 0 && (position / duration) >= 0.90 {
            allItems[idx].isWatched = true
        }
        allItems[idx].lastWatchedDate = Date()
        updateDerivedLists()
        PlaybackStateManager.shared.update(itemID: itemID, position: position, duration: duration)
        save()
    }

    @MainActor
    func markWatched(_ itemID: UUID) {
        guard let idx = allItems.firstIndex(where: { $0.id == itemID }) else { return }
        allItems[idx].isWatched = true
        allItems[idx].lastPlaybackPosition = allItems[idx].duration
        allItems[idx].lastWatchedDate = Date()
        PlaybackStateManager.shared.markWatched(itemID)
        updateDerivedLists()
        save()
    }

    @MainActor
    func resetProgress(_ itemID: UUID) {
        guard let idx = allItems.firstIndex(where: { $0.id == itemID }) else { return }
        allItems[idx].isWatched = false
        allItems[idx].lastPlaybackPosition = 0
        PlaybackStateManager.shared.resetProgress(itemID)
        updateDerivedLists()
        save()
    }

    // MARK: - Auto-Metadata Fetch

    func fetchMetadataForItem(id: UUID) async {
        guard let idx = allItems.firstIndex(where: { $0.id == id }) else { return }
        let item = allItems[idx]

        do {
            if item.mediaType == .movie {
                let updated = try await MetadataService.shared.fetchMovieMetadata(for: item)
                await MainActor.run {
                    guard let i = allItems.firstIndex(where: { $0.id == id }) else { return }
                    allItems[i] = updated
                    updateDerivedLists()
                    save()
                }
            } else if item.mediaType == .episode {
                // Update the whole series metadata if series name matches
                if let seriesName = item.seriesName,
                   let seriesIdx = series.firstIndex(where: { $0.name == seriesName }) {
                    let updated = try await MetadataService.shared.fetchSeriesMetadata(for: series[seriesIdx])
                    await MainActor.run {
                        guard let si = self.series.firstIndex(where: { $0.id == updated.id }) else { return }
                        self.series[si] = updated
                        self.save()
                    }
                }
            }
        } catch {
            // Metadata fetch is best-effort; log and continue
            print("[MetadataService] Failed for \(item.title): \(error.localizedDescription)")
        }
    }

    // MARK: - Auto-Subtitle Fetch

    func fetchSubtitleForItem(id: UUID) async {
        do {
            try await fetchSubtitleForItemThrowing(id: id)
        } catch SubtitleService.SubtitleError.rateLimited {
            await MainActor.run { lastError = "OpenSubtitles daily download limit reached. Try again tomorrow." }
        } catch {
            guard let idx = allItems.firstIndex(where: { $0.id == id }) else { return }
            print("[SubtitleService] Failed for \(allItems[idx].title): \(error.localizedDescription)")
        }
    }

    // MARK: - Series Organization

    private func organizeItem(_ item: MediaItem) {
        switch item.mediaType {
        case .movie:
            if !movies.contains(where: { $0.id == item.id }) {
                movies.append(item)
            }
        case .episode:
            guard let seriesName = item.seriesName else {
                // Treat as movie if no series info
                if !movies.contains(where: { $0.id == item.id }) {
                    movies.append(item)
                }
                return
            }
            let normalizedName = TVSeries.normalize(seriesName)
            if let idx = series.firstIndex(where: { $0.normalizedName == normalizedName }) {
                // Add to existing series
                if !series[idx].episodeIDs.contains(item.id) {
                    series[idx].episodeIDs.append(item.id)
                    let season = item.seasonNumber ?? 1
                    if series[idx].seasons[season] == nil {
                        series[idx].seasons[season] = []
                    }
                    series[idx].seasons[season]?.append(item.id)
                }
            } else {
                // Create new series
                var newSeries = TVSeries(name: seriesName)
                newSeries.episodeIDs = [item.id]
                let season = item.seasonNumber ?? 1
                newSeries.seasons[season] = [item.id]
                series.append(newSeries)
            }
        case .unknown:
            if !movies.contains(where: { $0.id == item.id }) {
                movies.append(item)
            }
        }
    }

    // MARK: - Local Subtitle Discovery

    private func discoverLocalSubtitles(for item: inout MediaItem) {
        let videoDir = item.fileURL.deletingLastPathComponent()
        let baseName = item.fileURL.deletingPathExtension().lastPathComponent
        let subtitleExtensions = ["srt", "ass", "ssa", "vtt", "sub"]

        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: videoDir,
            includingPropertiesForKeys: nil
        ) else { return }

        for file in contents {
            let ext = file.pathExtension.lowercased()
            guard subtitleExtensions.contains(ext) else { continue }
            let name = file.deletingPathExtension().lastPathComponent
            // Match files with same base name, e.g. "Movie.en.srt", "Movie.srt"
            guard name.hasPrefix(baseName) || name == baseName else { continue }

            // Detect language from filename (e.g. "Movie.en.srt")
            let langCode = detectLanguageCode(from: name, baseName: baseName)
            let langName = Locale(identifier: "en").localizedString(forLanguageCode: langCode) ?? langCode

            let track = SubtitleTrack(
                language: langName,
                languageCode: langCode,
                fileURL: file,
                isEmbedded: false,
                source: .local
            )
            if !item.subtitleFiles.contains(where: { $0.fileURL == file }) {
                item.subtitleFiles.append(track)
            }
        }
    }

    private func detectLanguageCode(from name: String, baseName: String) -> String {
        // Extract language code from patterns like "Movie.en", "Movie.english", "Movie.en-US"
        let suffix = String(name.dropFirst(baseName.count)).trimmingCharacters(in: CharacterSet(charactersIn: ".-_"))
        let parts = suffix.components(separatedBy: CharacterSet(charactersIn: ".-_"))
        for part in parts {
            let lower = part.lowercased()
            if lower.count == 2 || lower.count == 3 {
                return lower
            }
        }
        return "und" // undetermined
    }

    // MARK: - Derived Lists

    private func updateDerivedLists() {
        let sorted = allItems.sorted { $0.dateAdded > $1.dateAdded }
        recentlyAdded = Array(sorted.prefix(20))
        continueWatching = allItems
            .filter { !$0.isWatched && $0.lastPlaybackPosition > 5 }
            .sorted { ($0.lastWatchedDate ?? .distantPast) > ($1.lastWatchedDate ?? .distantPast) }
    }

    // MARK: - AVAsset Duration Probe

    private func probeDuration(url: URL) async -> TimeInterval? {
        let asset = AVAsset(url: url)
        do {
            let duration = try await asset.load(.duration)
            let seconds = CMTimeGetSeconds(duration)
            return seconds.isNaN || seconds.isInfinite ? nil : seconds
        } catch {
            return nil
        }
    }

    // MARK: - Lookup Helpers

    func item(id: UUID) -> MediaItem? {
        allItems.first { $0.id == id }
    }

    func seriesItem(id: UUID) -> TVSeries? {
        series.first { $0.id == id }
    }

    func episodes(for series: TVSeries, season: Int? = nil) -> [MediaItem] {
        let ids: [UUID]
        if let season = season {
            ids = series.seasons[season] ?? []
        } else {
            ids = series.episodeIDs
        }
        return ids.compactMap { id in allItems.first { $0.id == id } }
            .sorted { (a, b) -> Bool in
                let aNum = a.episodeNumber ?? 0
                let bNum = b.episodeNumber ?? 0
                return aNum < bNum
            }
    }

    func nextEpisode(after item: MediaItem) -> MediaItem? {
        guard item.mediaType == .episode,
              let seriesName = item.seriesName,
              let season = item.seasonNumber,
              let epNum = item.episodeNumber else { return nil }

        let normalizedName = TVSeries.normalize(seriesName)
        guard let s = series.first(where: { $0.normalizedName == normalizedName }) else { return nil }

        // Try next episode in same season
        let seasonEps = episodes(for: s, season: season)
        if let next = seasonEps.first(where: { $0.episodeNumber == epNum + 1 }) {
            return next
        }
        // Try first episode of next season
        let nextSeasonEps = episodes(for: s, season: season + 1)
        return nextSeasonEps.first
    }

    // MARK: - Persistence

    private func save() {
        if let itemsData = try? JSONEncoder().encode(allItems) {
            UserDefaults.standard.set(itemsData, forKey: storageKey)
        }
        if let seriesData = try? JSONEncoder().encode(series) {
            UserDefaults.standard.set(seriesData, forKey: seriesStorageKey)
        }
    }

    private func load() {
        if let data = UserDefaults.standard.data(forKey: storageKey),
           var items = try? JSONDecoder().decode([MediaItem].self, from: data) {
            // Resolve security-scoped bookmarks to restore file access across launches
            items = items.map { item in
                var item = item
                if let bookmarkData = item.bookmarkData {
                    var isStale = false
                    if let resolvedURL = try? URL(
                        resolvingBookmarkData: bookmarkData,
                        options: .withoutUI,
                        relativeTo: nil,
                        bookmarkDataIsStale: &isStale
                    ) {
                        item.fileURL = resolvedURL
                        if isStale {
                            item.bookmarkData = try? resolvedURL.bookmarkData(
                                options: .minimalBookmark,
                                includingResourceValuesForKeys: nil,
                                relativeTo: nil
                            )
                        }
                    }
                }
                return item
            }
            allItems = items
            movies = items.filter { $0.mediaType == .movie || $0.mediaType == .unknown }
        }
        if let data = UserDefaults.standard.data(forKey: seriesStorageKey),
           let loadedSeries = try? JSONDecoder().decode([TVSeries].self, from: data) {
            series = loadedSeries
        }
        updateDerivedLists()
    }

    // MARK: - Combine

    private func setupContinueWatchingUpdates() {
        PlaybackStateManager.shared.$states
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                self?.updateDerivedLists()
            }
            .store(in: &cancellables)
    }

    // MARK: - Manual Metadata/Subtitle Refresh

    func refreshMetadata(for item: MediaItem) async {
        await fetchMetadataForItem(id: item.id)
    }

    func refreshSeriesMetadata(seriesID: UUID) async {
        guard let series = series.first(where: { $0.id == seriesID }) else { return }
        do {
            let updated = try await MetadataService.shared.fetchSeriesMetadata(for: series)
            await MainActor.run {
                guard let si = self.series.firstIndex(where: { $0.id == updated.id }) else { return }
                self.series[si] = updated
                self.save()
            }
        } catch {
            print("[MetadataService] Series refresh failed: \(error.localizedDescription)")
        }
    }

    func refreshSubtitle(for item: MediaItem) async {
        await MainActor.run {
            if let idx = allItems.firstIndex(where: { $0.id == item.id }) {
                allItems[idx].subtitleFiles.removeAll { $0.source == .downloaded }
            }
        }
        do {
            try await fetchSubtitleForItemThrowing(id: item.id)
        } catch SubtitleService.SubtitleError.rateLimited {
            await MainActor.run { lastError = "OpenSubtitles daily download limit reached. Try again tomorrow." }
        } catch {}
    }

    // Internal throwing version used by refresh and auto-fetch
    private func fetchSubtitleForItemThrowing(id: UUID) async throws {
        guard let idx = allItems.firstIndex(where: { $0.id == id }) else { return }
        let item = allItems[idx]
        if !item.subtitleFiles.isEmpty { return }
        if let track = try await SubtitleService.shared.findAndDownloadSubtitle(for: item) {
            await MainActor.run {
                guard let i = allItems.firstIndex(where: { $0.id == id }) else { return }
                allItems[i].subtitleFiles.append(track)
                if allItems[i].selectedSubtitleIndex == nil {
                    allItems[i].selectedSubtitleIndex = 0
                }
                save()
            }
        }
    }
}
