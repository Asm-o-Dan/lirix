# Задача TASK-ING-02: реализовать `parseTrackMetadata`

**Файл:** `app/src/main/java/com/eventengine/app/feature/music/MusicTrackParser.kt` (создать / обновить)
**Спека:** [.sdd/specs/media-ingress/overview.md#parseTrackMetadata](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ingress/overview.md) (v1)

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
fun parseTrackMetadata(
    mediaTrack: String?,
    mediaArtist: String?,
    title: String?,
    text: String?
): ParsedTrackInfo
```

**Модель данных:**
```kotlin
data class ParsedTrackInfo(
    val title: String,
    val artist: String,
    val album: String = ""
)
```

**Поведение:**
1. Если `mediaTrack` и `mediaArtist` заполнены и валидны:
   - Проверить, не содержит ли `mediaTrack` разделитель `" - "`. Если `mediaArtist` совпадает с левой частью, взять правую как `title`.
   - Очистить `title` от мусора через regex `\s*[\(\[](official\s*video|remix|lyrics|audio|hd|4k)[\)\]]\s*` (ignoreCase).
   - Очистить от суффиксов плееров (`" - Spotify"`, `" - Yandex Music"`, `" - YouTube"`).
   - Вернуть `ParsedTrackInfo(title = cleanedTitle.trim(), artist = mediaArtist.trim())`.
2. Если `mediaArtist` пустой, но в `title` или `mediaTrack` есть разделитель `artist - track`:
   - Разбить по первому вхождению `" - "`.
   - Левая часть $\to$ `artist`, правая $\to$ `title`.
3. Если исполнитель не определен, но `title` не пустой:
   - `artist = "Unknown Artist"`, `title = cleanedTitle`.
4. Если ни `title`, ни `mediaTrack` не содержат осмысленного текста:
   - Вернуть `ParsedTrackInfo(title = "", artist = "")`.

**Ошибки:**
- Не выбрасывать исключений. При сбое парсинга возвращать пустой `ParsedTrackInfo("", "")`.

**Граничные случаи:**
- `"Linkin Park - Numb (Official Video)"` $\to$ `artist = "Linkin Park"`, `title = "Numb"`.
- `"Track Without Artist"` $\to$ `artist = "Unknown Artist"`, `title = "Track Without Artist"`.
- Пустые строки / null $\to$ `ParsedTrackInfo("", "")`.

**Критерий приёмки:**
- Проходят unit-тесты `MusicTrackParserTest::test_parseTrackMetadata_*`.
