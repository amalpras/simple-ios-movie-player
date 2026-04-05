import SwiftUI
import AVKit
import AVFoundation
import Combine
import MediaPlayer

// MARK: - PlayerView
/// Full-featured video player with movie/TV-specific controls
struct PlayerView: View {
    let item: MediaItem

    @EnvironmentObject var mediaLibrary: MediaLibrary
    @EnvironmentObject var playbackManager: PlaybackStateManager
    @Environment(\.dismiss) var dismiss

    @StateObject private var playerVM: PlayerViewModel

    init(item: MediaItem) {
        self.item = item
        _playerVM = StateObject(wrappedValue: PlayerViewModel(item: item))
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // Video player layer
            VideoPlayerLayerView(player: playerVM.player, videoGravity: playerVM.videoGravity)
                .ignoresSafeArea()
                .onTapGesture {
                    playerVM.toggleControls()
                }

            // Subtitle overlay
            VStack {
                Spacer()
                SubtitleOverlayView(playerVM: playerVM)
                    .padding(.bottom, playerVM.showControls ? 110 : 24)
            }

            // Controls overlay
            if playerVM.showControls {
                PlayerControlsView(playerVM: playerVM, item: item) {
                    saveStateAndDismiss()
                }
                .transition(.opacity)
            }

            // Next episode countdown
            if playerVM.showNextEpisodeCountdown, let next = playerVM.nextEpisode {
                NextEpisodeCountdownView(
                    nextItem: next,
                    countdown: playerVM.nextEpisodeCountdown
                ) {
                    playerVM.playNextEpisode()
                } onCancel: {
                    playerVM.cancelNextEpisodeCountdown()
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            // Loading indicator
            if playerVM.isLoading {
                ProgressView()
                    .scaleEffect(1.5)
                    .tint(.white)
            }

            // Error message
            if let error = playerVM.errorMessage {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.largeTitle)
                        .foregroundColor(.yellow)
                    Text(error)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    Button("Dismiss") { dismiss() }
                        .foregroundColor(.orange)
                }
            }
        }
        .statusBarHidden(true)
        .onAppear {
            playerVM.configure(mediaLibrary: mediaLibrary)
            playerVM.startPlayback()
        }
        .onDisappear {
            saveStateAndDismiss()
        }
        .animation(.easeInOut(duration: 0.25), value: playerVM.showControls)
        .animation(.spring(), value: playerVM.showNextEpisodeCountdown)
    }

    private func saveStateAndDismiss() {
        let pos = playerVM.currentTime
        let dur = playerVM.duration
        if pos > 0 {
            Task {
                await mediaLibrary.updatePlaybackPosition(
                    itemID: item.id,
                    position: pos,
                    duration: dur
                )
            }
        }
        playerVM.pause()
    }
}

// MARK: - VideoPlayerLayerView
struct VideoPlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    let videoGravity: AVLayerVideoGravity

    func makeUIView(context: Context) -> PlayerUIView {
        let view = PlayerUIView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = videoGravity
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ uiView: PlayerUIView, context: Context) {
        uiView.playerLayer.videoGravity = videoGravity
    }
}

class PlayerUIView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

// MARK: - PlayerViewModel
@MainActor
final class PlayerViewModel: ObservableObject {
    let player: AVPlayer
    private(set) var item: MediaItem

    // Playback state
    @Published var isPlaying = false
    @Published var isLoading = true
    @Published var currentTime: TimeInterval = 0
    @Published var duration: TimeInterval = 0
    @Published var playbackRate: Float = 1.0
    @Published var isSeeking = false

    // Controls visibility
    @Published var showControls = true
    private var controlsHideTimer: Timer?

    // Subtitle
    @Published var currentSubtitleText: String? = nil
    @Published var subtitleDelay: TimeInterval = 0
    private var subtitleCues: [SubtitleParser.Cue] = []
    @Published var selectedSubtitleIndex: Int?

    // Audio
    @Published var availableAudioTracks: [AudioTrack] = []
    @Published var selectedAudioTrackIndex: Int = 0

