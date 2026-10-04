## Модуль: LyricsEngine   Зона: lyrics-engine   Версия спеки: v1   Статус: APPROVED

### Назначение
Модуль обеспечивает поиск, кэширование и предоставление текстов песен, таймкодов караоке (LRC) и гитарных табулатур (AmDm). Модуль адаптирует сетевую активность под тип соединения (Wi-Fi vs Cellular) и сохраняет найденные данные в локальный кэш Room. Модуль НЕ воспроизводит аудио и НЕ управляет навигацией UI.

---

### Типы данных
- `LyricsDataResult` — поля: `trackKey: String`, `hasLyrics: Boolean`, `plainLyrics: String?`, `syncedLyrics: String?`, `chords: String?`, `sourceProvider: String`, `isOfflineCache: Boolean`, `syncOffsetMs: Long`, `errorMessage: String?`.
- `NetworkStatus` — перечисление: `WIFI_UNMETERED`, `CELLULAR_METERED`, `DISCONNECTED`.
- `LrcLine` — поля: `timestampMs: Long`, `text: String`. Инвариант: `timestampMs >= 0`.
- `ParsedChords` — поля: `hasChords: Boolean`, `chordsBlock: String`, `tonality: String?`.

---

### Публичный API

#### 1. Функция `getLyrics(trackKey: String, title: String, artist: String, album: String = "", forceNetwork: Boolean = false) -> LyricsDataResult`
- **Сигнатура:** `suspend fun getLyrics(trackKey: String, title: String, artist: String, album: String = "", forceNetwork: Boolean = false): LyricsDataResult`
- **Предусловия:** `trackKey.isNotBlank() == true`, `title.isNotBlank() == true`.
- **Постусловия:** Возвращает результат из кэша либо сети. Если текст найден в сети, он сохраняется в таблицу `lyrics_cache`.
- **Поведение:**
  1. Запрашивает локальный кэш `lyrics_cache` по `trackKey`.
  2. Если запись найдена и содержит `plainLyrics`, `syncedLyrics` или `chords`:
     - Вернуть `LyricsDataResult(..., isOfflineCache = true, sourceProvider = "RoomCache")`.
  3. Проверяет наличие личных заметок пользователя в `music_tracks.userNotes`. Если заметки есть и сеть не форсирована (`forceNetwork == false`):
     - Вернуть `LyricsDataResult(..., plainLyrics = userNotes, isOfflineCache = true, sourceProvider = "UserNotes")`.
  4. Проверяет тип сетевого соединения через `NetworkConditionManager`:
     - Если соединение `DISCONNECTED`: вернуть `LyricsDataResult(hasLyrics=false, isOfflineCache=true, errorMessage="Офлайн. Текст не сохранён.")`.
     - Если соединение `CELLULAR_METERED` и `forceNetwork == false`: вернуть `LyricsDataResult(hasLyrics=false, isOfflineCache=false, errorMessage="Экономия мобильного трафика. Нажмите для поиска.")`.
  5. Если соединение `WIFI_UNMETERED` или `forceNetwork == true`:
     - Выполняет каскадный сетевой опрос:
       a. `LrcLibLyricsProvider.getLyrics(title, artist)`: если найден `syncedLyrics` или `plainLyrics`, зафиксировать.
       b. Если в названии или исполнителе кириллица: выполнить параллельный запрос к `AmDmScraper.scrape(title, artist)` для извлечения табулатуры и аккордов.
       c. Если LRCLIB не вернул результат, опросить резервные провайдеры (`LyricFind`, `Textpesni`, `Genius`).
  6. Если текст или аккорды найдены:
     - Сохранить в `lyrics_cache` через `LyricsDao.insertOrUpdate(LyricsCacheEntity(...))`.
     - Вернуть `LyricsDataResult(hasLyrics=true, isOfflineCache=false, ...)`.
  7. Если ни один источник не дал результата:
     - Вернуть `LyricsDataResult(hasLyrics=false, isOfflineCache=false, errorMessage="Текст не найден в доступных базах")`.
