# Задача TASK-UI-04: Выбор и просмотр трека из Истории и Библиотеки в режиме IDLE на главном экране

- **ID задачи:** `TASK-UI-04`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/ui/AppScaffold.kt` (сохранение выбранного трека в `viewingTrack` и передача в `NowPlayingScreen`)
  2. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (поддержка `selectedTrack`, приоритеты отображения, режим инспекции)
  3. `app/src/main/java/com/eventengine/app/ui/HistoryScreen.kt` (вызов `onTrackSelected(track)`)
  4. `app/src/main/java/com/eventengine/app/ui/LibraryScreen.kt` (вызов `onTrackSelected(track)`)
- **Спека:** [.sdd/specs/media-ui/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/overview.md) (v1)
- **Приоритет:** HIGH (Требование пользователя: просмотр текста, аккордов и обложки сохраненного трека при неактивном плеере)

---

## 1. Контекст и UX-сценарий

Когда внешний плеер (Яндекс Музыка, Spotify и др.) остановлен или не запущен, пользователь хочет открыть ранее сохраненный трек из «Истории» или «Библиотеки» на главном экране плеера (`NowPlayingScreen`), чтобы:
- Посмотреть обложку альбома в центре винила;
- Прочитать или прокрутить синхронизированный текст караоке;
- Разучить аккорды AmDm на гитаре;
- Записать или отредактировать личные заметки к треку.

В текущей версии клик в Истории/Библиотеке переключал таб на `NowPlaying`, но сам трек не передавался, и экран всегда показывал только `recentTracks.firstOrNull()`.

---

## 2. Спецификация изменений

### 2.1 Навигационный контекст в `AppScaffold.kt`

Добавить состояние `viewingTrack`:
```kotlin
var viewingTrack by remember { mutableStateOf<TrackEntity?>(null) }
```
При выборе трека в списках сохранять его:
```kotlin
AppTab.NOW_PLAYING -> NowPlayingScreen(
    initialTrack = initialLyricsTrack,
    selectedTrack = viewingTrack,
    onTrackConsumed = {
        onLyricsDialogConsumed()
        viewingTrack = null
    }
)
AppTab.HISTORY -> HistoryScreen(
    onTrackSelected = { track ->
        viewingTrack = track
        currentTab = AppTab.NOW_PLAYING
    }
)
AppTab.LIBRARY -> LibraryScreen(
    onTrackSelected = { track ->
        viewingTrack = track
        currentTab = AppTab.NOW_PLAYING
    }
)
```

### 2.2 Логика определения `currentTrack` в `NowPlayingScreen.kt`

Обновить сигнатуру `NowPlayingScreen`:
```kotlin
@Composable
fun NowPlayingScreen(
    initialTrack: ParsedTrackInfo? = null,
    selectedTrack: TrackEntity? = null,
    onTrackConsumed: () -> Unit = {}
)
```

Иерархия приоритетов:
1. **Активное воспроизведение в эфире:** Если `livePlayback != null && livePlayback.isPlaying` $\to$ отображать активный эфирный трек из `livePlayback`.
2. **Явный выбор пользователя (Режим IDLE):** Если плеер на паузе/остановлен и `selectedTrack != null` $\to$ отображать выбранный `selectedTrack`.
3. **Последний эфирный трек:** Если `livePlayback != null && livePlayback.title.isNotBlank()` $\to$ отображать снимок `livePlayback`.
4. **Внешний Intent:** Если передан `initialTrack` $\to$ отображать его.
5. **Фоллбек:** `recentTracks.firstOrNull()`.

```kotlin
val isLiveActive = livePlayback != null && livePlayback!!.isPlaying

val currentTrack: TrackEntity? = remember(recentTracks, initialTrack, livePlayback, selectedTrack, isLiveActive) {
    if (isLiveActive) {
        val cleanArtist = livePlayback!!.artist.ifBlank { "Unknown Artist" }
        val cleanAlbum = livePlayback!!.album
        val trackKey = MusicFeatureEngine.computeTrackKey(livePlayback!!.title, cleanArtist, cleanAlbum)
        TrackEntity(
            trackKey = trackKey,
            title = livePlayback!!.title,
            artist = cleanArtist,
            album = cleanAlbum,
            sourcePackage = livePlayback!!.packageName,
            albumArtUri = livePlayback!!.albumArtUri,
            syncedLyrics = recentTracks.find { it.trackKey == trackKey }?.syncedLyrics,
            plainLyrics = recentTracks.find { it.trackKey == trackKey }?.plainLyrics,
            userNotes = recentTracks.find { it.trackKey == trackKey }?.userNotes.orEmpty()
        )
    } else if (selectedTrack != null) {
        selectedTrack
    } else if (livePlayback != null && livePlayback!!.title.isNotBlank()) {
        val cleanArtist = livePlayback!!.artist.ifBlank { "Unknown Artist" }
        val cleanAlbum = livePlayback!!.album
        val trackKey = MusicFeatureEngine.computeTrackKey(livePlayback!!.title, cleanArtist, cleanAlbum)
        recentTracks.find { it.trackKey == trackKey } ?: TrackEntity(
            trackKey = trackKey,
            title = livePlayback!!.title,
            artist = cleanArtist,
            album = cleanAlbum,
            sourcePackage = livePlayback!!.packageName,
            albumArtUri = livePlayback!!.albumArtUri
        )
    } else if (initialTrack != null && initialTrack.title.isNotBlank()) {
        val cleanArtist = initialTrack.artist.ifBlank { "Unknown Artist" }
        val cleanAlbum = initialTrack.album ?: ""
        val trackKey = MusicFeatureEngine.computeTrackKey(initialTrack.title, cleanArtist, cleanAlbum)
        recentTracks.find { it.trackKey == trackKey } ?: TrackEntity(
            trackKey = trackKey,
            title = initialTrack.title,
            artist = cleanArtist,
            album = cleanAlbum,
            sourcePackage = "com.spotify.music"
        )
    } else {
        recentTracks.firstOrNull()
    }
}
```

### 2.3 Поведение в режиме оффлайн-просмотра
1. Винил отображает обложку выбранного трека, шпиндель и канавки, но **не вращается** (так как воспроизведение не активно).
2. Загрузка текстов и аккордов из `lyrics_cache` автоматически срабатывает по ключу `currentTrack.trackKey`.
3. Вкладки «Караоке», «Текст», «Аккорды» и «Заметки» полностью интерактивны в режиме чтения и редактирования.

---

## 3. Критерии приемки (DoD)

1. Клик по любому треку из списка «История» открывает экран «Плеер» именно с этим треком (название, артист, обложка).
2. Клик по любому треку из вкладки «Библиотека» (включая фильтр «С текстом») открывает экран «Плеер» с текстом и аккордами выбранного трека.
3. При старте воспроизведения в плеере экран мгновенно переключается на актуальный играющий трек.
4. Сборка проекта и тесты выполняются без ошибок.