    // Next episode
    @Published var showNextEpisodeCountdown = false
    @Published var nextEpisodeCountdown = 5
    @Published var nextEpisode: MediaItem?
    private var nextEpisodeTimer: Timer?

    // Settings panel
    @Published var showSettings = false
    @Published var showSubtitleSettings = false
    @Published var showAudioTrackPicker = false
    @Published var showSubtitlePicker = false
    @Published var showSpeedPicker = false

    // Aspect ratio / video gravity
    @Published var videoGravity: AVLayerVideoGravity = .resizeAspect

    // Error
    @Published var errorMessage: String?

    private var timeObserver: Any?
    private var statusObserver: NSKeyValueObservation?
    private var cancellables = Set<AnyCancellable>()
    private weak var mediaLibrary: MediaLibrary?

    init(item: MediaItem) {
        self.item = item
        self.player = AVPlayer(url: item.fileURL)
        self.selectedSubtitleIndex = item.selectedSubtitleIndex
        self.subtitleDelay = AppSettings.shared.subtitleDelay
    }

    func configure(mediaLibrary: MediaLibrary) {
        self.mediaLibrary = mediaLibrary
        self.nextEpisode = mediaLibrary.nextEpisode(after: item)
        self.playbackRate = AppSettings.shared.defaultPlaybackSpeed
    }

