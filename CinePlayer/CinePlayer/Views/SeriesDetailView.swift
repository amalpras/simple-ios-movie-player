import SwiftUI

// MARK: - SeriesListView
/// Shows all TV series as a grid with series cover image and episode count
struct SeriesListView: View {
    @EnvironmentObject var mediaLibrary: MediaLibrary
    @EnvironmentObject var playbackManager: PlaybackStateManager
    @State private var searchText = ""
    @State private var selectedSeries: TVSeries?

    var filteredSeries: [TVSeries] {
        guard !searchText.isEmpty else { return mediaLibrary.series }
        return mediaLibrary.series.filter {
            $0.name.localizedCaseInsensitiveContains(searchText)
        }
    }

    private let columns = [GridItem(.adaptive(minimum: 120, maximum: 160), spacing: 16)]

    var body: some View {
        NavigationView {
            ZStack {
                Color.black.ignoresSafeArea()

                if filteredSeries.isEmpty {
                    EmptySeriesView()
                } else {
                    ScrollView {
                        LazyVGrid(columns: columns, spacing: 20) {
                            ForEach(filteredSeries) { series in
                                SeriesCardView(series: series)
                                    .onTapGesture {
                                        selectedSeries = series
                                    }
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle("TV Shows")
            .navigationBarTitleDisplayMode(.large)
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always))
            .sheet(item: $selectedSeries) { series in
                SeriesDetailView(series: series)
                    .environmentObject(mediaLibrary)
                    .environmentObject(playbackManager)
            }
            .preferredColorScheme(.dark)
        }
        .navigationViewStyle(.stack)
    }
}

// MARK: - SeriesCardView
struct SeriesCardView: View {
    let series: TVSeries

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottomLeading) {
                SeriesPosterView(series: series, width: 120, height: 175)
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.white.opacity(0.1), lineWidth: 1)
                    )

                // Episode count badge
                HStack(spacing: 2) {
                    Image(systemName: "play.rectangle.fill")
                        .font(.system(size: 9))
                    Text("\(series.totalEpisodeCount) ep")
                        .font(.system(size: 10, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(Color.black.opacity(0.75))
                .cornerRadius(4)
                .padding(6)
            }

            Text(series.name)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(.white)
                .lineLimit(2)
                .frame(width: 120, alignment: .leading)

            if let year = series.firstAirYear {
                Text(String(year))
                    .font(.system(size: 10))
                    .foregroundColor(.gray)
            }
        }
    }
}

// MARK: - SeriesDetailView
/// Detailed view of a TV series with all seasons and episodes
struct SeriesDetailView: View {
    let series: TVSeries
    @EnvironmentObject var mediaLibrary: MediaLibrary
    @EnvironmentObject var playbackManager: PlaybackStateManager
    @Environment(\.dismiss) var dismiss

    @State private var selectedSeason: Int = 1
    @State private var selectedItem: MediaItem?
    @State private var isFetchingMetadata = false

    var seasons: [Int] {
        series.seasons.keys.sorted()
    }

    var episodesForSelectedSeason: [MediaItem] {
        mediaLibrary.episodes(for: series, season: selectedSeason)
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.black.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 0) {
                        // Hero backdrop
                        SeriesHeroView(series: series)

                        // Series info
                        VStack(alignment: .leading, spacing: 12) {
                            // Title and rating
                            HStack {
                                Text(series.name)
                                    .font(.title).bold()
                                    .foregroundColor(.white)
                                Spacer()
                                if let rating = series.rating {
                                    Label(String(format: "%.1f", rating), systemImage: "star.fill")
                                        .font(.subheadline)
                                        .foregroundColor(.yellow)
                                }
                            }

                            // Meta info
                            HStack(spacing: 12) {
                                if let year = series.firstAirYear {
                                    Text(String(year)).foregroundColor(.gray)
                                }
                                if series.seasonCount > 1 {
                                    Text("\(series.seasonCount) Seasons").foregroundColor(.gray)
                                }
                                Text("\(series.totalEpisodeCount) Episodes").foregroundColor(.gray)
                            }
                            .font(.subheadline)

                            // Genres
                            if !series.genres.isEmpty {
                                HStack {
                                    ForEach(series.genres.prefix(3), id: \.self) { genre in
                                        Text(genre)
                                            .font(.caption)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(Color.orange.opacity(0.3))
                                            .foregroundColor(.orange)
                                            .cornerRadius(4)
                                    }
                                }
                            }

                            // Overview
                            if let overview = series.overview {
                                Text(overview)
                                    .font(.body)
                                    .foregroundColor(.gray)
                                    .lineLimit(4)
                            }

                            // Play next episode button
                            PlayNextButton(series: series, mediaLibrary: mediaLibrary) { item in
                                selectedItem = item
                            }
                        }
                        .padding()

                        // Season selector
                        if seasons.count > 1 {
                            SeasonSelector(seasons: seasons, selectedSeason: $selectedSeason)
                        }

                        // Episodes list
                        VStack(spacing: 0) {
                            ForEach(episodesForSelectedSeason) { episode in
                                EpisodeRow(episode: episode, playbackState: playbackManager.state(for: episode.id))
                                    .onTapGesture { selectedItem = episode }
                                Divider()
                                    .background(Color.gray.opacity(0.2))
                                    .padding(.leading, 80)
                            }
                        }
                        .padding(.bottom, 20)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.white)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    if isFetchingMetadata {
                        ProgressView().tint(.orange)
                    } else {
                        Button {
                            isFetchingMetadata = true
                            Task {
                                await mediaLibrary.refreshSeriesMetadata(seriesID: series.id)
                                await MainActor.run { isFetchingMetadata = false }
                            }
                        } label: {
                            Image(systemName: "arrow.clockwise")
                                .foregroundColor(.orange)
                        }
                    }
                }
            }
            .fullScreenCover(item: $selectedItem) { item in
                PlayerView(item: item)
                    .environmentObject(mediaLibrary)
                    .environmentObject(playbackManager)
            }
            .preferredColorScheme(.dark)
            .onAppear {
                if let first = seasons.first {
                    selectedSeason = first
                }
            }
        }
    }
}

