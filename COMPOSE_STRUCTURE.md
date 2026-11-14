# Mustre Jetpack Compose Architecture & File Structure

This document is your reference blueprint for porting **Mustre** to **Jetpack Compose**.

---

## 1. Architectural Blueprint (Unidirectional Data Flow)

The application follows Modern Android Architecture using **Unidirectional Data Flow (UDF)** with **StateFlow** and Compose:

```
┌────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                   │
│   (PlayerScreen, PlaylistSheet, SearchOverlay, etc.)   │
└───────────────────────────▲────────────────────────────┘
                            │ StateFlow (UI State)
                            │ Events / Intents (User Actions)
┌───────────────────────────▼────────────────────────────┐
│                       ViewModels                       │
│    (PlayerViewModel, SearchViewModel, SettingsViewModel)│
└───────────────────────────▲────────────────────────────┘
                            │ Kotlin Coroutines / StateFlows
┌───────────────────────────▼────────────────────────────┐
│               Service Bridge / Repositories            │
│  (MusicServiceConnection, SongRepository, ServerPrefs) │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                     Data & Engine                      │
│   (MusicService / ExoPlayer, HttpHandler, Cache)       │
└────────────────────────────────────────────────────────┘
```

### Architectural Principles
1. **Incremental Interoperability**: `MusicService.java`, `HttpHandler.java`, `AudioCacheManager.java`, and `ServerPrefs.java` do not need a rewrite on day one. They bridge cleanly into Kotlin and Compose.
2. **Service Connection as a Bridge (`MusicServiceConnection`)**: Centralizes `MusicService` binding and converts listener callbacks and player position polling into Kotlin `StateFlow`s.
3. **Decoupled ViewModels**: ViewModels consume repository/service flows and emit a single immutable `UiState` data class for each screen.
4. **Declarative Compose UI**: Replaces all XML layouts (`activity_main.xml`, `layout-land`, `activity_settings.xml`, bottom sheets, and dialogs) with idiomatic, stateless Composable functions.

---

## 2. Recommended Directory & File Structure

```
app/src/main/java/com/aadith/mustre/
├── MustreApplication.kt                     # Application class (initializes cache, coil, notifications)
├── MainActivity.kt                          # Single Activity entry point: setContent { MustreApp() }
│
├── core/                                    # Shared foundations, bridges, and design system
│   ├── connection/
│   │   └── MusicServiceConnection.kt        # Bridges MusicService into StateFlows
│   ├── designsystem/
│   │   ├── Theme.kt                         # Material3 ColorScheme, Shapes, Typography
│   │   ├── Color.kt                         # Mustre palette (neon teal accents, dark scrims)
│   │   ├── Type.kt                          # App typography styles
│   │   └── Components.kt                    # GlassmorphicCard, HeaderVisualizer, MarqueeText
│   └── util/
│       ├── TimeFormatter.kt                 # Millisecond to mm:ss formatter
│       └── NetworkUtils.kt                  # Local network / IP discovery helpers
│
├── feature/                                 # Feature-first presentation modules
│   ├── player/                              # Now Playing Screen
│   │   ├── PlayerScreen.kt                  # Adaptive host (chooses Portrait or Landscape)
│   │   ├── PlayerPortrait.kt                # Portrait layout
│   │   ├── PlayerLandscape.kt               # Landscape dual-pane layout
│   │   ├── PlayerViewModel.kt               # Coordinates playback state & seek actions
│   │   ├── PlayerUiState.kt                 # Immutable state for Player UI
│   │   └── components/
│   │       ├── PlayerControls.kt            # Play/Pause, Next, Prev, Repeat buttons
│   │       ├── PlayerSeekBar.kt             # Progress slider + buffer & loading shimmer
│   │       ├── AlbumCoverCard.kt            # Album artwork card with elevation & shadow
│   │       └── AudioQualityBadge.kt         # Lossless / Hi-Res / Bitrate chip
│   │
│   ├── playlist/                            # Song Catalog & Queue
│   │   ├── PlaylistSheet.kt                 # Slide-up panel / bottom sheet (Songs & Queue)
│   │   ├── PlaylistViewModel.kt             # Manages catalog, queue reordering, deduplication
│   │   ├── PlaylistUiState.kt               # Catalog & queue state
│   │   └── components/
│   │       ├── SongItem.kt                  # Song list row (with active indicator)
│   │       └── QueueItem.kt                 # Draggable queue row with swipe-to-remove
│   │
│   ├── search/                              # Multi-source Search (Local, Telegram, YouTube)
│   │   ├── SearchOverlay.kt                 # Expanding glassmorphic search overlay
│   │   ├── SearchViewModel.kt               # Debounced search & download status coordination
│   │   ├── SearchUiState.kt                 # Search query, results, pending source states
│   │   └── components/
│   │       └── SearchResultItem.kt          # Search item with source badge & download button
│   │
│   ├── songinfo/                            # Detailed Song Metadata Sheet
│   │   └── SongInfoSheet.kt                 # ModalBottomSheet (codec, bitrate, sample rate, path)
│   │
│   └── settings/                            # Server & App Settings
│       ├── SettingsScreen.kt                # Settings screen with Material 3 components
│       ├── SettingsViewModel.kt             # Server testing & preference toggles
│       ├── SettingsUiState.kt               # Server list, connection test state, switches
│       └── components/
│           └── ServerUrlCard.kt             # Saved server card with latency badge & delete action
│
├── navigation/
│   ├── NavGraph.kt                          # Navigation Compose routes (Player ↔ Settings)
│   └── Screen.kt                            # Sealed class: Screen.Player, Screen.Settings
│
├── model/                                   # Domain & Network Models (Kotlin data classes)
│   ├── Song.kt                              # Audio track model (stable, Compose-ready)
│   ├── SearchResult.kt                      # Multi-source search item
│   ├── SongsResponse.kt                     # Server catalog response wrapper
│   ├── SearchResponse.kt                    # Search query response wrapper
│   └── DownloadResponse.kt                  # Song download response wrapper
│
├── service/                                 # Foreground Services
│   ├── MusicService.java (or .kt)           # ExoPlayer media playback service
│   └── FloatingPlayerService.kt             # WindowManager overlay player using ComposeView
│
└── network/                                 # Network & Cache Layer (reusable from Java)
    ├── HttpHandler.java
    ├── AudioCacheManager.java
    ├── MediaCacheManager.java
    ├── ServerPrefs.java
    └── SongDownloader.java
```

---

## 3. UI State Definitions

### PlayerUiState
```kotlin
data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val audioQuality: String = "",
    val repeatMode: Int = 0,
    val isDownloading: Boolean = false,
    val showSongInfoDialog: Boolean = false
)
```

### PlaylistUiState
```kotlin
data class PlaylistUiState(
    val songs: List<Song> = emptyList(),
    val queue: List<Song> = emptyList(),
    val isQueueActiveTab: Boolean = false,
    val isPanelOpen: Boolean = false,
    val currentPlayingSongId: String? = null
)
```

### SearchUiState
```kotlin
data class SearchUiState(
    val query: String = "",
    val isSearchOpen: Boolean = false,
    val isLoading: Boolean = false,
    val results: List<SearchResult> = emptyList()
)
```

### SettingsUiState
```kotlin
data class SettingsUiState(
    val currentServerUrl: String = "",
    val savedServers: List<String> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Idle,
    val isOfflineMode: Boolean = false,
    val isContrastProtectionEnabled: Boolean = true,
    val isPopupPlayerEnabled: Boolean = false
)
```