    func startPlayback() {
        // Observe player status
        statusObserver = player.currentItem?.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                switch item.status {
                case .readyToPlay:
                    self.isLoading = false
                    self.duration = CMTimeGetSeconds(item.duration)
                    self.setupAudioTracks()
                    self.resumeIfNeeded()
                case .failed:
                    self.isLoading = false
                    self.errorMessage = item.error?.localizedDescription ?? "Failed to load video."
                default:
                    break
                }
            }
        }

        // Periodic time observer (every 0.5s)
        let interval = CMTime(seconds: 0.5, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self else { return }
            Task { @MainActor [weak self] in
                guard let self, !self.isSeeking else { return }
                self.currentTime = CMTimeGetSeconds(time)
                self.updateSubtitle()
                self.checkNextEpisodeTrigger()
            }
        }

        // Load subtitle cues
        loadSubtitleCues()

        // Setup remote control
        setupNowPlaying()
        setupRemoteControls()

        // Auto-hide controls after 3s
        scheduleControlsHide()
    }

    // MARK: - Playback Controls

    func play() {
        player.rate = playbackRate
        isPlaying = true
        updateNowPlayingState()
    }

    func pause() {
        player.pause()
        isPlaying = false
        updateNowPlayingState()
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { play() }
        showControlsBriefly()
    }

    func seek(to time: TimeInterval) {
        let cmTime = CMTime(seconds: time, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
        isSeeking = true
        player.seek(to: cmTime, toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.isSeeking = false
                if self?.isPlaying == true {
                    self?.player.rate = self?.playbackRate ?? 1.0
                }
            }
        }
    }

    func skipForward() {
        seek(to: min(currentTime + Double(AppSettings.shared.skipForwardSeconds), duration))
        showControlsBriefly()
    }

    func skipBackward() {
        seek(to: max(currentTime - Double(AppSettings.shared.skipBackwardSeconds), 0))
        showControlsBriefly()
    }

    func setPlaybackRate(_ rate: Float) {
        playbackRate = rate
        if isPlaying {
            player.rate = rate
        }
        AppSettings.shared.defaultPlaybackSpeed = rate
    }

    func setAspectRatio(_ gravity: AVLayerVideoGravity) {
        videoGravity = gravity
    }

    // MARK: - Resume Playback

    private func resumeIfNeeded() {
        let state = PlaybackStateManager.shared.state(for: item.id)
        if AppSettings.shared.resumePlayback && state.shouldResume {
            seek(to: state.position)
        }
        play()
    }

    // MARK: - Next Episode

    private func checkNextEpisodeTrigger() {
        guard AppSettings.shared.autoPlayNextEpisode,
              nextEpisode != nil,
              duration > 0,
              !showNextEpisodeCountdown else { return }

        // Trigger countdown when 30s from end (or at 95%)
        let threshold = min(30.0, duration * 0.05)
        if duration - currentTime <= threshold && duration - currentTime > 0 {
            startNextEpisodeCountdown()
        }
    }

    private func startNextEpisodeCountdown() {
        showNextEpisodeCountdown = true
        nextEpisodeCountdown = AppSettings.shared.countdownToNextEpisode
        nextEpisodeTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.nextEpisodeCountdown -= 1
                if self.nextEpisodeCountdown <= 0 {
                    self.playNextEpisode()
                }
            }
        }
    }

    func playNextEpisode() {
        cancelNextEpisodeCountdown()
        guard let next = nextEpisode else { return }
        // Save current position
        Task { [weak self] in
            guard let self else { return }
            await self.mediaLibrary?.updatePlaybackPosition(
                itemID: self.item.id,
                position: self.duration,
                duration: self.duration
            )
        }
        // Switch to next item
        item = next
        let newPlayerItem = AVPlayerItem(url: next.fileURL)
        player.replaceCurrentItem(with: newPlayerItem)
        nextEpisode = mediaLibrary?.nextEpisode(after: next)
        selectedSubtitleIndex = next.selectedSubtitleIndex
        loadSubtitleCues()
        isLoading = true
        isPlaying = false
        // Setup status observer for new item
        statusObserver = player.currentItem?.observe(\.status, options: [.new]) { [weak self] playerItem, _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                if playerItem.status == .readyToPlay {
                    self.isLoading = false
                    self.duration = CMTimeGetSeconds(playerItem.duration)
                    self.setupAudioTracks()
                    self.play()
                }
            }
        }
        setupNowPlaying()
    }

    func cancelNextEpisodeCountdown() {
        nextEpisodeTimer?.invalidate()
        nextEpisodeTimer = nil
        showNextEpisodeCountdown = false
        nextEpisodeCountdown = AppSettings.shared.countdownToNextEpisode
    }

    // MARK: - Controls Visibility

    func toggleControls() {
        showControls.toggle()
        if showControls { scheduleControlsHide() }
    }

    func showControlsBriefly() {
        showControls = true
        scheduleControlsHide()
    }

    private func scheduleControlsHide() {
        controlsHideTimer?.invalidate()
        controlsHideTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: false) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.isPlaying else { return }
                withAnimation { self.showControls = false }
            }
        }
    }

    // MARK: - Subtitles

    func selectSubtitle(index: Int?) {
        selectedSubtitleIndex = index
        loadSubtitleCues()
        currentSubtitleText = nil
    }

    private func loadSubtitleCues() {
        guard let idx = selectedSubtitleIndex,
              idx < item.subtitleFiles.count,
              !item.subtitleFiles[idx].isEmbedded,
              let url = item.subtitleFiles[idx].fileURL else {
            subtitleCues = []
            return
        }
        subtitleCues = SubtitleParser.parse(fileURL: url)
    }

    private func updateSubtitle() {
        let adjustedTime = currentTime - subtitleDelay
        let cue = subtitleCues.first(where: {
            adjustedTime >= $0.startTime && adjustedTime <= $0.endTime
        })
        let newText = cue?.text
        if newText != currentSubtitleText {
            currentSubtitleText = newText
        }
    }

    // MARK: - Audio Tracks

    private func setupAudioTracks() {
        guard let asset = player.currentItem?.asset else { return }
        Task {
            let tracks = (try? await asset.loadTracks(withMediaType: .audio)) ?? []
            var audioTracks: [AudioTrack] = []
            for (idx, track) in tracks.enumerated() {
                let locale = (try? await track.load(.languageCode)).flatMap { $0 } ?? "und"
                let name = Locale(identifier: "en").localizedString(forLanguageCode: locale) ?? "Track \(idx + 1)"
                audioTracks.append(AudioTrack(language: locale, displayName: name, trackIndex: idx))
            }
            await MainActor.run {
                self.availableAudioTracks = audioTracks
            }
        }
    }

    // MARK: - Now Playing / Remote Controls

    private func setupNowPlaying() {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: item.title,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? playbackRate : 0,
        ]
        if let seriesName = item.seriesName {
            info[MPMediaItemPropertyAlbumTitle] = seriesName
        }
        if let ep = item.episodeNumber {
            info[MPNowPlayingInfoPropertyChapterNumber] = ep
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func updateNowPlayingState() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? playbackRate : 0
    }

    private func setupRemoteControls() {
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.play() }
            return .success
        }
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.pause() }
            return .success
        }
        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.togglePlayPause() }
            return .success
        }
        commandCenter.skipForwardCommand.preferredIntervals = [NSNumber(value: AppSettings.shared.skipForwardSeconds)]
        commandCenter.skipForwardCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.skipForward() }
            return .success
        }
        commandCenter.skipBackwardCommand.preferredIntervals = [NSNumber(value: AppSettings.shared.skipBackwardSeconds)]
        commandCenter.skipBackwardCommand.addTarget { [weak self] _ in
            Task { @MainActor [weak self] in self?.skipBackward() }
            return .success
        }
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            if let e = event as? MPChangePlaybackPositionCommandEvent {
                Task { @MainActor [weak self] in self?.seek(to: e.positionTime) }
            }
            return .success
        }
    }

    deinit {
        if let observer = timeObserver {
            player.removeTimeObserver(observer)
        }
        controlsHideTimer?.invalidate()
        nextEpisodeTimer?.invalidate()
        MPRemoteCommandCenter.shared().playCommand.removeTarget(nil)
        MPRemoteCommandCenter.shared().pauseCommand.removeTarget(nil)
        MPRemoteCommandCenter.shared().togglePlayPauseCommand.removeTarget(nil)
        MPRemoteCommandCenter.shared().skipForwardCommand.removeTarget(nil)
        MPRemoteCommandCenter.shared().skipBackwardCommand.removeTarget(nil)
        MPRemoteCommandCenter.shared().changePlaybackPositionCommand.removeTarget(nil)
    }
}

