# Задача TASK-BUG-08: Независимый поиск аккордов с AmDm и сохранение chords в LyricsResult

- **ID задачи:** `TASK-BUG-08`
- **Роль исполнителя:** Кодер
- **Зона:** `lyrics-engine`, `media-core`, `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/feature/LyricsProvider.kt` (поле `chords` в `LyricsResult`, возврат аккордов из `scrapeAmDm`, фоновый опрос AmDm в `AggregatedLyricsProvider`)
  2. `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt` (сохранение `fetched.chords` в `chordsAmDm` в `LyricsCacheEntity`)
  3. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (сохранение `fetched.chords`, on-demand загрузка аккордов при переключении на вкладку «Аккорды», если они ещё не были загружены)
  4. `app/src/test/java/com/eventengine/app/AmDmChordCascadeTest.kt` (TDD unit-тесты: сохранение аккордов из AmDm, гибридный режим LRCLIB lyrics + AmDm chords)
- **Приоритет:** HIGH (Исправление дефекта отсутствия аккордов)

---

## 1. Назначение и контекст

Пользователь сообщил о дефекте: трек «Аберрация — Продолжаем бой» имеет источник AMDM, слова найдены, но на вкладке «Аккорды» отображается «Аккорды AmDm не найдены для этого трека».
Причина: `scrapeAmDm` вырезал блок аккордов для караоке и возвращал только текст без аккордов, а `LyricsResult` не содержал поля `chords`. Кроме того, если текст найден в LRCLIB, аккорды с AmDm вообще не опрашивались.

Требуется:
1. Добавить `chords: String? = null` в `LyricsResult`.
2. В `scrapeAmDm` извлекать сырой HTML блока `<pre itemprop="chordsBlock">` и форматировать аккорды с сохранением позиций над текстом.
3. В `AggregatedLyricsProvider`: если текст найден во внешнем источнике (LRCLIB, Textpesni и т.д.), но аккордов нет, выполнять параллельный/каскадный поиск аккордов на AmDm, формируя гибридный результат (Слова от LRCLIB + Аккорды от AmDm).
4. В `NowPlayingScreen` и `MusicFeatureEngine` записывать `entity.chordsAmDm = fetched.chords`.
5. Если пользователь открыл вкладку «Аккорды», а в кэше аккордов нет — делать асинхронный дозапрос на AmDm и обновлять экран.

---

## 2. Критерии приемки (DoD)

1. Для трека «Аберрация — Продолжаем бой» из AmDm успешно извлекаются аккорды (`Bm`, `G`, `D`, `A`) и сохраняются в `lyrics_cache.chordsAmDm`.
2. Если трек найден в `LRCLIB`, на вкладке «Караоке» отображаются синхронизированные тайминги, а на вкладке «Аккорды» — подбор с AmDm.
3. Все существующие и новые unit-тесты проходят на 100% (Green).
4. Проект успешно собирается (`assembleDebug`).
