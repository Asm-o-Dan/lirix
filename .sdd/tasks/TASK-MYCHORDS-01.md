# Задача TASK-MYCHORDS-01: Реализация парсера и провайдера MyChords.net как резерва для AmDm

**Статус**: DONE  
**Зона**: `lyrics-engine`  
**Спецификация**: `.sdd/specs/lyrics-engine/mychords_parser_spec.md`  
**Исполнитель**: Кодер Media Ingress & UI  

## Описание
Реализовать поддержку портала `mychords.net` для извлечения текстов песен и гитарных аккордов в случае, если трек отсутствует на `amdm.ru` или источник AmDm отклонен пользователем.

## Чек-лист реализации

### 1. `app/src/main/java/com/lirix/app/feature/lyrics/MyChordsParser.kt`
- Создать парсер `MyChordsParser`.
- Извлекать контейнер `w-words__text` / `b-words__centering`.
- Преобразовывать `<span class="subline">`, `<div class="pline">`, `<div class="single-line">` и `<br>` в переносы строк с сохранением выравнивания аккордов.
- Очищать HTML-теги и декодировать сущности (`&nbsp;`, `&quot;`, `&amp;` и др.).
- Формировать `MyChordsScrapeResult(plainLyrics, chords)`.

### 2. `app/src/main/java/com/lirix/app/feature/LyricsProvider.kt`
- Добавить `const val MYCHORDS = "builtin:mychords"` в `LyricsSourceIds`.
- Добавить `data class MyChordsScrapeResult(val plainLyrics: String, val chords: String?)`.
- В `FallbackLyricsScraper` добавить:
  - `suspend fun scrapeMyChordsDetails(artist: String, title: String): MyChordsScrapeResult?`
  - Поиск через `https://mychords.net/ru/ajax/autocomplete?q={q}` с заголовком `"X-Requested-With" to "XMLHttpRequest"`.
  - Загрузка найденной страницы песни и парсинг через `MyChordsParser`.
  - Интеграция в каскад `FallbackLyricsScraper.getLyrics` после AmDm.
- В `AggregatedLyricsProvider` обновить логику получения аккордов (`fetchAmDmChords` -> расширить до каскада `AmDm` -> `MyChords`).

### 3. `app/src/test/java/com/lirix/app/MyChordsParserTest.kt`
- Написать юнит-тесты на парсинг HTML с MyChords (валидный блок `w-words__text`, выравнивание аккордов, очистка тегов).

---

## Верификация
1. `./gradlew testDebugUnitTest` — должен завершиться `BUILD SUCCESSFUL` (0 ошибок).
2. `./gradlew assembleRelease` — успешная сборка APK.
3. Предоставить отчет о тестировании и статусе.