- **Ошибки:** Сетевые сбои (SocketTimeoutException, UnknownHostException) перехватываются, возвращается объект с `hasLyrics=false` и описанием ошибки в `errorMessage`. Падений приложения не происходит.
- **Побочные эффекты:** Сетевые HTTP GET запросы, запись в `lyrics_cache`.
- **Граничные случаи:**
  - Отсутствие интернета при пустом кэше $\to$ мгновенный возврат с сообщением об офлайне ($< 5$ мс).
  - Название трека содержит скобки с ремиксами $\to$ нормализуется перед запросом к API.
- **Примеры:**
  1. Вход: `forceNetwork=false`, трек уже в кэше $\to$ Выход: `hasLyrics=true, sourceProvider="RoomCache", isOfflineCache=true`.
  2. Вход: `forceNetwork=false`, сотовая сеть, в кэше нет $\to$ Выход: `hasLyrics=false, errorMessage="Экономия мобильного трафика..."`.
  3. Вход: `forceNetwork=true`, сотовая сеть, трек найден на LRCLIB $\to$ Выход: `hasLyrics=true, syncedLyrics="[00:10.00]...", isOfflineCache=false`.

#### 2. Функция `parseLrcTimestamps(rawLrc: String) -> List<LrcLine>`
- **Сигнатура:** `fun parseLrcTimestamps(rawLrc: String): List<LrcLine>`
- **Предусловия:** `rawLrc.isNotBlank() == true`.
- **Постусловия:** Возвращает отсортированный по возрастанию `timestampMs` список строк караоке.
- **Поведение:**
  1. Разбивает `rawLrc` на строки.
  2. Применяет регулярное выражение `^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$` к каждой строке.
  3. Вычисляет миллисекунды: `ms = minutes * 60000 + seconds * 1000 + (fraction * 10 или fraction)`.
  4. Фильтрует пустые служебные строки тегов (`[ar:...]`, `[ti:...]`).
  5. Сортирует результат по `timestampMs`.
- **Ошибки:** Строки, не соответствующие формату, безопасно пропускаются.
- **Побочные эффекты:** нет.
- **Граничные случаи:** Текст без таймкодов $\to$ пустой список `emptyList()`.
- **Примеры:**
  1. Вход: `"[01:23.45]Hello world"` $\to$ Выход: `listOf(LrcLine(timestampMs=83450, text="Hello world"))`.
  2. Вход: `"[00:05.100]First\n[00:02.000]Intro"` $\to$ Выход: `listOf(LrcLine(2000, "Intro"), LrcLine(5100, "First"))` (отсортировано).
  3. Вход: `"Обычный текст без таймкодов"` $\to$ Выход: `emptyList()`.

#### 3. Функция `enqueueWifiPrefetch(trackKey: String, title: String, artist: String)`
- **Сигнатура:** `suspend fun enqueueWifiPrefetch(trackKey: String, title: String, artist: String): Unit`
- **Предусловия:** `trackKey.isNotBlank() == true`.
- **Постусловия:** Если устройство подключено к Wi-Fi и трек отсутствует в кэше, запускает фоновую загрузку с низким приоритетом (`Dispatchers.IO`).
- **Поведение:**
  1. Проверяет `lyrics_cache` по `trackKey`. Если запись уже есть, немедленно выходит.
  2. Проверяет статус сети: если не `WIFI_UNMETERED`, выходит.
  3. Выполняет `getLyrics(trackKey, title, artist, forceNetwork=true)` в фоновой корутине.
- **Ошибки:** ошибки сети логируются в Timber без прерывания работы.
- **Побочные эффекты:** запись в кэш текстов.
- **Граничные случаи:** Быстрая смена 10 треков подряд $\to$ последовательная обработка в очереди с ограничением параллелизма `semaphore(2)`.
- **Примеры:**
  1. Вход: новый трек на Wi-Fi $\to$ текст скачивается и кладется в кэш.
  2. Вход: новый трек на 4G LTE $\to$ задача отклоняется без сетевых запросов.
  3. Вход: трек уже кэширован $\to$ моментальный выход без сетевых запросов.

---

### Зависимости
- Межзонный контракт: [.sdd/contracts/core__lyrics.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__lyrics.md)
- Внешние библиотеки: `android.net.ConnectivityManager`, `org.json:json`.

---

### Вне скоупа
- Воспроизведение звука.
- Прямое редактирование пользовательских заметок (выполняется через `media-core`).
- Отображение графического интерфейса караоке (делегируется в `media-ui`).
