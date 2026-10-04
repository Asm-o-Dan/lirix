## Модуль: MediaCoreEngine   Зона: media-core   Версия спеки: v1   Статус: APPROVED

### Назначение
Модуль обеспечивает персистентность данных в базе данных Room (версия 6), управляет каталогом музыкальных треков, агрегирует сессии непрерывного прослушивания (схлопывание пауз $\le 30$ с), отслеживает счетчики воспроизведений и автоматическое добавление в «Избранное». Модуль НЕ выполняет сетевых запросов и НЕ отрисовывает UI.

---

### Типы данных
- `TrackEntity` — поля: `trackKey: String (PK)`, `title: String`, `artist: String`, `album: String`, `sourcePackage: String`, `playCount: Int`, `totalDurationMs: Long`, `firstPlayedAt: Long`, `lastPlayedAt: Long`, `userNotes: String`, `isFavorite: Boolean`.
- `ListeningSessionEntity` — поля: `id: Long (PK auto)`, `trackKey: String (FK)`, `sourcePackage: String`, `startTimeMs: Long`, `endTimeMs: Long`, `durationMs: Long`, `isCompleted: Boolean`.
- `LyricsCacheEntity` — поля: `trackKey: String (PK, FK)`, `plainLyrics: String?`, `syncedLyrics: String?`, `chords: String?`, `sourceProvider: String`, `cachedAtMs: Long`, `syncOffsetMs: Long`.
- `AchievementEntity` — поля: `id: String (PK)`, `title: String`, `description: String`, `category: String`, `iconName: String`, `isUnlocked: Boolean`, `unlockedAtMs: Long?`, `currentProgress: Int`, `maxProgress: Int`, `isSecret: Boolean`.

---

### Публичный API

#### 1. Функция `processMediaSignal(signal: MediaPlaybackSignal) -> TrackProcessingResult`
- **Сигнатура:** `suspend fun processMediaSignal(signal: MediaPlaybackSignal): TrackProcessingResult`
- **Предусловия:** `signal.title.isNotBlank() == true`, `signal.artist.isNotBlank() == true`.
- **Постусловия:** Обновляет или создает запись в `music_tracks`, актуализирует сессию в `music_listening_sessions`, пересчитывает `isFavorite`.
- **Поведение:**
  1. Вычисляет детерминированный SHA-256 ключ:
     `trackKey = computeTrackKey(signal.title, signal.artist, signal.album)`.
  2. Запрашивает из `MusicDao` последнюю сессию для пакета `signal.packageName`:
     `lastSession = musicDao.getLastSessionForPackage(signal.packageName)`.
  3. Проверяет смену трека и интервал паузы:
     - `isNewTrack = (lastSession == null || lastSession.trackKey != trackKey)`.
     - `isGapLarge = (lastSession != null && (signal.timestamp - lastSession.endTimeMs) > 30_000L)`.
  4. Если `!isNewTrack && !isGapLarge`:
     - Вычисляет приращение времени $\Delta = \max(0, signal.timestamp - lastSession.endTimeMs)$.
     - Обновляет текущую сессию: `endTimeMs = signal.timestamp`, `durationMs += \Delta`, `isCompleted = (signal.playbackState == PAUSED || STOPPED)`.
  5. Иначе (`isNewTrack || isGapLarge`):
     - Создает новую запись `ListeningSessionEntity(trackKey, signal.packageName, signal.timestamp, signal.timestamp, 0L, false)`.
     - Инкрементирует `playCount` трека на 1.
  6. Проверяет условие авто-избранного:
     `isFavorite = (existingTrack?.isFavorite == true) || (updatedPlayCount >= 5) || (updatedDurationMs >= 900_000L)`.
  7. Сохраняет обновленный `TrackEntity` через `insertOrUpdateTrack`.
  8. Возвращает `TrackProcessingResult`.
