import SwiftUI

// MARK: - SettingsView
struct SettingsView: View {
    @StateObject private var settings = AppSettings.shared
    @State private var showTMDBHelp = false
    @State private var showSubtitleHelp = false

    var body: some View {
        NavigationView {
            List {
                // MARK: API Keys
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("TMDB API Key")
                                .font(.subheadline)
                            Spacer()
                            Button {
                                showTMDBHelp = true
                            } label: {
                                Image(systemName: "questionmark.circle")
                                    .foregroundColor(.orange)
                            }
                        }
                        SecureField("Paste your TMDB API key here", text: $settings.tmdbAPIKey)
                            .font(.system(.caption, design: .monospaced))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("OpenSubtitles API Key")
                                .font(.subheadline)
                            Spacer()
                            Button {
                                showSubtitleHelp = true
                            } label: {
                                Image(systemName: "questionmark.circle")
                                    .foregroundColor(.orange)
                            }
                        }
                        SecureField("Paste your OpenSubtitles API key here", text: $settings.openSubtitlesAPIKey)
                            .font(.system(.caption, design: .monospaced))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }

                    TextField("OpenSubtitles Username (optional)", text: $settings.openSubtitlesUsername)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    SecureField("OpenSubtitles Password (optional)", text: $settings.openSubtitlesPassword)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("API Configuration")
                } footer: {
                    Text("Free API keys required for metadata and subtitle features. See links in help (?)")
                        .font(.caption)
                }

                // MARK: Subtitle Settings
                Section("Subtitles") {
                    Picker("Language", selection: $settings.preferredSubtitleLanguage) {
                        ForEach(SubtitleLanguage.all, id: \.code) { lang in
                            Text(lang.name).tag(lang.code)
                        }
                    }

                    Toggle("Auto-fetch Subtitles", isOn: $settings.autoFetchSubtitles)
                        .tint(.orange)

                    HStack {
                        Text("Font Size")
                        Slider(value: $settings.subtitleFontSize, in: 12...32, step: 1)
                        Text("\(Int(settings.subtitleFontSize))pt")
                            .frame(width: 40)
                    }

                    Picker("Text Color", selection: $settings.subtitleTextColor) {
                        Text("White").tag("white")
                        Text("Yellow").tag("yellow")
                        Text("Gray").tag("gray")
                    }
                    .pickerStyle(.segmented)
                }

                // MARK: Playback Settings
                Section("Playback") {
                    Toggle("Resume Playback", isOn: $settings.resumePlayback)
                        .tint(.orange)

                    Toggle("Auto-play Next Episode", isOn: $settings.autoPlayNextEpisode)
                        .tint(.orange)

                    Toggle("Auto-fetch Metadata", isOn: $settings.autoFetchMetadata)
                        .tint(.orange)

                    HStack {
                        Text("Skip Forward")
                        Spacer()
                        Picker("", selection: $settings.skipForwardSeconds) {
                            ForEach([5, 10, 15, 30, 45, 60], id: \.self) { sec in
                                Text("\(sec)s").tag(sec)
                            }
                        }
                        .pickerStyle(.menu)
                        .tint(.orange)
                    }

                    HStack {
                        Text("Skip Backward")
                        Spacer()
                        Picker("", selection: $settings.skipBackwardSeconds) {
                            ForEach([5, 10, 15, 30, 45], id: \.self) { sec in
                                Text("\(sec)s").tag(sec)
                            }
                        }
                        .pickerStyle(.menu)
                        .tint(.orange)
                    }

                    HStack {
                        Text("Next Episode Countdown")
                        Spacer()
                        Picker("", selection: $settings.countdownToNextEpisode) {
                            ForEach([3, 5, 7, 10, 15], id: \.self) { sec in
                                Text("\(sec)s").tag(sec)
                            }
                        }
                        .pickerStyle(.menu)
                        .tint(.orange)
                    }
                }

                // MARK: About
                Section("About") {
                    LabeledContent("Version", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                    Link("TMDB (metadata source)", destination: URL(string: "https://www.themoviedb.org")!)
                    Link("OpenSubtitles (subtitle source)", destination: URL(string: "https://www.opensubtitles.com")!)
                    LabeledContent("License", value: "Personal Use")
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.large)
            .preferredColorScheme(.dark)
            .sheet(isPresented: $showTMDBHelp) {
                TMDBHelpView()
            }
            .sheet(isPresented: $showSubtitleHelp) {
                OpenSubtitlesHelpView()
            }
        }
        .navigationViewStyle(.stack)
    }
}

