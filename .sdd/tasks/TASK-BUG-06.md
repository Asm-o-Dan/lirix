# Задача TASK-BUG-06: Отображение и функционал аккордов AmDm в NowPlaying (парсер, хранение, транспонирование)

- **ID задачи:** `TASK-BUG-06`
- **Роль исполнителя:** Кодер
- **Зона:** `lyrics-engine` / `media-ui` / `media-core`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/feature/LyricsProvider.kt` (добавление `chords` в `LyricsResult`, сохранение аккордов в `scrapeAmDm`)
  2. `app/src/main/java/com/eventengine/app/feature/lyrics/AmDmChordParser.kt` (извлечение аккордов из HTML, распознавание инлайн-аккордов, транспонирование $\pm 12$ полутонов)
  3. `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt` (сохранение `chordsAmDm` в `lyrics_cache`)
  4. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (интерактивный экран аккордов с транспозитором)
  5. `app/src/test/java/com/eventengine/app/AmDmChordParserTest.kt` (тесты парсинга, сохранения и транспонирования)
- **Спека:** [.sdd/specs/lyrics-engine/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/lyrics-engine/overview.md) (v1)
- **Приоритет:** HIGH (Дефект: вкладка «Аккорды» в NowPlaying всегда пуста)

---

## 1. Root Cause Analysis (RCA)

1. **Отсутствие поля в контракте `LyricsResult`:** В `LyricsProvider.kt` класс данных `LyricsResult` содержал только `plainLyrics` и `syncedLyrics`. Поле `chords: String?` отсутствовало, из-за чего провайдеры физически не могли вернуть аккорды.
2. **Принудительное вырезание аккордов в `scrapeAmDm`:** В `LyricsProvider.kt` регулярное выражение `.replace(Regex("""<div class="podbor__chord"...>"""), "")` удаляло саму аккордовую разметку из HTML AmDm.ru перед возвратом.
3. **Ошибочный вызов парсера:** В `MusicFeatureEngine` и `NowPlayingScreen` вызывался `AmDmChordParser.parseAmDmHtml(plain)`. Так как `plain` уже являлся голым текстом без HTML-тегов `<pre itemprop="chordsBlock">`, метод закономерно возвращал `null`.

---

## 2. Спецификация изменений

### 2.1 Расширение модели `LyricsResult`
В `LyricsProvider.kt`:
```kotlin
data class LyricsResult(
    val hasLyrics: Boolean,
    val lyricsText: String,
    val source: String,
    val isUserNote: Boolean = false,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val chords: String? = null
)
```

### 2.2 Модернизация `AmDmChordParser.kt`

Добавить 3 функции:
1. **`extractChordsWithLyrics(html: String?): String?`:**
   Извлекает блок `<pre itemprop="chordsBlock">` или `<div class="b-podbor__text">`, преобразует `<div class="podbor__chord">` в текстовые аккорды над строками или в скобках, очищает технический HTML, декодирует сущности (`&nbsp;`, `&quot;`) и возвращает форматированный текст.
2. **`detectAndFormatInlineChords(text: String): String?`:**
   Если текст уже содержит аккордовые строки (например, последовательности `Am`, `Dm`, `E`, `C`, `G`, `F`, `H7`, `B7`), сохраняет их как аккордовую разметку.
3. **`transpose(chordsText: String, semitones: Int): String`:**
   Сдвигает все найденные аккорды на `semitones` (-11..+11 полутонов) с учетом диезов/бемолей:
   - Хроматическая гамма: `C`, `C#`, `D`, `D#`, `E`, `F`, `F#`, `G`, `G#`, `A`, `A#`, `B` (или `H`).

### 2.3 Сохранение в `MusicFeatureEngine.kt` и `NowPlayingScreen.kt`
При сохранении в `lyrics_cache`:
```kotlin
val entity = LyricsCacheEntity(
    trackKey = track.trackKey,
    plainLyrics = plain,
    syncedLyricsLrc = synced,
    chordsAmDm = fetched.chords ?: AmDmChordParser.detectAndFormatInlineChords(plain),
    userNotes = "",
    provider = fetched.source
)
db.lyricsDao().saveLyrics(entity)
```

### 2.4 Интерактивный UI аккордов в `NowPlayingScreen.kt`

В блоке `NowPlayingMode.CHORDS`:
1. **Панель транспонирования:**
   - Кнопка `[-1]` (на полтона ниже)
   - Бейдж с текущим сдвигом: `[Тональность: 0]` (или `+1`, `-2`) с кнопкой сброса
   - Кнопка `[+1]` (на полтона выше)
2. **Форматированный рендеринг:**
   - Моноширинный шрифт `FontFamily.Monospace` с межстрочным интервалом `22.sp`.
   - Аккордовые токены подсвечиваются цветом `AppColors.CyberCyan` или `AppColors.AmberGold`.
   - Текст песни отображается цветом `AppColors.TextPrimary`.

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `AmDmChordParserTest.kt`:**
   - Парсинг реального фрагмента HTML AmDm.ru сохраняет аккорды и текст.
   - Транспонирование `Am -> Bm` при `semitones = +2` и `Am -> Gm` при `semitones = -2`.
2. В приложении:
   - Для песен с AmDm вкладка «Аккорды» отображает читаемую аппликатуру и слова.
   - Нажатие на `[+1]` и `[-1]` мгновенно транспонирует аккорды в реальном времени.
3. Все unit-тесты (`./gradlew test`) успешно проходят.
