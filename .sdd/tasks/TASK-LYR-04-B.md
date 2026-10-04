# Задача TASK-LYR-04-B: Приём текста и ссылок через Android Share Intent

- **ID задачи:** `TASK-LYR-04-B`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ingress` / `media-ui`
- **Файлы:**
  1. `app/src/main/AndroidManifest.xml` (регистрация `intent-filter` для `ACTION_SEND`)
  2. `app/src/main/java/com/eventengine/app/MainActivity.kt` (перехват intent, дифференциация URL vs текст)
  3. `app/src/main/java/com/eventengine/app/ui/AppScaffold.kt` (навигация и хостинг диалога привязки)
  4. `app/src/main/java/com/eventengine/app/ui/components/ShareLyricsAttachDialog.kt` (диалог привязки к играющему/недавнему треку)
  5. `app/src/test/java/com/eventengine/app/ShareIntentClassifierTest.kt` (тесты парсинга и маршрутизации входящих данных)
- **Спека:** [.sdd/architecture_lyr_community.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_lyr_community.md) (Раздел 1, 6 - Этап 2), [.sdd/intake/LYR-COMMUNITY.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/intake/LYR-COMMUNITY.md) (FR-2)
- **Приоритет:** HIGH (Входная точка для мобильного поиска текстов в браузере)

---

## 1. Назначение и контекст

Поскольку мобильный Chrome на Android не поддерживает браузерные расширения, основным сценарием взаимодействия с внешними сайтами является системный механизм **Android Share (Поделиться)**:
1. Пользователь находит песню в любом мобильном браузере (Chrome, Firefox, Яндекс, Samsung Internet).
2. Пользователь либо выделяет текст песни и нажимает «Поделиться» $\to$ Music Tracker, либо делится прямой ссылкой (URL) на страницу песни.
3. Приложение должно автоматически определить тип данных:
   - **Текст песни:** мгновенно предложить привязать его к текущему играющему треку или треку из истории.
   - **URL ссылка:** открыть встроенный Teach Mode WebView для извлечения текста и генерации правила для этого сайта (FR-3).

---

## 2. Спецификация изменений

### 2.1 Изменения в `AndroidManifest.xml`

В блоке `<activity android:name=".MainActivity">` зарегистрировать `intent-filter` для обработки текстовых данных:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

### 2.2 Обработка в `MainActivity.kt`

1. Добавить структуры состояния для переданных данных:
   ```kotlin
   sealed interface SharedMediaPayload {
       data class LyricsText(val text: String) : SharedMediaPayload
       data class WebUrl(val url: String) : SharedMediaPayload
   }
   ```
2. Реализовать классификатор `classifySharedText(raw: String): SharedMediaPayload`:
   - Если строка (после `trim()`) начинается с `http://` или `https://` и не содержит переводов строк (`\n`), классифицировать как `SharedMediaPayload.WebUrl(url)`.
   - Если строка содержит перевод строки или не является URL — классифицировать как `SharedMediaPayload.LyricsText(text)`.
3. В `handleIntent(intent)`:
   ```kotlin
   if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
       val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
           ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
       if (!rawText.isNullOrBlank()) {
           sharedPayload = classifySharedText(rawText)
       }
   }
   ```
4. Передать `sharedPayload` и коллбэк потребления `onSharedPayloadConsumed = { sharedPayload = null }` в `AppScaffold`.

### 2.3 Диалог привязки текста `ShareLyricsAttachDialog.kt`

Диалог отображается поверх экрана, когда `sharedPayload is SharedMediaPayload.LyricsText`:
- **Заголовок:** «Привязать текст из буфера»
- **Превью текста:** первые 3–4 строки (с обрезкой `maxLines = 4`, `style = Monospace`).
- **Секция выбора трека:**
  - Если сейчас играет трек (из `livePlaybackFlow` или `recentTracks.firstOrNull()`):
    - Карточка быстрого выбора: `«Текущий: [Title] — [Artist]»` (кнопка `[Привязать к нему]`).
  - Раскрывающийся список/список последних 5 треков из `db.musicDao().observeRecentTracks()`.
- **Действие при подтверждении:**
  ```kotlin
  scope.launch(Dispatchers.IO) {
      val targetTrackKey = selectedTrack.trackKey
      // 1. Dual-write в music_tracks
      db.musicDao().updateLyrics(targetTrackKey, text, null)
      // 2. Dual-write в lyrics_cache с источником share:manual
      db.lyricsDao().saveLyrics(
          LyricsCacheEntity(
              trackKey = targetTrackKey,
              plainLyrics = text,
              syncedLyricsLrc = null,
              chordsAmDm = AmDmChordParser.parseAmDmHtml(text),
              userNotes = selectedTrack.userNotes,
              provider = "share:manual"
          )
      )
  }
  ```
- **Результат:** переключение на вкладку `AppTab.NOW_PLAYING` и отображение Snackbar `«Текст успешно привязан к треку»`.

### 2.4 Маршрутизация URL в Teach Mode

Когда `sharedPayload is SharedMediaPayload.WebUrl`:
- Устанавливается состояние `activeTeachUrl = payload.url`.
- В `AppScaffold` открывается экран `TeachModeScreen(url = payload.url, ...)` в полноэкранном режиме (реализуется в TASK-LYR-04-D).

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `ShareIntentClassifierTest.kt`:**
   - `test_http_url_classified_as_web_url`: `"https://amalgama-lab.com/songs/e/eminem/mockingbird.html"` распознается как `WebUrl`.
   - `test_multiline_text_classified_as_lyrics`: `"Yeah, I know sometimes things may not make sense now\nBut hey..."` распознается как `LyricsText`.
   - `test_url_with_leading_whitespace_trimmed`: `"   https://genius.com/track   "` корректно триммится и распознается как `WebUrl`.
2. **Интеграционные сценарии:**
   - При отправке текста через `adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "Куплет 1..."` открывается диалог привязки.
   - При подтверждении текст сохраняется в базу и мгновенно появляется на экране плеера.
3. Проект успешно собирается и проходит тесты (`.\gradlew.bat test`).
