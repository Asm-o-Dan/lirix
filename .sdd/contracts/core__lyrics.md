# Межзонный контракт: core__lyrics

- **Зоны:** `media-core` (потребитель/владелец треков) ──> `lyrics-engine` (провайдер текстов и аккордов)
- **Версия:** FROZEN v1
- **Дата фиксации:** 2026-10-01
- **Статус:** FROZEN
- **Нормативная база:** [architecture.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture.md), ADR-002, ADR-003

---

## 1. Назначение контракта

Контракт специфицирует протокол запроса, кэширования, парсинга и сетевой загрузки текстов песен, таймкодов караоке (LRC) и гитарных табулатур (AmDm) с дифференциацией по типу сетевого подключения (Wi-Fi vs Cellular).

---

## 2. Типы данных

### 2.1 Сетевая политика `LyricsNetworkPolicy`
```kotlin
enum class LyricsNetworkPolicy {
    WIFI_ONLY_PREFETCH,  // Автоматическая фоновая предзагрузка только при безлимитном Wi-Fi
    LAZY_ON_DEMAND,      // Загрузка только по явному запросу пользователя (сотовая сеть / по запросу)
    OFFLINE_ONLY         // Только локальный Room кэш и личные заметки
}
```

### 2.2 Формат ответа `LyricsDataResult`
```kotlin
data class LyricsDataResult(
    val trackKey: String,
    val hasLyrics: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?,     // Строки вида "[01:23.45] Текст строки караоке"
    val chords: String?,           // Аккорды AmDm с разметкой
    val sourceProvider: String,    // "RoomCache", "LRCLIB", "AmDm", "LyricFind", "UserNotes"
    val isOfflineCache: Boolean,
    val syncOffsetMs: Long = 0L,   // Пользовательский сдвиг таймингов караоке (+/- мс)
    val errorMessage: String? = null
)
```

- **Инварианты полей:**
  - Если `hasLyrics == true`, то хотя бы одно из полей `plainLyrics`, `syncedLyrics`, `chords` обязано быть не пустым.
  - `syncedLyrics`: должен соответствовать регулярному выражению `(?m)^\[\d{2}:\d{2}\.\d{2,3}\].*$`.
  - `chords`: блок текста, содержащий аккорды над словами либо стандартные гармонические сетки (например, `Am C Dm E`).
  - `syncOffsetMs`: целочисленный сдвиг в диапазоне `[-10000..10000]` мс.

---

## 3. Гарантии провайдера (`lyrics-engine`)

1. **Приоритет локального кэша (Zero-Latency Offline Guarantee):**
   - Если текст для `trackKey` уже присутствует в таблице `lyrics_cache`, он отдаётся моментально ($< 15$ мс) без проверки сети.
2. **Сетевая адаптивность (Network-Aware Fetching):**
   - При подключении к мобильной сети (Cellular / Metered) фоновая предзагрузка **блокируется**. Запрос выполняется только при флаге `forceNetwork == true`.
   - При подключении к Wi-Fi (`NET_CAPABILITY_NOT_METERED`) новые треки ставятся в очередь фоновой предзагрузки (`prefetchQueue`).
3. **Каскад источников:**
   1. `RoomCache` (локальный)
   2. `LRCLIB` (открытый API, синхронизированные LRC + чистый текст)
   3. `AmDm.ru` (аккорды и табулатуры)
   4. Резервные скраперы (Genius / LyricFind)
   5. Офлайн-заметки пользователя из `music_tracks.userNotes`
4. **Автоматическая персистентность:**
   - Любой успешно найденный из сети текст сохраняется в `lyrics_cache` базы данных `media-core` для последующей 100% офлайн работы.

---

## 4. Публичный API интеграции

```kotlin
interface LyricsEngineContract {
    /**
     * Запрос текстов для конкретного трека.
     */
    suspend fun fetchLyrics(
        trackKey: String,
        title: String,
        artist: String,
        album: String = "",
        forceNetwork: Boolean = false
    ): LyricsDataResult

    /**
     * Постановка трека в очередь предзагрузки по Wi-Fi.
     */
    suspend fun enqueuePrefetch(
        trackKey: String,
        title: String,
        artist: String
    )

    /**
     * Обновление пользовательского сдвига тайминга караоке.
     */
    suspend fun updateSyncOffset(trackKey: String, offsetMs: Long)

    /**
     * Реактивный поток состояния кэша текстов трека.
     */
    fun observeLyrics(trackKey: String): Flow<LyricsDataResult?>
}
```

---

## 5. Граничные случаи и примеры

### Пример 1 (Офлайн кэш хит):
- Вход: `fetchLyrics(trackKey="abc123hash", title="Звезда по имени Солнце", artist="Кино", forceNetwork=false)`
- Сеть: отсутствует (Airplane mode).
- Результат: `LyricsDataResult(trackKey="abc123hash", hasLyrics=true, plainLyrics="Белый снег...", syncedLyrics="[00:15.20]Белый снег...", chords="Am C Dm G", sourceProvider="RoomCache", isOfflineCache=true)`

### Пример 2 (Wi-Fi сетевой поиск с успехом на LRCLIB):
- Вход: `fetchLyrics(trackKey="xyz789hash", title="In The End", artist="Linkin Park", forceNetwork=true)`
- Сеть: Wi-Fi активен.
- Результат: `LyricsDataResult(trackKey="xyz789hash", hasLyrics=true, syncedLyrics="[00:09.12]It starts with one...", sourceProvider="LRCLIB", isOfflineCache=false)`
- Побочный эффект: данные сохранены в `lyrics_cache`.

### Пример 3 (Сотовая сеть без принудительного поиска):
- Вход: `fetchLyrics(trackKey="new_track", title="Rare Song", artist="Unknown", forceNetwork=false)`
- Сеть: Сотовая (Cellular / Metered), в кэше нет.
- Результат: `LyricsDataResult(trackKey="new_track", hasLyrics=false, plainLyrics=null, syncedLyrics=null, chords=null, sourceProvider="NetworkPolicySuppressed", isOfflineCache=false, errorMessage="Загрузка отложена: мобильная сеть. Нажмите для загрузки вручную.")`
