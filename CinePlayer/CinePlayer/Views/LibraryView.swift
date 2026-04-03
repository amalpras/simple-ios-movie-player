import SwiftUI
import UniformTypeIdentifiers

// MARK: - LibraryView
struct LibraryView: View {
    @EnvironmentObject var mediaLibrary: MediaLibrary
    @EnvironmentObject var playbackManager: PlaybackStateManager
    @StateObject private var settings = AppSettings.shared

    @State private var searchText = ""
    @State private var showFilePicker = false
    @State private var selectedItem: MediaItem?
    @State private var sortOrder: SortOrder = .dateAdded
    @State private var showSortMenu = false

    enum SortOrder: String, CaseIterable {
        case dateAdded = "Recently Added"
        case title = "Title"
        case lastWatched = "Recently Watched"
    }

    var filteredMovies: [MediaItem] {
        let movies = mediaLibrary.movies
        let searched = searchText.isEmpty ? movies : movies.filter {
            $0.title.localizedCaseInsensitiveContains(searchText)
        }
        switch sortOrder {
        case .dateAdded: return searched.sorted { $0.dateAdded > $1.dateAdded }
        case .title: return searched.sorted { $0.title < $1.title }
        case .lastWatched: return searched.sorted {
            ($0.lastWatchedDate ?? .distantPast) > ($1.lastWatchedDate ?? .distantPast)
        }
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color.black.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        // Continue Watching
                        if !mediaLibrary.continueWatching.isEmpty {
                            ContinueWatchingSection(items: mediaLibrary.continueWatching) { item in
                                selectedItem = item
                            }
                        }

                        // Recently Added
                        if !mediaLibrary.recentlyAdded.isEmpty && searchText.isEmpty {
                            RecentlyAddedSection(items: Array(mediaLibrary.recentlyAdded.prefix(10))) { item in
                                selectedItem = item
                            }
                        }

                        // All Movies
                        if !filteredMovies.isEmpty {
                            VStack(alignment: .leading) {
                                HStack {
                                    Text("Movies")
                                        .font(.title2).bold()
                                        .foregroundColor(.white)
                                    Spacer()
                                    Text("\(filteredMovies.count)")
                                        .font(.subheadline)
                                        .foregroundColor(.gray)
                                }
                                .padding(.horizontal)

                                if settings.libraryViewStyle == .grid {
                                    MovieGridContent(items: filteredMovies) { item in
                                        selectedItem = item
                                    }
                                } else {
                                    MovieListContent(items: filteredMovies) { item in
                                        selectedItem = item
                                    }
                                }
                            }
                        }

                        // Empty state
                        if filteredMovies.isEmpty && mediaLibrary.continueWatching.isEmpty {
                            EmptyLibraryView { showFilePicker = true }
                        }
                    }
                    .padding(.top)
                }
            }
            .navigationTitle("Movies")
            .navigationBarTitleDisplayMode(.large)
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always))
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    // Sort menu
                    Menu {
                        ForEach(SortOrder.allCases, id: \.self) { order in
                            Button {
                                sortOrder = order
                            } label: {
                                if sortOrder == order {
                                    Label(order.rawValue, systemImage: "checkmark")
                                } else {
                                    Text(order.rawValue)
                                }
                            }
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                            .foregroundColor(.orange)
                    }

                    // View style toggle
                    Button {
                        settings.libraryViewStyle = settings.libraryViewStyle == .grid ? .list : .grid
                    } label: {
                        Image(systemName: settings.libraryViewStyle == .grid ? "list.bullet" : "square.grid.2x2")
                            .foregroundColor(.orange)
                    }

                    // Add media
                    Button {
                        showFilePicker = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .foregroundColor(.orange)
                    }
                }
            }
            .sheet(isPresented: $showFilePicker) {
                DocumentPicker { urls in
                    Task { await mediaLibrary.addMedia(from: urls) }
                }
            }
            .fullScreenCover(item: $selectedItem) { item in
                PlayerView(item: item)
                    .environmentObject(mediaLibrary)
                    .environmentObject(playbackManager)
            }
            .preferredColorScheme(.dark)
        }
        .navigationViewStyle(.stack)
    }
}

// MARK: - ContinueWatchingSection
struct ContinueWatchingSection: View {
    let items: [MediaItem]
    let onSelect: (MediaItem) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Continue Watching")
                .font(.title2).bold()
                .foregroundColor(.white)
                .padding(.horizontal)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(items) { item in
                        ContinueWatchingCard(item: item)
                            .onTapGesture { onSelect(item) }
                    }
                }
                .padding(.horizontal)
            }
        }
    }
}

// MARK: - ContinueWatchingCard
struct ContinueWatchingCard: View {
    let item: MediaItem

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack(alignment: .bottom) {
                // Poster or backdrop
                MediaPosterView(item: item, width: 160, height: 90)
                    .cornerRadius(8)

                // Progress bar
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.white.opacity(0.3))
                            .frame(height: 3)
                        Rectangle()
                            .fill(Color.orange)
                            .frame(width: geo.size.width * item.progressPercentage, height: 3)
                    }
                }
                .frame(height: 3)
                .padding(.horizontal, 4)
                .padding(.bottom, 4)
            }
            .frame(width: 160, height: 90)

            Text(item.displayTitle)
                .font(.caption)
                .foregroundColor(.white)
                .lineLimit(2)
                .frame(width: 160, alignment: .leading)
        }
    }
}

