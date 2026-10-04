# Архитектурный аудит и ревью: ARCH-PIVOT-02

**Дата:** 2 октября 2026 г.  
**Роль:** Главный Архитектор  
**Статус:** APPROVED / READY FOR IMPLEMENTATION  

---

## 1. Введение и цели аудита

На основе полевого тестирования пользователем автономного Music & Lyrics Hub на реальном устройстве (Xiaomi Poco M7, Android 15) выявлены 4 критических архитектурных дефекта и функциональных разрыва:

1. **Баг счетчика прослушиваний (`playCount`):** за одно реальное воспроизведение трека счетчик накручивается в 2 или 3 раза.
2. **Отсутствие оффлайн/IDLE-просмотра на главном экране:** при отсутствии активного воспроизведения выбор трека в «Истории» или «Библиотеке» не открывает его данные (обложку, текст, караоке, аккорды, заметки) на экране `NowPlayingScreen`.
3. **Неработающие аккорды AmDm:** вкладка «Аккорды» в `NowPlayingScreen` всегда сообщает «Аккорды AmDm не найдены», даже для композиций с разметкой.
4. **Невозможность редактировать заметки:** вкладка «Заметки» в `NowPlayingScreen` содержит только статичный `Text` без поля ввода и автосохранения.

---

## 2. Глубокий Root Cause Analysis (RCA)

### 2.1 Дефект 1: Мультипликация `playCount` (x2 / x3)
- **Файлы:** `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt`, `app/src/main/java/com/eventengine/app/ingestion/MediaSessionCollector.kt`, `app/src/main/java/com/eventengine/app/ingestion/NotificationListener.kt`.
- **Механика сбоя:**
  1. **Рассогласование `trackKey` между источниками:** `computeTrackKey(title, artist, album)` включает поле `album`. В `NotificationListener` альбом чаще всего пустой (`""`), а в `MediaSessionCollector` извлекается из `MediaMetadata` (например, `"Starboy"`). В результате для одной песни создаются два разных `trackKey`.
  2. **Погрешность эвристики `isGapLarge`:** В `recordPlaybackSignal` условие `shouldIncrement = (existingTrack == null) || isNewTrack || isGapLarge`, где `SESSION_GAP_THRESHOLD_MS = 30_000L`. Если пользователь поставил песню на паузу на 35 секунд (ответить на звонок или сообщение) и нажал Play, `isGapLarge` становится `true`, и счетчик инкрементируется повторно в середине трека.
  3. **Гонка `UPDATE` и `PLAYING` в `MediaSessionCollector`:** В `lastTrackMap[packageName] = "$packageName|$title|$artist|$stateName"` суффикс `$stateName` приводит к тому, что событие смены метаданных (`UPDATE`) и событие старта (`PLAYING`) оба проходят через фильтр с интервалом в 50–100 мс, вызывая повторный вызов `recordPlaybackSignal` до фиксации первой сессии.
  4. **Дублирование от `NowPlayingScreen`:** В `NowPlayingScreen.kt` (строка 286) при создании `TrackEntity` из `livePlayback` значение `playCount` по умолчанию равно `1`, и если трек еще не успел закоммититься через фоновый сервис, интерфейс повторно инициирует запись.

### 2.2 Дефект 2: Отсутствие просмотра трека из Истории/Библиотеки в режиме IDLE
- **Файлы:** `app/src/main/java/com/eventengine/app/ui/AppScaffold.kt`, `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt`.
- **Механика сбоя:**
  1. В `AppScaffold.kt` коллбэк `onTrackSelected` в экранах `HistoryScreen` и `LibraryScreen` принимает `TrackEntity`, но тело лямбды выполняет только `currentTab = AppTab.NOW_PLAYING`. Выбранный трек **никуда не сохраняется** и теряется.
  2. В `NowPlayingScreen.kt` переменная `currentTrack` вычисляется строго:
     - Если `livePlayback != null` $\to$ берется `livePlayback`.
     - Иначе если `initialTrack != null` $\to$ берется `initialTrack` из внешнего Intent.
     - Иначе $\to$ всегда берется `recentTracks.firstOrNull()`.
  3. В результате, когда плеер не играет, клик по любому треку из 100 песен в Истории или Библиотеке переключает вкладку, но экран упрямо показывает только самый первый недавний трек.

