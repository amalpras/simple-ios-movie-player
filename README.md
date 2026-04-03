# CinePlayer — iOS Movie & TV Series Player

A full-featured iOS video player app specialized for movies and TV series. Inspired by the best features of Infuse and similar apps.

---

## Features

### 📚 Smart Library
- **Auto-organize** — TV series episodes are automatically detected from filenames and grouped into series folders with cover artwork
- **Grid & List views** — Switch between poster grid and compact list with a tap
- **Continue Watching** — Resume any in-progress movie or episode right from the home screen
- **Recently Added** — Quick access to newly imported files
- Sort by: Recently Added, Title, or Recently Watched

### 📺 TV Series Support
- Episodes automatically grouped by series name (parsed from filename)
- Per-season episode lists with episode titles and descriptions
- "Play Next" button always queued to the first unwatched episode
- Auto-play next episode countdown with cancel option
- Series cover art, ratings, and descriptions fetched from TMDB

### 🎬 Video Player Controls
- Play / Pause
- Seek bar with time display (current + remaining)
- Skip forward / backward (configurable: 5s, 10s, 15s, 30s, 60s)
- Playback speed: 0.5×, 0.75×, 1×, 1.25×, 1.5×, 1.75×, 2×
- Aspect ratio cycling (fit, fill, stretch)
- AirPlay / Apple TV support (built-in route picker)
- Picture-in-Picture support
- Lock screen / Control Center controls (MPNowPlayingInfo)
- Remote control (headphone buttons, Apple Watch)
- Auto-hide controls with tap to show

### 💬 Subtitle Support
- **Auto subtitle detection** — Parses filename to find the best subtitle automatically
- **Hash-based search** (most accurate) — uses OpenSubtitles file hash for exact match
- **Title+season+episode search** fallback
- Supports: `.srt`, `.ass`/`.ssa`, `.vtt`, `.sub`
- Detects local subtitle files in the same folder as the video
- Customizable: font size, text color (white/yellow/gray), background opacity
- Subtitle sync adjustment (±5 seconds in 0.5s steps)
- Multi-language support — 20+ languages in settings

### 🎭 Metadata & Cover Art
- Fetches posters, backdrops, descriptions, ratings, and genres from **TMDB** (free)
- Auto-fetches on import (can be disabled in Settings)
- Manual refresh per movie or series
- Images cached locally

### ⚙️ Settings
- TMDB API key configuration
- OpenSubtitles API key + optional account credentials
- Default playback speed
- Skip forward/backward durations
- Next-episode countdown timer
- Resume playback toggle
- Auto-fetch metadata/subtitles toggles
- Preferred subtitle language

---

## Data Sources (Free, No Copyright Issues)

| Feature | Source | License |
|---------|--------|---------|
| Movie/TV metadata, posters | [The Movie Database (TMDB)](https://www.themoviedb.org) | Free API, attribution required |
| Subtitles | [OpenSubtitles.com](https://www.opensubtitles.com) | Free API for personal use |

> **This product uses the TMDB API but is not endorsed or certified by TMDB.**

---

## Setup

### Requirements
- Xcode 15+
- iOS 16+ device or simulator
- macOS 13+ for building

### Steps

1. **Clone/open the project:**
   ```
   open CinePlayer/CinePlayer.xcodeproj
   ```

2. **Get a free TMDB API key:**
   - Create a free account at [themoviedb.org](https://www.themoviedb.org/settings/api)
   - Go to Settings → API → Request a Developer key
   - Copy the "API Key (v3 auth)" value

3. **Get a free OpenSubtitles API key:**
   - Create a free account at [opensubtitles.com](https://www.opensubtitles.com)
   - Go to [opensubtitles.com/en/consumers](https://www.opensubtitles.com/en/consumers)
   - Create a new API consumer and copy the key

4. **Enter API keys in the app:**
   - Launch CinePlayer → Settings tab
   - Paste TMDB API key
   - Paste OpenSubtitles API key
   - Optionally enter OpenSubtitles username/password for higher download limits

5. **Add videos:**
   - Tap **+** in the Library tab → select video files from Files app
   - Or share a video from Files/Photos to CinePlayer

---

## Filename Conventions (Auto-Detection)

CinePlayer parses filenames to extract metadata. These common naming patterns are supported:

| Pattern | Example |
|---------|---------|
| `S01E01` standard | `Breaking.Bad.S01E01.1080p.mkv` |
| `1x01` alternate | `Breaking.Bad.1x01.mkv` |
| Season/Episode words | `Breaking Bad Season 1 Episode 1.mp4` |
| Movie with year | `Inception.2010.BluRay.1080p.mkv` |
| Movie without year | `The.Dark.Knight.mkv` |

---

## Architecture

```
CinePlayer/
├── Models/
│   ├── MediaItem.swift        — Video file model (movie or episode)
│   ├── TVSeries.swift         — TV series grouping + TMDB response models
│   └── PlaybackState.swift    — Playback position persistence + AppSettings
├── Services/
│   ├── FilenameParser.swift   — Extract title/season/episode from filenames
│   ├── MetadataService.swift  — TMDB REST API client
│   ├── SubtitleService.swift  — OpenSubtitles REST API + hash algorithm
│   ├── MediaLibrary.swift     — Central library store + auto-organization
│   └── SubtitleParser.swift   — SRT/VTT/ASS subtitle file parser
└── Views/
    ├── LibraryView.swift      — Movies grid/list, continue watching, recently added
    ├── MovieCardView.swift    — Movie poster card
    ├── SeriesDetailView.swift — TV series view with season/episode list
    ├── PlayerView.swift       — Video player + all controls + subtitle overlay
    ├── DownloadsView.swift    — All files management view
    └── SettingsView.swift     — Settings + API key configuration
```

---

## Supported Video Formats

AVFoundation-native (hardware decoded):
- MP4, M4V, MOV (H.264, H.265/HEVC, ProRes)
- HLS streams

Software decoded (performance may vary):
- MKV (H.264, H.265)
- AVI
- WMV, TS, M2TS

> For best performance and broadest codec support, use MKV or MP4 containers with H.264 or H.265 video.

---

## Notes
- Free OpenSubtitles accounts are limited to **5 subtitle downloads per day**. Registered users get more.
- TMDB API is free with attribution (the app shows TMDB attribution in Settings).
- No AI is used. Subtitle search uses file hash and title matching.
- All metadata and subtitles are cached locally after first download.