// MARK: - PlayerControlsView
struct PlayerControlsView: View {
    @ObservedObject var playerVM: PlayerViewModel
    let item: MediaItem
    let onDismiss: () -> Void

    @State private var showMoreOptions = false

    var body: some View {
        ZStack {
            // Gradient overlays
            VStack {
                // Top gradient
                LinearGradient(
                    colors: [.black.opacity(0.8), .clear],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 120)
                Spacer()
                // Bottom gradient
                LinearGradient(
                    colors: [.clear, .black.opacity(0.8)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 160)
            }

            VStack {
                // Top bar
                TopControlBar(playerVM: playerVM, item: item, onDismiss: onDismiss)
                    .padding(.horizontal, 20)
                    .padding(.top, 8)

                Spacer()

                // Center playback controls
                CenterPlaybackControls(playerVM: playerVM)

                Spacer()

                // Bottom bar: seek bar + time + tools
                VStack(spacing: 8) {
                    SeekBar(playerVM: playerVM)
                        .padding(.horizontal, 20)

                    BottomControlBar(playerVM: playerVM, item: item)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 12)
                }
            }
        }
        .sheet(isPresented: $playerVM.showSubtitleSettings) {
            SubtitleSettingsSheet(playerVM: playerVM, item: item)
        }
        .sheet(isPresented: $playerVM.showSpeedPicker) {
            SpeedPickerSheet(playerVM: playerVM)
        }
    }
}

// MARK: - TopControlBar
struct TopControlBar: View {
    @ObservedObject var playerVM: PlayerViewModel
    let item: MediaItem
    let onDismiss: () -> Void