### 2.3 Дефект 3: Поломка вкладки «Аккорды AmDm»
- **Файлы:** `app/src/main/java/com/eventengine/app/feature/LyricsProvider.kt`, `app/src/main/java/com/eventengine/app/feature/lyrics/AmDmChordParser.kt`, `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt`.
- **Механика сбоя:**
  1. `LyricsResult` в `LyricsProvider.kt` содержит только поля `plainLyrics` и `syncedLyrics`. Поле для аккордов **отсутствует в контракте**.
  2. В `scrapeAmDm` в `LyricsProvider.kt` блок аккордов парсится, но из него регулярным выражением вырезаются все теги `<div class="podbor__chord">`, оставляя только голый текст песни.
  3. В `MusicFeatureEngine.kt` и `NowPlayingScreen.kt` делается вызов:
     `chordsAmDm = AmDmChordParser.parseAmDmHtml(plain)`.
     Но `plain` — это уже очищенный плоский текст из LrcLib или Genius! В нем нет HTML-тегов `<pre itemprop="chordsBlock">`, поэтому `parseAmDmHtml(plain)` **всегда возвращает `null`**.
  4. В `NowPlayingScreen.kt` отсутствует возможность транспонирования аккордов ($\pm 1$ полутон) и форматирования сетки.

### 2.4 Дефект 4: Отсутствие редактирования заметок к песне
- **Файлы:** `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt`, `app/src/main/java/com/eventengine/app/storage/MusicDao.kt`.
- **Механика сбоя:**
  1. В `NowPlayingScreen.kt` (строки 878–905) вкладка `NowPlayingMode.NOTES` отрисовывает статический компонент `Text(text = if (notes.isNotBlank()) notes else "Заметок пока нет...")`.
  2. Нет ни `TextField`, ни кнопки редактирования, ни диалога, ни вызова `db.musicDao().updateUserNotes(...)`.
  3. Пользователь физически не может ввести строй гитары, каподастр, персональные мысли или любимые цитаты.

---

## 3. Архитектурная матрица решений

| Дефект | Зона | Архитектурное решение | Затрагиваемые компоненты |
|---|---|---|---|
| **TASK-BUG-05** (x2/x3 playCount) | `media-ingress` / `media-core` | 1. Нормализация `trackKey` без жесткой зависимости от пустого альбома.<br>2. Кулдаун инкремента `playCount` (минимум 60с для одного и того же трека).<br>3. Инкремент только при реальном старте нового трека или повторе после прослушивания > 60% длительности, а не при 30с паузе.<br>4. Дедупликация сигналов Ingress по `packageName\|title\|artist` без суффикса состояния. | `MusicFeatureEngine.kt`, `MediaSessionCollector.kt`, `NotificationListener.kt` |
| **TASK-UI-04** (IDLE просмотр трека) | `media-ui` | 1. Добавление `viewingTrack: TrackEntity?` в состояние навигации `AppScaffold.kt`.<br>2. Передача `selectedTrack` в `NowPlayingScreen(viewingTrack)`.<br>3. Поддержка режима IDLE Inspection: если плеер остановлен, экран отображает выбранный трек, его обложку, сохраненные тексты, аккорды и заметки. | `AppScaffold.kt`, `NowPlayingScreen.kt`, `HistoryScreen.kt`, `LibraryScreen.kt` |
| **TASK-BUG-06** (Аккорды AmDm) | `lyrics-engine` / `media-ui` | 1. Расширение модели `LyricsResult(val chords: String? = null)`.<br>2. В `LyricsProvider.kt` сохранение форматированного текста аккордов AmDm.<br>3. Поддержка инлайн-аккордов `[Am]`, `[C]` и парсинга аккордовых строк.<br>4. Интерактивный UI в NowPlaying: транспонирование ($\pm 1$ полутон), моноширинный рендеринг аккордов с подсветкой `CyberCyan`. | `LyricsProvider.kt`, `AmDmChordParser.kt`, `MusicEntities.kt`, `NowPlayingScreen.kt` |
| **TASK-BUG-07** (Редактор заметок) | `media-ui` / `media-core` | 1. Интерактивный редактор заметок в `NowPlayingScreen.kt` (режим просмотра / режим редактирования с `OutlinedTextField`).<br>2. Debounced автосохранение через корутину в `db.musicDao().updateUserNotes(trackKey, notes)`.<br>3. Мгновенная синхронизация локального состояния `TrackEntity`. | `NowPlayingScreen.kt`, `MusicDao.kt`, `MusicFeatureEngine.kt` |

---

## 4. Спецификации задач (SDD Phase 4)

Сформированы 4 карты задач в директории `.sdd/tasks/`:
1. [`.sdd/tasks/TASK-BUG-05.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/tasks/TASK-BUG-05.md)
2. [`.sdd/tasks/TASK-UI-04.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/tasks/TASK-UI-04.md)
3. [`.sdd/tasks/TASK-BUG-06.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/tasks/TASK-BUG-06.md)
4. [`.sdd/tasks/TASK-BUG-07.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/tasks/TASK-BUG-07.md)
