# Межзонный контракт: core__ui

- **Зоны:** `media-core` / `lyrics` / `wrapped` ──> `media-ui` (AMOLED интерфейс)
- **Версия:** FROZEN v1
- **Дата фиксации:** 2026-10-01
- **Статус:** FROZEN
- **Нормативная база:** [architecture.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture.md), ADR-002, ADR-003

---

## 1. Назначение контракта

Контракт специфицирует неизменяемые модели состояния (UI State) и функции пользовательских намерений (User Intents/Actions) для 4 главных AMOLED экранов приложения.

---

## 2. Модели UI State для 4 табов

### 2.1 Таб 1: Состояние экрана «Сейчас играет» (`NowPlayingUiState`)
```kotlin
data class NowPlayingUiState(
    val activeTrack: TrackItemUiModel?,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lyricsState: LyricsDisplayState = LyricsDisplayState.Loading,
    val displayMode: LyricsViewMode = LyricsViewMode.SYNCED_KARAOKE,
    val isFavorite: Boolean = false,
    val isWifiConnected: Boolean = true
)

enum class LyricsViewMode {
    SYNCED_KARAOKE, // Построчный скролл с таймкодами LRC
    PLAIN_TEXT,     // Простой текст песни
    CHORDS_AMDM,    // Аккорды и табулатуры
    USER_NOTES      // Личные заметки к песне
}

sealed interface LyricsDisplayState {
    data object Loading : LyricsDisplayState
    data class Success(
        val plainText: String?,
        val syncedLines: List<KaraokeLineUiModel>,
        val chordsText: String?,
        val source: String,
        val syncOffsetMs: Long
    ) : LyricsDisplayState
    data class NotFound(val reason: String) : LyricsDisplayState
}

data class KaraokeLineUiModel(
    val timestampMs: Long,
    val text: String,
    val isActive: Boolean = false
)
```

### 2.2 Таб 2: Состояние экрана «История» (`HistoryUiState`)
```kotlin
data class HistoryUiState(
    val groupedSessions: Map<String, List<HistorySessionItemUiModel>> = emptyMap(), // "Сегодня", "Вчера", "Ранее"
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

data class HistorySessionItemUiModel(
    val sessionId: Long,
    val trackKey: String,
    val title: String,
    val artist: String,
    val sourcePackage: String,
    val sourceAppName: String,
    val startTimeFormatted: String,
    val durationFormatted: String,
    val playCount: Int,
    val isFavorite: Boolean
)
```

### 2.3 Таб 3: Состояние экрана «Библиотека» (`LibraryUiState`)
```kotlin
data class LibraryUiState(
    val tracks: List<LibraryTrackUiModel> = emptyList(),
    val activeFilter: LibraryFilter = LibraryFilter.ALL_OFFLINE,
    val searchQuery: String = "",
    val selectedArtist: String? = null
)

enum class LibraryFilter {
    ALL_OFFLINE, // Все треки с сохраненным кэшем текстов
    FAVORITES,   // Только избранные
    WITH_CHORDS  // Только треки с аккордами AmDm
}

data class LibraryTrackUiModel(
    val trackKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val hasSyncedLyrics: Boolean,
    val hasChords: Boolean,
    val playCount: Int,
    val totalTimeFormatted: String,
    val isFavorite: Boolean
)
```

### 2.4 Таб 4: Состояние экрана «Итоги & Ачивки» (`WrappedUiState`)
```kotlin
data class WrappedUiState(
    val activeTimeRange: WrappedTimeRange = WrappedTimeRange.CURRENT_YEAR,
    val stats: WrappedStats? = null,
    val achievements: List<AchievementUiModel> = emptyList(),
    val unlockedCount: Int = 0,
    val isExportingCard: Boolean = false,
    val exportedCardUri: String? = null // Uri файла PNG для системной шторки
)

data class AchievementUiModel(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean,
    val currentProgress: Int,
    val maxProgress: Int,
    val progressPercentage: Float // 0.0 .. 1.0
)
```

---

## 3. Пользовательские действия (UI Actions / Intents)

```kotlin
interface MediaUiActions {
    // Таб 1: Плеер и тексты
    fun togglePlayPause()
    fun toggleFavorite(trackKey: String)
    fun setLyricsViewMode(mode: LyricsViewMode)
    fun adjustSyncOffset(deltaMs: Long)
    fun saveUserNote(trackKey: String, note: String)
    fun retryFetchLyricsManually(trackKey: String)

    // Таб 2: История
    fun onHistorySearchChanged(query: String)
    fun clearHistory()

    // Таб 3: Библиотека
    fun setLibraryFilter(filter: LibraryFilter)
    fun onLibrarySearchChanged(query: String)

    // Таб 4: Wrapped и ачивки
    fun setWrappedTimeRange(range: WrappedTimeRange)
    fun generateAndShareCard()
}
```

---

## 4. Гарантии отображения

1. **AMOLED Pure Black Standard:** Цвет фона подложки всех 4 экранов строго `#000000`, отсутствие серых подложек на всю площадь для экономии батареи на OLED экранах.
2. **120 Гц плавный скроллинг караоке:** Автоскролл строки караоке при смене `playbackPositionMs` выполняется через `animateScrollToItem` с длительностью анимации не более 250 мс.
3. **Офлайн-готовность:** Экраны Истории, Библиотеки и Итогов никогда не показывают системную ошибку сетевого соединения при отсутствии интернета.