    var body: some View {
        HStack {
            Button(action: onDismiss) {
                Image(systemName: "chevron.down")
                    .font(.title3)
                    .foregroundColor(.white)
                    .padding(8)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(item.title)
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(1)
                if item.mediaType == .episode,
                   let series = item.seriesName,
                   let ep = item.episodeNumber,
                   let season = item.seasonNumber {
                    Text("\(series) · S\(String(format: "%02d", season))E\(String(format: "%02d", ep))")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
            }

            Spacer()

            // AirPlay button
            AirPlayButton()
                .frame(width: 44, height: 44)

            // Aspect ratio
            AspectRatioButton(playerVM: playerVM)

            // PiP button
            PiPButton(player: playerVM.player)
        }
    }
}

// MARK: - CenterPlaybackControls
struct CenterPlaybackControls: View {
    @ObservedObject var playerVM: PlayerViewModel
    @StateObject private var settings = AppSettings.shared

    // Valid SF Symbol numbers for gobackward/goforward
    private static let validSkipSeconds = [5, 10, 15, 30, 45, 60, 75, 90]

    private func backwardIconName(_ seconds: Int) -> String {
        let valid = Self.validSkipSeconds.contains(seconds) ? seconds : 10
        return "gobackward.\(valid)"
    }

    private func forwardIconName(_ seconds: Int) -> String {
        let valid = Self.validSkipSeconds.contains(seconds) ? seconds : 30
        return "goforward.\(valid)"
    }

    var body: some View {
        HStack(spacing: 48) {
            // Skip backward
            ControlButton(
                systemName: backwardIconName(settings.skipBackwardSeconds),
                size: 30
            ) {
                playerVM.skipBackward()
            }

            // Play / Pause
            ControlButton(
                systemName: playerVM.isPlaying ? "pause.fill" : "play.fill",
                size: 50
            ) {
                playerVM.togglePlayPause()
            }

            // Skip forward
            ControlButton(
                systemName: forwardIconName(settings.skipForwardSeconds),
                size: 30
            ) {
                playerVM.skipForward()
            }
        }
    }
}

// MARK: - SeekBar
struct SeekBar: View {
    @ObservedObject var playerVM: PlayerViewModel
    @GestureState private var isDragging = false
    @State private var seekingValue: Double = 0
    @State private var showTimeLabel = false

    var body: some View {
        VStack(spacing: 4) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    // Track background
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.white.opacity(0.3))
                        .frame(height: isDragging ? 6 : 4)

                    // Progress fill
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.orange)
                        .frame(
                            width: geo.size.width * (isDragging ? seekingValue / max(playerVM.duration, 1) : playerVM.currentTime / max(playerVM.duration, 1)),
                            height: isDragging ? 6 : 4
                        )

                    // Thumb
                    Circle()
                        .fill(Color.white)
                        .frame(width: isDragging ? 16 : 12, height: isDragging ? 16 : 12)
                        .offset(
                            x: geo.size.width * (isDragging ? seekingValue / max(playerVM.duration, 1) : playerVM.currentTime / max(playerVM.duration, 1)) - (isDragging ? 8 : 6)
                        )
                }
                .animation(.easeInOut(duration: 0.15), value: isDragging)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .updating($isDragging) { _, state, _ in state = true }
                        .onChanged { value in
                            let ratio = max(0, min(1, value.location.x / geo.size.width))
                            seekingValue = ratio * playerVM.duration
                            showTimeLabel = true
                        }
                        .onEnded { value in
                            let ratio = max(0, min(1, value.location.x / geo.size.width))
                            let time = ratio * playerVM.duration
                            playerVM.seek(to: time)
                            showTimeLabel = false
                        }
                )
            }
            .frame(height: 20)

            // Time labels
            HStack {
                Text(formatTime(isDragging ? seekingValue : playerVM.currentTime))
                    .font(.caption).monospacedDigit()
                    .foregroundColor(.white)
                Spacer()
                Text("-\(formatTime(max(0, playerVM.duration - playerVM.currentTime)))")
                    .font(.caption).monospacedDigit()
                    .foregroundColor(.gray)
            }
        }
    }

    private func formatTime(_ seconds: TimeInterval) -> String {
        let s = Int(seconds)
        if s >= 3600 {
            return String(format: "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        }
        return String(format: "%d:%02d", s / 60, s % 60)
    }
}

// MARK: - BottomControlBar
struct BottomControlBar: View {
    @ObservedObject var playerVM: PlayerViewModel
    let item: MediaItem

    var body: some View {
        HStack(spacing: 20) {
            // Speed
            Button {
                playerVM.showSpeedPicker = true
            } label: {
                Text(playerVM.playbackRate == 1.0 ? "1× " : "\(String(format: "%.2g", playerVM.playbackRate))× ")
                    .font(.subheadline.bold())
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.2))
                    .cornerRadius(6)
            }

            // Subtitles
            Button {
                playerVM.showSubtitleSettings = true
            } label: {
                Image(systemName: playerVM.selectedSubtitleIndex != nil ? "captions.bubble.fill" : "captions.bubble")
                    .font(.title3)
                    .foregroundColor(playerVM.selectedSubtitleIndex != nil ? .orange : .white)
            }

            Spacer()

            // Next episode (if TV)
            if item.mediaType == .episode, playerVM.nextEpisode != nil {
                Button {
                    playerVM.playNextEpisode()
                } label: {
                    Image(systemName: "forward.end.fill")
                        .font(.title3)
                        .foregroundColor(.white)
                }
            }
        }
    }
}

