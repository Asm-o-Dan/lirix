## Модуль: MediaIngressGate   Зона: media-ingress   Версия спеки: v1   Статус: APPROVED

### Назначение
Модуль захватывает медиа-события из уведомлений ОС Android и активных MediaSession, отсекает 100% не-медиа событий на входе и передает очищенный сигнал в `media-core`. Модуль НЕ парсит финансовые, учебные, OTP или системные уведомления и НЕ сохраняет данные в базу данных.

---

### Типы данных
- `MediaPlaybackSignal` — поля: `title: String`, `artist: String`, `album: String`, `packageName: String`, `playbackState: PlaybackStateEnum`, `timestamp: Long`. Обязательные: `title`, `artist`, `packageName`, `playbackState`, `timestamp`.
- `PlaybackStateEnum` — перечисление: `PLAYING`, `PAUSED`, `STOPPED`, `BUFFERING`.
- `ParsedTrackInfo` — поля: `title: String`, `artist: String`, `album: String`. Инвариант: `title` не содержит названий приложений и суффиксов «Remix», «Official Video».
- `MediaGateDecision` — перечисление: `DROP` (не медиа или дубликат), `PROCESS` (валидный медиасигнал).

---

### Публичный API

#### 1. Функция `isMediaNotification(sbn: StatusBarNotification) -> Boolean`
- **Сигнатура:** `fun isMediaNotification(sbn: StatusBarNotification): Boolean`
- **Предусловия:** `sbn != null`, `sbn.notification != null`.
- **Постусловия:** Возвращает `true` тогда и только тогда, когда уведомление порождено медиаплеером.
- **Поведение:**
  1. Извлекает `packageName = sbn.packageName?.lowercase() ?: return false`.
  2. Проверяет вхождение `packageName` в `KNOWN_MEDIA_PACKAGES`. Если `true` $\to$ вернуть `true`.
  3. Проверяет наличие ключа `Notification.EXTRA_MEDIA_SESSION` или `"android.mediaSession"` в `notification.extras`. Если `true` $\to$ вернуть `true`.
  4. Проверяет категорию `notification.category == Notification.CATEGORY_TRANSPORT`. Если `true` $\to$ вернуть `true`.
  5. Проверяет строку `extras.getString("android.template")` на содержание `"MediaStyle"`. Если `true` $\to$ вернуть `true`.
  6. Во всех остальных случаях вернуть `false`.
- **Ошибки:** исключений не бросает, при null-полях безопасно возвращает `false`.
- **Побочные эффекты:** нет.
- **Граничные случаи:** `sbn.notification.extras == null` $\to$ возвращает `false`.
- **Примеры:**
  1. Вход: `sbn` от `"com.spotify.music"` $\to$ Выход: `true`.
  2. Вход: `sbn` от `"com.tcsbank.mobile"` $\to$ Выход: `false`.
  3. Вход: `sbn` от неизвестного плеера с шаблоном `"androidx.media.app.NotificationCompat$MediaStyle"` $\to$ Выход: `true`.

#### 2. Функция `parseTrackMetadata(mediaTrack: String?, mediaArtist: String?, title: String?, text: String?) -> ParsedTrackInfo`
- **Сигнатура:** `fun parseTrackMetadata(mediaTrack: String?, mediaArtist: String?, title: String?, text: String?): ParsedTrackInfo`
- **Предусловия:** Хотя бы один из параметров не null.
- **Постусловия:** Возвращает `ParsedTrackInfo` с очищенными полями `title` и `artist`. Если название трека определить невозможно, `title` равен `""`.
- **Поведение:**
  1. Если `mediaTrack` не пустой и `mediaArtist` не пустой и не входит в список игнорируемых слов плеера:
     - Проверить, не содержит ли `mediaTrack` шаблон `"Исполнитель - Трек"`. Если содержит и совпадает с `mediaArtist`, отделить название.
     - Очистить название трека от суффиксов `[remix|feat|official]` через `cleanTrackName`.
     - Вернуть `ParsedTrackInfo(title = cleanedTitle, artist = mediaArtist)`.
  2. Если заполнен только `title`, проверить наличие разделителей `" — "`, `" - "`, `" – "`:
     - Если разделитель найден, левая часть трактуется как `artist`, правая как `title`.
     - Если разделитель не найден, `title` остается названием, а `text` проверяется на роль исполнителя.
  3. Если в `title` или `artist` содержится служебное слово плеера («Яндекс Музыка», «Spotify», «Плеер»), очистить его или заменить на резервное.
- **Ошибки:** исключений не бросает.
- **Побочные эффекты:** нет.
- **Граничные случаи:** Все параметры равны `""` или `null` $\to$ `ParsedTrackInfo("", "", "")`.
- **Примеры:**
  1. Вход: `title = "Кино — Группа крови", text = "Звезда по имени Солнце"` $\to$ Выход: `ParsedTrackInfo(title = "Группа крови", artist = "Кино", album = "Звезда по имени Солнце")`.
  2. Вход: `title = "Spotify", text = "Linkin Park - Numb"` $\to$ Выход: `ParsedTrackInfo(title = "Numb", artist = "Linkin Park", album = "")`.
  3. Вход: `title = "Код подтверждения: 1234", text = "СберБанк"` $\to$ Выход: `ParsedTrackInfo(title = "Код подтверждения: 1234", artist = "", album = "")` (будет отсеяно гейтом `isMediaNotification`).

#### 3. Функция `onStateChange(packageName: String, title: String?, artist: String?, album: String?, stateName: String)`
- **Сигнатура:** `fun onStateChange(packageName: String, title: String?, artist: String?, album: String?, stateName: String): Unit`
- **Предусловия:** `packageName.isNotBlank() == true`, `stateName` в списке `["PLAYING", "PAUSED", "STOPPED", "BUFFERING", "UPDATE"]`.
- **Постусловия:** Запускает корутину с задержкой 600 мс. Если за это время поступил новый статус от того же `packageName`, предыдущий таймер отменяется.
- **Поведение:**
  1. Отменяет предыдущую отложенную `Job` для данного `packageName` в таблице активных корутин.
  2. Создает новую задачу с `delay(600)`.
  3. По истечении 600 мс передает `MediaPlaybackSignal` в контракт `MediaIngressListener.onMediaSignalReceived`.
- **Ошибки:** при отмене корутины (`CancellationException`) безопасно завершается.
- **Побочные эффекты:** запись в ConcurrentHashMap задач, логирование в Timber.
- **Граничные случаи:** Вызов 10 раз за 100 мс $\to$ в Core отправляется ровно 1 сигнал.
- **Примеры:**
  1. Вход: `BUFFERING` в момент $0$, `PLAYING` в момент $200$ мс $\to$ в момент $800$ мс отправлен `PLAYING`.
  2. Вход: `STOPPED` $\to$ немедленно снимает регистрацию в `CrossSourceCorrelator`.
  3. Вход: `title == null` и `artist == null` $\to$ событие игнорируется, корутина не запускается.

---

### Зависимости
- Межзонный контракт: [.sdd/contracts/ingress__core.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/ingress__core.md)
- Внешние библиотеки: `androidx.core:core-ktx:1.15.0`, `com.jakewharton.timber:timber:5.0.1`.

---

### Вне скоупа
- Сохранение событий в базу данных Room.
- Загрузка текстов песен или обложек альбомов.
- Обработка любых SMS, сообщений мессенджеров или push-уведомлений от банков.
- Взаимодействие с UI или показ собственных уведомлений.