- **Ошибки:** при сбое SQLite выбрасывает `DatabaseException` с сохранением транзакционной целостности.
- **Побочные эффекты:** запись в таблицы `music_tracks` и `music_listening_sessions`.
- **Граничные случаи:**
  - Время устройства скакнуло назад $\to \Delta$ ограничивается нулем (`coerceAtLeast(0L)`).
  - Быстрые повторные сигналы с паузой $< 30$ с $\to$ единая непрерывная сессия, `playCount` не растет.
- **Примеры:**
  1. Вход: первый запуск трека `title="Smells Like Teen Spirit", artist="Nirvana"` $\to$ Выход: `isNewTrack=true, currentPlayCount=1, isFavorite=false`.
  2. Вход: сигнал через 10 секунд от того же плеера $\to$ Выход: `isNewTrack=false, currentPlayCount=1, durationMs=10000`.
  3. Вход: 5-е воспроизведение трека $\to$ Выход: `isFavorite=true` (автоматическая установка флага избранного).

#### 2. Функция `computeTrackKey(title: String, artist: String, album: String = "") -> String`
- **Сигнатура:** `fun computeTrackKey(title: String, artist: String, album: String = ""): String`
- **Предусловия:** `title.isNotBlank() == true`, `artist.isNotBlank() == true`.
- **Постусловия:** Возвращает 64-символьную hex-строку SHA-256 хэша.
- **Поведение:**
  1. Приводит все строки к нижнему регистру и обрезает пробелы:
     `normalized = "${title.trim().lowercase()}|${artist.trim().lowercase()}|${album.trim().lowercase()}"`.
  2. Вычисляет дайджест SHA-256 от UTF-8 байтов.
  3. Форматирует байты в шестнадцатеричную строку из 64 символов.
- **Ошибки:** исключений не бросает.
- **Побочные эффекты:** нет.
- **Граничные случаи:** Разный регистр и лишние пробелы по краям $\to$ дают строго одинаковый `trackKey`.
- **Примеры:**
  1. `computeTrackKey("Numb", "Linkin Park")` $\to$ `"3a1f87e5b..."`
  2. `computeTrackKey("  numb  ", "LINKIN PARK  ")` $\to$ `"3a1f87e5b..."` (идентично примеру 1).
  3. `computeTrackKey("Numb", "Linkin Park", "Meteora")` $\to$ отдельный уникальный хэш с учетом альбома.

#### 3. Функция `toggleFavorite(trackKey: String, isFavorite: Boolean)`
- **Сигнатура:** `suspend fun toggleFavorite(trackKey: String, isFavorite: Boolean): Unit`
- **Предусловия:** `trackKey.length == 64`.
- **Постусловия:** Значение поля `isFavorite` для записи с ключом `trackKey` обновлено в базе данных.
- **Поведение:** Вызывает `musicDao.setFavorite(trackKey, isFavorite)`.
- **Ошибки:** если трек с таким ключом не найден, операция завершается без ошибок (`UPDATE ... WHERE trackKey = ...` затрагивает 0 строк).
- **Побочные эффекты:** обновление записи в SQLite.
- **Граничные случаи:** `trackKey` отсутствует в БД $\to$ завершение без исключения.
- **Примеры:**
  1. Вход: `trackKey="3a1f...", isFavorite=true` $\to$ поле в БД становится `1`.
  2. Вход: `trackKey="3a1f...", isFavorite=false` $\to$ поле в БД становится `0`.
  3. Вход: несуществующий ключ $\to$ успешное завершение без падений.

---

### Зависимости
- Межзонные контракты:
  - [.sdd/contracts/ingress__core.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/ingress__core.md)
  - [.sdd/contracts/core__lyrics.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__lyrics.md)
  - [.sdd/contracts/core__wrapped.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__wrapped.md)
- Внешние библиотеки: `androidx.room:room-runtime:2.6.1`, `androidx.room:room-ktx:2.6.1`.

---

### Вне скоупа
- HTTP запросы и сетевая загрузка текстов (делегируется в `lyrics-engine`).
- Рендеринг карточек аналитики (делегируется в `wrapped-analytics`).
- Прослушивание системных интентов и нотификаций.