// MARK: - SubtitleOverlayView
struct SubtitleOverlayView: View {
    @ObservedObject var playerVM: PlayerViewModel
    @StateObject private var settings = AppSettings.shared

    var body: some View {
        if let text = playerVM.currentSubtitleText, !text.isEmpty {
            Text(text)
                .font(.system(size: settings.subtitleFontSize, weight: .semibold))
                .foregroundColor(subtitleColor)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Color.black.opacity(settings.subtitleBackgroundOpacity)
                        .cornerRadius(4)
                )
                .padding(.horizontal, 40)
        }
    }

    var subtitleColor: Color {
        switch settings.subtitleTextColor {
        case "yellow": return .yellow
        case "gray": return .gray
        default: return .white
        }
    }
}

// MARK: - SubtitleSettingsSheet
struct SubtitleSettingsSheet: View {
    @ObservedObject var playerVM: PlayerViewModel
    let item: MediaItem
    @StateObject private var settings = AppSettings.shared
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            List {
                // Subtitle track selection
                Section("Subtitle Track") {
                    Button("None") {
                        playerVM.selectSubtitle(index: nil)
                    }
                    .foregroundColor(playerVM.selectedSubtitleIndex == nil ? .orange : .primary)

                    ForEach(Array(item.subtitleFiles.enumerated()), id: \.offset) { idx, track in
                        Button {
                            playerVM.selectSubtitle(index: idx)
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(track.language)
                                    Text(track.source == .downloaded ? "Downloaded" :
                                         track.source == .embedded ? "Embedded" : "Local")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                if playerVM.selectedSubtitleIndex == idx {
                                    Image(systemName: "checkmark").foregroundColor(.orange)
                                }
                            }
                        }
                        .foregroundColor(.primary)
                    }
                }

                // Appearance
                Section("Appearance") {
                    HStack {
                        Text("Font Size")
                        Slider(value: $settings.subtitleFontSize, in: 12...32, step: 1)
                        Text("\(Int(settings.subtitleFontSize))")
                            .frame(width: 30)
                    }

                    Picker("Color", selection: $settings.subtitleTextColor) {
                        Text("White").tag("white")
                        Text("Yellow").tag("yellow")
                        Text("Gray").tag("gray")
                    }
                    .pickerStyle(.segmented)

                    HStack {
                        Text("Background Opacity")
                        Slider(value: $settings.subtitleBackgroundOpacity, in: 0...1, step: 0.1)
                        Text(String(format: "%.0f%%", settings.subtitleBackgroundOpacity * 100))
                            .frame(width: 40)
                    }
                }

                // Sync
                Section("Sync") {
                    HStack {
                        Text("Delay")
                        Slider(value: $playerVM.subtitleDelay, in: -5...5, step: 0.5)
                        Text(String(format: "%.1fs", playerVM.subtitleDelay))
                            .frame(width: 50)
                    }
                }

