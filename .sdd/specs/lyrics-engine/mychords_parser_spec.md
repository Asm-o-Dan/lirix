# Спецификация SPEC-MYCHORDS-01: Резервный парсер текстов и аккордов MyChords.net

## 1. Контекст и Цели
Добавить провайдер и парсер текстов и аккордов с портала `mychords.net` в качестве резервного источника для AmDm.ru:
- Если AmDm не вернул результат или отклонен пользователем, движок обращается к MyChords.
- В гибридном режиме (когда текст получен из LrcLib, но аккордов нет), если AmDm не нашел аккорды, движок подтягивает аккорды из MyChords.

## 2. Архитектурные Требования

### Зона 1: Модель и Константы (`LyricsProvider.kt`)
- Добавить константу в `LyricsSourceIds`:
  ```kotlin
  const val MYCHORDS = "builtin:mychords"
  ```
- Описать структуру результата скрейпинга:
  ```kotlin
  data class MyChordsScrapeResult(
      val plainLyrics: String,
      val chords: String?
  )
  ```

### Зона 2: Парсер HTML (`app/src/main/java/com/lirix/app/feature/lyrics/MyChordsParser.kt`)
Создать `object MyChordsParser`:
- Метод `parseMyChordsHtml(html: String?): MyChordsScrapeResult?`
  1. Найти контейнер текста песни:
     Ищется блок `class="w-words__text"` или атрибут `itemprop="lyrics"` / `itemprop="text"`.
     Границы блока: до `<div class="b-words__text-actions"` или закрывающих тегов.
  2. Обработка структуры разметки:
     - Заменить теги `<span class="subline">` на начало строки, а закрывающие `</span>` на перевод строки `\n`.
     - Заменить теги `<div class="pline">` и `<div class="single-line">` на переводы строк `\n`.
     - Заменить теги `<br>` на `\n`.
     - Удалить все оставшиеся теги `<[^>]+>`.
     - Разэкранировать HTML сущности: `&nbsp;` -> `" "`, `&quot;` -> `"\""`, `&amp;` -> `"&"`, `&#39;` -> `"'"` и др.
     - Очистить избыточные пустые строки (не более двух подряд).
  3. Формирование `plainLyrics`:
     Текст с вырезанными аккордами (строки, состоящие исключительно из аккордов, удаляются, либо используется парсер `AmDmChordParser.detectAndFormatInlineChords`).
  4. Формирование `chords`:
     Полный текст с сохраненным выравниванием аккордов над строками.

### Зона 3: Интеграция в FallbackLyricsScraper (`LyricsProvider.kt`)
- В `FallbackLyricsScraper`:
  - Метод `suspend fun scrapeMyChordsDetails(artist: String, title: String): MyChordsScrapeResult?`
    1. Поиск через Autocomplete API:
       `https://mychords.net/ru/ajax/autocomplete?q=` + URLEncoder.encode("$artist $title", "UTF-8")
       Заголовок: `"X-Requested-With" to "XMLHttpRequest"`
       Парсинг JSON ответа `{"suggestions":[{"value":"...","data":{"url":"..."}}]}`.
       Поиск ссылки на песню (содержит `/ru/` и `.html`).
    2. Если ссылка найдена, загрузить HTML по `https://mychords.net$url` (или полный URL).
    3. Распарсить через `MyChordsParser.parseMyChordsHtml(pageHtml)`.
- В методе `FallbackLyricsScraper.getLyrics()`:
  - Сразу после блока AmDm:
    ```kotlin
    if (LyricsSourceIds.MYCHORDS !in rejectedSourceIds) {
        val myChordsResult = scrapeMyChordsDetails(cleanArtist, cleanTitle)
        if (myChordsResult != null) {
            return LyricsResult(
                hasLyrics = true,
                lyricsText = myChordsResult.plainLyrics,
                source = "MyChords",
                plainLyrics = myChordsResult.plainLyrics,
                sourceId = LyricsSourceIds.MYCHORDS,
                chords = myChordsResult.chords
            )
        }
    }
    ```

### Зона 4: Каскадный поиск аккордов в `AggregatedLyricsProvider`
- Метод `fetchChordTabs(track, rejectedSourceIds, forceNetwork)`:
  - Опрашивает AmDm.
  - Если аккорды не найдены и `LyricsSourceIds.MYCHORDS !in rejectedSourceIds`, опрашивает MyChords.
  - Возвращает аккорды для гибридного объединения с текстом из LrcLib.

### Зона 5: Тестирование (`MyChordsParserTest.kt`)
- Создать модульные тесты в `app/src/test/java/com/lirix/app/MyChordsParserTest.kt`:
  - Проверка извлечения аккордов и текста из реального фрагмента HTML страницы `mychords.net` (Day Darogu - Supersession).
  - Проверка сохранения пробелов и выравнивания аккордов.
  - Проверка очистки HTML-тегов и спецсимволов.

---

## 3. Критерии приемки (DoD)
1. Создан `MyChordsParser.kt` и unit-тест `MyChordsParserTest.kt`.
2. Реализован поиск и загрузка через `scrapeMyChordsDetails` в `FallbackLyricsScraper`.
3. Поддержан каскадный фолбэк для текстов и аккордов при отсутствии на AmDm.
4. Все unit-тесты зеленые (`./gradlew testDebugUnitTest`).
5. Release APK успешно собирается (`./gradlew assembleRelease`).