// MARK: - SeriesHeroView
struct SeriesHeroView: View {
    let series: TVSeries
    @State private var backdropImage: UIImage?

    var body: some View {
        ZStack(alignment: .bottom) {
            if let img = backdropImage {
                Image(uiImage: img)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(height: 220)
                    .clipped()
                    .overlay(
                        LinearGradient(
                            colors: [.clear, .black],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
            } else {
                ZStack {
                    LinearGradient(
                        colors: [Color.indigo, Color.black],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    SeriesPosterView(series: series, width: 80, height: 120)
                        .cornerRadius(8)
                }
                .frame(height: 220)
            }
        }
        .onAppear {
            if let path = series.backdropPath {
                backdropImage = MetadataService.shared.loadCachedPoster(named: path)
            }
        }
    }
}

// MARK: - SeasonSelector
struct SeasonSelector: View {
    let seasons: [Int]
    @Binding var selectedSeason: Int

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(seasons, id: \.self) { season in
                    Button {
                        selectedSeason = season
                    } label: {
                        Text("Season \(season)")
                            .font(.subheadline).fontWeight(.semibold)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(selectedSeason == season ? Color.orange : Color.gray.opacity(0.3))
                            .foregroundColor(selectedSeason == season ? .black : .white)
                            .cornerRadius(20)
                    }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }
}

// MARK: - EpisodeRow
struct EpisodeRow: View {
    let episode: MediaItem
    let playbackState: PlaybackState

    var body: some View {
        HStack(spacing: 12) {
            // Thumbnail / poster
            ZStack(alignment: .bottomLeading) {
                MediaPosterView(item: episode, width: 130, height: 73)
                    .cornerRadius(6)

                if playbackState.shouldResume {
                    ProgressView(value: playbackState.progressPercentage)
                        .tint(.orange)
                        .frame(width: 130)
                        .padding(.bottom, 2)
                }

                if playbackState.isCompleted {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                        .background(Color.black.opacity(0.5).clipShape(Circle()))
                        .padding(4)
                }
            }
            .frame(width: 130, height: 73)

            VStack(alignment: .leading, spacing: 4) {
                let epStr = episode.episodeNumber.map { String(format: "Ep %d", $0) } ?? ""
                Text(epStr)
                    .font(.caption)
                    .foregroundColor(.orange)
                Text(episode.episodeTitle ?? episode.title)
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundColor(.white)
                    .lineLimit(2)
                if let overview = episode.overview {
                    Text(overview)
                        .font(.caption)
                        .foregroundColor(.gray)
                        .lineLimit(2)
                }
            }

            Spacer()
            Image(systemName: "play.fill")
                .foregroundColor(.orange)
                .padding(.trailing, 4)
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
    }
}

// MARK: - PlayNextButton
struct PlayNextButton: View {
    let series: TVSeries
    let mediaLibrary: MediaLibrary
    let onSelect: (MediaItem) -> Void

    var nextEpisode: MediaItem? {
        // Find the first unwatched episode
        let allEps = mediaLibrary.episodes(for: series)
        return allEps.first(where: { !$0.isWatched }) ?? allEps.first
    }

    var body: some View {
        if let ep = nextEpisode {
            Button {
                onSelect(ep)
            } label: {
                HStack {
                    Image(systemName: "play.fill")
                    Text("Play \(ep.displayTitle)")
                        .lineLimit(1)
                }
                .font(.headline)
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color.orange)
                .cornerRadius(10)
            }
        }
    }
}

// MARK: - EmptySeriesView
struct EmptySeriesView: View {
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "tv.slash")
                .font(.system(size: 64))
                .foregroundColor(.orange.opacity(0.7))
            Text("No TV Shows Yet")
                .font(.title2).bold()
                .foregroundColor(.white)
            Text("Add video files from the Movies tab.\nTV episodes are automatically grouped by series.")
                .font(.body)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
    }
}