// MARK: - RecentlyAddedSection
struct RecentlyAddedSection: View {
    let items: [MediaItem]
    let onSelect: (MediaItem) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Recently Added")
                .font(.title2).bold()
                .foregroundColor(.white)
                .padding(.horizontal)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(items) { item in
                        SmallMovieCard(item: item)
                            .onTapGesture { onSelect(item) }
                    }
                }
                .padding(.horizontal)
            }
        }
    }
}

// MARK: - SmallMovieCard
struct SmallMovieCard: View {
    let item: MediaItem

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            MediaPosterView(item: item, width: 110, height: 160)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.white.opacity(0.1), lineWidth: 1)
                )

            Text(item.title)
                .font(.caption2)
                .foregroundColor(.white)
                .lineLimit(2)
                .frame(width: 110, alignment: .leading)
        }
    }
}

// MARK: - MovieGridContent
struct MovieGridContent: View {
    let items: [MediaItem]
    let onSelect: (MediaItem) -> Void

    private let columns = [
        GridItem(.adaptive(minimum: 110, maximum: 140), spacing: 12)
    ]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 16) {
            ForEach(items) { item in
                MovieCardView(item: item)
                    .onTapGesture { onSelect(item) }
            }
        }
        .padding(.horizontal)
    }
}

// MARK: - MovieListContent
struct MovieListContent: View {
    let items: [MediaItem]
    let onSelect: (MediaItem) -> Void

    var body: some View {
        LazyVStack(spacing: 0) {
            ForEach(items) { item in
                MovieListRow(item: item)
                    .onTapGesture { onSelect(item) }
                Divider()
                    .background(Color.gray.opacity(0.3))
                    .padding(.leading, 76)
            }
        }
        .padding(.horizontal)
    }
}

// MARK: - MovieListRow
struct MovieListRow: View {
    let item: MediaItem

    var body: some View {
        HStack(spacing: 12) {
            MediaPosterView(item: item, width: 56, height: 80)
                .cornerRadius(6)

            VStack(alignment: .leading, spacing: 4) {
                Text(item.title)
                    .font(.callout).bold()
                    .foregroundColor(.white)
                    .lineLimit(2)
                if let year = item.releaseYear {
                    Text(String(year))
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                if !item.genres.isEmpty {
                    Text(item.genres.prefix(2).joined(separator: " · "))
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                if item.lastPlaybackPosition > 5 && !item.isWatched {
                    ProgressView(value: item.progressPercentage)
                        .tint(.orange)
                        .frame(maxWidth: 150)
                }
            }

            Spacer()

            VStack(alignment: .trailing) {
                if let rating = item.rating {
                    Label(String(format: "%.1f", rating), systemImage: "star.fill")
                        .font(.caption)
                        .foregroundColor(.yellow)
                }
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
        .padding(.vertical, 8)
    }
}

// MARK: - EmptyLibraryView
struct EmptyLibraryView: View {
    let onAdd: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "film.stack")
                .font(.system(size: 64))
                .foregroundColor(.orange.opacity(0.7))
            Text("No Movies Yet")
                .font(.title2).bold()
                .foregroundColor(.white)
            Text("Tap the + button to add video files from your device.")
                .font(.body)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Button(action: onAdd) {
                Label("Add Movies", systemImage: "plus.circle.fill")
                    .font(.headline)
                    .foregroundColor(.black)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.orange)
                    .cornerRadius(12)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - MediaPosterView
/// Shows cached poster or a placeholder
struct MediaPosterView: View {
    let item: MediaItem
    let width: CGFloat
    let height: CGFloat

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: width, height: height)
                    .clipped()
            } else {
                ZStack {
                    LinearGradient(
                        colors: [Color.gray.opacity(0.5), Color.black.opacity(0.8)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    VStack(spacing: 4) {
                        Image(systemName: item.mediaType == .episode ? "tv" : "film")
                            .font(.system(size: width * 0.25))
                            .foregroundColor(.white.opacity(0.6))
                        Text(item.title)
                            .font(.system(size: min(width * 0.1, 10)))
                            .foregroundColor(.white.opacity(0.7))
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 4)
                    }
                }
                .frame(width: width, height: height)
            }
        }
        .onAppear { loadImage() }
    }

    private func loadImage() {
        guard let posterPath = item.posterPath else { return }
        image = MetadataService.shared.loadCachedPoster(named: posterPath)
    }
}

// MARK: - SeriesPosterView
struct SeriesPosterView: View {
    let series: TVSeries
    let width: CGFloat
    let height: CGFloat

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: width, height: height)
                    .clipped()
            } else {
                ZStack {
                    LinearGradient(
                        colors: [Color.indigo.opacity(0.5), Color.black.opacity(0.8)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    VStack(spacing: 4) {
                        Image(systemName: "tv")
                            .font(.system(size: width * 0.25))
                            .foregroundColor(.white.opacity(0.6))
                        Text(series.name)
                            .font(.system(size: min(width * 0.1, 10)))
                            .foregroundColor(.white.opacity(0.7))
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 4)
                    }
                }
                .frame(width: width, height: height)
            }
        }
        .onAppear { loadImage() }
    }

    private func loadImage() {
        guard let posterPath = series.posterPath else { return }
        image = MetadataService.shared.loadCachedPoster(named: posterPath)
    }
}

// MARK: - DocumentPicker
struct DocumentPicker: UIViewControllerRepresentable {
    let onPick: ([URL]) -> Void

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let types: [UTType] = [.movie, .video, .mpeg4Movie, UTType("public.avi") ?? .video,
                               UTType("org.matroska.mkv") ?? .video].compactMap { $0 }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.allowsMultipleSelection = true
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPick: onPick) }

    class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: ([URL]) -> Void
        init(onPick: @escaping ([URL]) -> Void) { self.onPick = onPick }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            onPick(urls)
        }
    }
}
