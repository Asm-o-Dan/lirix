# Межзонный контракт: ingress__core

- **Зоны:** `media-ingress` (источник) ──> `media-core` (приёмник)
- **Версия:** FROZEN v1
- **Дата фиксации:** 2026-10-01
- **Статус:** FROZEN
- **Нормативная база:** [architecture.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture.md), ADR-002, ADR-003

---

## 1. Назначение контракта

Контракт регламентирует передачу очищенных, дедуплицированных и валидированных сигналов воспроизведения медиаконтента от слоя захвата ОС (`media-ingress`) в доменное ядро учёта прослушиваний (`media-core`).

---

## 2. Типы данных

### 2.1 Перечисление `PlaybackStateEnum`
```kotlin
enum class PlaybackStateEnum {
    PLAYING,
    PAUSED,
    STOPPED,
    BUFFERING
}
```
- **Инварианты:** Допустимы только 4 указанных значения. Промежуточные состояния Android OS (`PlaybackState.STATE_FAST_FORWARDING`, `STATE_SKIPPING_TO_NEXT`) нормализуются в `PLAYING` либо `BUFFERING`.

### 2.2 Модель `MediaPlaybackSignal`
```kotlin
data class MediaPlaybackSignal(
    val title: String,
    val artist: String,
    val album: String = "",
    val packageName: String,
    val playbackState: PlaybackStateEnum,
    val timestamp: Long = System.currentTimeMillis()
)
```
- **Поля и ограничения:**
  - `title`: `String`, обязательное, не пустое (`title.isNotBlank() == true`), максимальная длина 255 символов.
  - `artist`: `String`, обязательное, не пустое (`artist.isNotBlank() == true`), значение по умолчанию при отсутствии в тегах: `"Unknown Artist"`, длина до 255 символов.
  - `album`: `String`, опциональное, если не найдено — пустая строка `""`.
  - `packageName`: `String`, валидный package ID Android-приложения (`^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$`).
  - `playbackState`: `PlaybackStateEnum`.
  - `timestamp`: `Long`, epoch milliseconds, $timestamp > 0$.

---

## 3. Гарантии источника (`media-ingress`)

1. **Жесткий отсев не-медиа событий (Zero Non-Media Ingress Guarantee):**
   - Уведомления от приложений, не входящих в `KNOWN_MEDIA_PACKAGES` и не имеющих флагов `Notification.EXTRA_MEDIA_SESSION`, `"android.mediaSession"`, `Notification.CATEGORY_TRANSPORT` или шаблона `MediaStyle`, отбрасываются до парсинга.
   - Банковские уведомления, OTP, СМС, мессенджеры и системные алерты ни при каких условиях не передаются в `media-core`.
2. **Дебаунс плееров 600 мс (Debounce Guarantee):**
   - Быстрые каскадные обновления одного и того же трека в интервале $\le 600$ мс (например, `BUFFERING` $\to$ `PLAYING` $\to$ `METADATA_UPDATE`) схлопываются в единый финальный сигнал.
3. **Кросс-источниковая дедупликация (Cross-Source Guarantee):**
   - Если плеер зарегистрировал активную сессию в `MediaSessionCollector`, уведомление в строке состояния от того же `packageName` подавляется в `CrossSourceCorrelator`. В `media-core` приходит ровно один сигнал.
4. **Нормализация названий:**
   - Названия очищены от мусора плееров («Official Video», «Remix», «feat.», названий плееров) через `MusicTrackParser`.

---

## 4. Гарантии приёмника (`media-core`)

1. **Идемпотентность обработки:**
   - Повторный приём `MediaPlaybackSignal` с тем же `(title, artist, album, packageName)` и состоянием `PLAYING` в пределах 30 секунд не увеличивает счетчик `playCount`.
2. **Асинхронность и неблокирующий приём:**
   - Обработка сигнала выполняется в фоновом пуле корутин (`Dispatchers.IO`). Приёмник никогда не блокирует поток вызова `NotificationListener` или `MediaController.Callback`.
3. **Персистентность:**
   - Каждый валидный сигнал с состоянием `PLAYING` регистрируется в таблице `music_tracks` и обновляет активную запись в `music_listening_sessions`.

---

## 5. Сигнатура точки интеграции

```kotlin
interface MediaIngressListener {
    suspend fun onMediaSignalReceived(signal: MediaPlaybackSignal): TrackProcessingResult
}

data class TrackProcessingResult(
    val trackKey: String,
    val isNewTrack: Boolean,
    val currentPlayCount: Int,
    val isFavorite: Boolean
)
```

---

## 6. Примеры сигналов

### Пример 1 (Обычный трек из Spotify):
- Вход: `MediaPlaybackSignal(title="Numb", artist="Linkin Park", album="Meteora", packageName="com.spotify.music", playbackState=PlaybackStateEnum.PLAYING, timestamp=1727820000000)`
- Выход `TrackProcessingResult`: `trackKey="3a1f8...", isNewTrack=false, currentPlayCount=6, isFavorite=true`

### Пример 2 (Новый трек без указания альбома):
- Вход: `MediaPlaybackSignal(title="Группа крови", artist="Кино", album="", packageName="ru.yandex.music", playbackState=PlaybackStateEnum.PLAYING, timestamp=1727820010000)`
- Выход `TrackProcessingResult`: `trackKey="c8b41...", isNewTrack=true, currentPlayCount=1, isFavorite=false`

### Пример 3 (Пауза трека):
- Вход: `MediaPlaybackSignal(title="Numb", artist="Linkin Park", album="Meteora", packageName="com.spotify.music", playbackState=PlaybackStateEnum.PAUSED, timestamp=1727820120000)`
- Выход `TrackProcessingResult`: `trackKey="3a1f8...", isNewTrack=false, currentPlayCount=6, isFavorite=true` (сессия помечена как приостановленная, `playCount` не инкрементирован).
