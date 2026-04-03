import SwiftUI

@main
struct CinePlayerApp: App {
    @StateObject private var mediaLibrary = MediaLibrary.shared
    @StateObject private var playbackManager = PlaybackStateManager.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(mediaLibrary)
                .environmentObject(playbackManager)
                .preferredColorScheme(.dark)
        }
    }
}
