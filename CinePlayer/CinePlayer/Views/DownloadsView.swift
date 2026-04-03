import SwiftUI

// MARK: - DownloadsView
/// Shows files imported into the app and allows management
struct DownloadsView: View {
    @EnvironmentObject var mediaLibrary: MediaLibrary
    @EnvironmentObject var playbackManager: PlaybackStateManager
    @State private var showFilePicker = false
    @State private var selectedItem: MediaItem?
    @State private var itemToDelete: MediaItem?
    @State private var showDeleteConfirm = false

    var body: some View {
        NavigationView {
            ZStack {
                Color.black.ignoresSafeArea()

                if mediaLibrary.allItems.isEmpty {
                    VStack(spacing: 20) {
                        Image(systemName: "arrow.down.circle")
                            .font(.system(size: 64))
                            .foregroundColor(.orange.opacity(0.7))
                        Text("No Files Yet")
                            .font(.title2).bold()
                            .foregroundColor(.white)
                        Text("Add video files to your library.")
                            .foregroundColor(.gray)
                        Button {
                            showFilePicker = true
                        } label: {
                            Label("Import Files", systemImage: "plus.circle.fill")
                                .font(.headline)
                                .foregroundColor(.black)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 12)
                                .background(Color.orange)
                                .cornerRadius(12)
                        }
                    }
                } else {
                    List {
                        ForEach(mediaLibrary.allItems.sorted { $0.dateAdded > $1.dateAdded }) { item in
                            DownloadRow(item: item, playbackState: playbackManager.state(for: item.id))
                                .listRowBackground(Color.black)
                                .contentShape(Rectangle())
                                .onTapGesture { selectedItem = item }
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button(role: .destructive) {
                                        itemToDelete = item
                                        showDeleteConfirm = true
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                                .swipeActions(edge: .leading) {
                                    Button {
                                        Task { await mediaLibrary.refreshMetadata(for: item) }
                                    } label: {
                                        Label("Refresh", systemImage: "arrow.clockwise")
                                    }
                                    .tint(.blue)

                                    Button {
                                        Task { await mediaLibrary.refreshSubtitle(for: item) }
                                    } label: {
                                        Label("Subtitles", systemImage: "captions.bubble")
                                    }
                                    .tint(.orange)
                                }
                        }
                    }
                    .listStyle(.plain)
                    .background(Color.black)
                }
            }
            .navigationTitle("All Files")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showFilePicker = true } label: {
                        Image(systemName: "plus.circle.fill").foregroundColor(.orange)
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
            .alert("Delete File?", isPresented: $showDeleteConfirm, presenting: itemToDelete) { item in
                Button("Delete", role: .destructive) {
                    mediaLibrary.removeItem(item)
                }
                Button("Cancel", role: .cancel) {}
            } message: { item in
                Text("Remove \"\(item.title)\" from your library?")
            }
            .preferredColorScheme(.dark)
        }
        .navigationViewStyle(.stack)
    }
}

// MARK: - DownloadRow
struct DownloadRow: View {
    let item: MediaItem
    let playbackState: PlaybackState

    var body: some View {
        HStack(spacing: 12) {
            // Thumbnail
            MediaPosterView(item: item, width: 56, height: 80)
                .cornerRadius(6)

            VStack(alignment: .leading, spacing: 4) {
                Text(item.displayTitle)
                    .font(.callout).bold()
                    .foregroundColor(.white)
                    .lineLimit(2)

                // File info
                HStack(spacing: 8) {
                    if item.mediaType == .episode {
                        Label(item.seriesName ?? "", systemImage: "tv")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                    Text(formatFileSize(item.fileSize))
                        .font(.caption)
                        .foregroundColor(.gray)
                    if item.duration > 0 {
                        Text(formatDuration(item.duration))
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }

                // Subtitle info
                if !item.subtitleFiles.isEmpty {
                    HStack(spacing: 4) {
                        Image(systemName: "captions.bubble.fill")
                            .font(.system(size: 10))
                            .foregroundColor(.green)
                        Text("\(item.subtitleFiles.count) subtitle\(item.subtitleFiles.count > 1 ? "s" : "")")
                            .font(.caption)
                            .foregroundColor(.green)
                    }
                }

                // Progress
                if playbackState.shouldResume {
                    HStack(spacing: 4) {
                        ProgressView(value: playbackState.progressPercentage)
                            .tint(.orange)
                            .frame(width: 80)
                        Text(formatDuration(playbackState.remainingTime) + " left")
                            .font(.caption2)
                            .foregroundColor(.gray)
                    }
                } else if playbackState.isCompleted {
                    Label("Watched", systemImage: "checkmark.circle.fill")
                        .font(.caption)
                        .foregroundColor(.green)
                }
            }

            Spacer()
        }
        .padding(.vertical, 6)
    }

    private func formatFileSize(_ bytes: Int64) -> String {
        let gb = Double(bytes) / 1_073_741_824
        if gb >= 1 { return String(format: "%.1f GB", gb) }
        let mb = Double(bytes) / 1_048_576
        if mb >= 1 { return String(format: "%.0f MB", mb) }
        return "\(bytes / 1024) KB"
    }

    private func formatDuration(_ seconds: TimeInterval) -> String {
        let s = Int(seconds)
        if s >= 3600 {
            return String(format: "%dh %dm", s / 3600, (s % 3600) / 60)
        }
        return String(format: "%dm %ds", s / 60, s % 60)
    }
}