// MARK: - TMDBHelpView
struct TMDBHelpView: View {
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("TMDB is The Movie Database — a free, community-maintained database of movies and TV shows.")
                        .font(.body)

                    Text("To get a free API key:").font(.headline)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("1. Visit themoviedb.org and create a free account.")
                        Text("2. Go to Settings → API in your account.")
                        Text("3. Request a Developer API key (free).")
                        Text("4. Copy the 'API Key (v3 auth)' value.")
                        Text("5. Paste it in CinePlayer Settings.")
                    }
                    .font(.body)
                    .foregroundColor(.secondary)

                    Link("Open TMDB Website",
                         destination: URL(string: "https://www.themoviedb.org/settings/api")!)
                        .font(.headline)
                        .foregroundColor(.orange)
                }
                .padding()
            }
            .navigationTitle("TMDB API Key")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - OpenSubtitlesHelpView
struct OpenSubtitlesHelpView: View {
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("OpenSubtitles is the largest free subtitle database. The REST API allows subtitle search and download.")
                        .font(.body)

                    Text("To get a free API key:").font(.headline)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("1. Visit opensubtitles.com and create a free account.")
                        Text("2. Go to opensubtitles.com/en/consumers and create an API consumer.")
                        Text("3. Copy your API key.")
                        Text("4. Paste it in CinePlayer Settings.")
                        Text("5. Optionally enter your username and password for higher download limits.")
                    }
                    .font(.body)
                    .foregroundColor(.secondary)

                    Text("Free accounts get 5 subtitle downloads per day. Registered users get more.")
                        .font(.callout)
                        .foregroundColor(.orange)

                    Link("Open OpenSubtitles Website",
                         destination: URL(string: "https://www.opensubtitles.com/en/consumers")!)
                        .font(.headline)
                        .foregroundColor(.orange)
                }
                .padding()
            }
            .navigationTitle("OpenSubtitles API Key")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - SubtitleLanguage
struct SubtitleLanguage {
    let code: String
    let name: String

    static let all: [SubtitleLanguage] = [
        SubtitleLanguage(code: "en", name: "English"),
        SubtitleLanguage(code: "es", name: "Spanish"),
        SubtitleLanguage(code: "fr", name: "French"),
        SubtitleLanguage(code: "de", name: "German"),
        SubtitleLanguage(code: "it", name: "Italian"),
        SubtitleLanguage(code: "pt", name: "Portuguese"),
        SubtitleLanguage(code: "ru", name: "Russian"),
        SubtitleLanguage(code: "zh", name: "Chinese"),
        SubtitleLanguage(code: "ja", name: "Japanese"),
        SubtitleLanguage(code: "ko", name: "Korean"),
        SubtitleLanguage(code: "ar", name: "Arabic"),
        SubtitleLanguage(code: "nl", name: "Dutch"),
        SubtitleLanguage(code: "pl", name: "Polish"),
        SubtitleLanguage(code: "tr", name: "Turkish"),
        SubtitleLanguage(code: "sv", name: "Swedish"),
        SubtitleLanguage(code: "da", name: "Danish"),
        SubtitleLanguage(code: "fi", name: "Finnish"),
        SubtitleLanguage(code: "nb", name: "Norwegian"),
        SubtitleLanguage(code: "he", name: "Hebrew"),
        SubtitleLanguage(code: "hi", name: "Hindi"),
    ]
}