                // Download subtitle
                Section {
                    Button {
                        Task {
                            await mediaLibraryRefreshSubtitle()
                            dismiss()
                        }
                    } label: {
                        Label("Find Subtitle Online", systemImage: "arrow.down.circle")
                            .foregroundColor(.orange)
                    }
                }
            }
            .navigationTitle("Subtitles")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .preferredColorScheme(.dark)
        }
    }

    @MainActor
    private func mediaLibraryRefreshSubtitle() async {
        // Trigger subtitle download for current item
        await MediaLibrary.shared.refreshSubtitle(for: item)
    }
}

// MARK: - SpeedPickerSheet
struct SpeedPickerSheet: View {
    @ObservedObject var playerVM: PlayerViewModel
    @Environment(\.dismiss) var dismiss

    let speeds: [Float] = [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0]

    var body: some View {
        NavigationView {
            List(speeds, id: \.self) { speed in
                Button {
                    playerVM.setPlaybackRate(speed)
                    dismiss()
                } label: {
                    HStack {
                        Text(speed == 1.0 ? "Normal (1×)" : "\(String(format: "%.2g", speed))×")
                        Spacer()
                        if playerVM.playbackRate == speed {
                            Image(systemName: "checkmark").foregroundColor(.orange)
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
            .navigationTitle("Playback Speed")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .preferredColorScheme(.dark)
        }
        .presentationDetents([.medium])
    }
}

// MARK: - NextEpisodeCountdownView
struct NextEpisodeCountdownView: View {
    let nextItem: MediaItem
    let countdown: Int
    let onPlay: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .trailing, spacing: 12) {
            Spacer()
            HStack {
                Spacer()
                VStack(alignment: .trailing, spacing: 8) {
                    Text("Next Episode in \(countdown)s")
                        .font(.headline)
                        .foregroundColor(.white)
                    Text(nextItem.displayTitle)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .lineLimit(1)
                    HStack(spacing: 12) {
                        Button("Cancel", action: onCancel)
                            .font(.subheadline)
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.white.opacity(0.2))
                            .cornerRadius(8)
                        Button("Play Now", action: onPlay)
                            .font(.subheadline.bold())
                            .foregroundColor(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.orange)
                            .cornerRadius(8)
                    }
                }
                .padding(16)
                .background(Color.black.opacity(0.8))
                .cornerRadius(12)
                .padding(.trailing, 24)
                .padding(.bottom, 80)
            }
        }
    }
}

// MARK: - Helper Buttons

struct ControlButton: View {
    let systemName: String
    let size: CGFloat
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: size, weight: .medium))
                .foregroundColor(.white)
                .padding(12)
        }
    }
}

struct AirPlayButton: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let view = AVRoutePickerView()
        view.tintColor = .white
        view.activeTintColor = .orange
        return view
    }
    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}

struct AspectRatioButton: View {
    @ObservedObject var playerVM: PlayerViewModel

    private let gravities: [AVLayerVideoGravity] = [.resizeAspect, .resizeAspectFill, .resize]
    private let icons: [String] = [
        "aspectratio",
        "arrow.up.left.and.arrow.down.right",
        "arrow.left.and.right"
    ]
    private let tooltips = ["Fit", "Fill", "Stretch"]

    var body: some View {
        let currentIndex = gravities.firstIndex(of: playerVM.videoGravity) ?? 0
        Button {
            let nextIndex = (currentIndex + 1) % gravities.count
            playerVM.setAspectRatio(gravities[nextIndex])
        } label: {
            Image(systemName: icons[currentIndex])
                .font(.title3)
                .foregroundColor(.white)
        }
        .padding(8)
    }
}

struct PiPButton: View {
    let player: AVPlayer

    var body: some View {
        // PiP is handled at the UIKit layer; show icon as visual cue
        Button {
            NotificationCenter.default.post(name: .init("CinePlayer.TogglePiP"), object: nil)
        } label: {
            Image(systemName: "pip.enter")
                .font(.title3)
                .foregroundColor(.white)
        }
        .padding(8)
    }
}
