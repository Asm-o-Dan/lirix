# Модуль: TelegramPauseController (в составе MediaSessionCollector)   Зона: media-ingress   Версия спеки: v1   Статус: APPROVED

- **ID спеки:** `SPEC-TG-01`
- **Роль:** Спецификатор (Spec Driven Design)
- **Связанный архитектурный отчет:** [.sdd/reports/BUG_TG_01.architect.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/reports/BUG_TG_01.architect.md)
- **Целевой файл реализации:** [`MediaSessionCollector.kt`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt)
- **Зона ответственности:** `media-ingress`

---

### Назначение

Модуль обеспечивает детерминированное переключение воспроизведения (`Play/Pause`) во внешних медиаплеерах Android с множественными параллельными сессиями (`Telegram`, `AyuGram`) через математически строгий выбор единого целевого контроллера (`Single Target Controller`) и двухуровневую безопасную иерархию IPC-команд.
Модуль **НЕ** выполняет циклическую рассылку команд по множеству контроллеров, **НЕ** отправляет двухпозиционные переключатели (`KEYCODE_MEDIA_PLAY_PAUSE`) при запросе паузы, **НЕ** изменяет логику персистентности в Room и **НЕ** модифицирует дебаунс метаданных `MediaDebounceFilter`.

---

### Типы данных

1. **`android.media.session.MediaController`** (системный класс Android):
   - `packageName: String` — имя пакета приложения-владельца сессии. Инвариант: не пустое.
   - `playbackState: PlaybackState?` — текущее состояние воспроизведения или `null`.
   - `metadata: MediaMetadata?` — текущие метаданные трека или `null`.
   - `transportControls: MediaController.TransportControls` — IPC-интерфейс отправки управляющих команд (`play()`, `pause()`, `seekTo(Long)`).
   - `dispatchMediaButtonEvent(event: KeyEvent): Boolean` — синхронный метод отправки аппаратного медиа-события.

2. **`android.media.session.PlaybackState`** (системный класс Android):
   - `state: Int` — статус плеера (`PlaybackState.STATE_PLAYING = 3`, `PlaybackState.STATE_PAUSED = 2`, `PlaybackState.STATE_STOPPED = 1`, `PlaybackState.STATE_NONE = 0`).
   - `actions: Long` — 64-битная битовая маска поддерживаемых действий:
     - `PlaybackState.ACTION_STOP = 1L` ($2^0$)
     - `PlaybackState.ACTION_PAUSE = 2L` ($2^1$)
     - `PlaybackState.ACTION_PLAY = 4L` ($2^2$)
     - `PlaybackState.ACTION_SKIP_TO_PREVIOUS = 16L` ($2^4$)
     - `PlaybackState.ACTION_SKIP_TO_NEXT = 32L` ($2^5$)
     - `PlaybackState.ACTION_SEEK_TO = 256L` ($2^8$)
     - `PlaybackState.ACTION_PLAY_PAUSE = 512L` ($2^9$)
   - `position: Long` — позиция воспроизведения в миллисекундах.

3. **`LivePlaybackSnapshot`** (внутренняя модель `media-ingress`):
   - `packageName: String` — имя пакета источника.
   - `title: String`, `artist: String`, `album: String` — метаданные текущего трека.
   - `isPlaying: Boolean` — флаг активного воспроизведения.
   - `basePositionMs: Long`, `lastPositionUpdateTimeMs: Long`, `playbackSpeed: Float`, `durationMs: Long`, `albumArtUri: String?`.

4. **`ControllerScoreVector`** (математический кортеж скоринга):
   Кортеж $V(c) = \langle p_1, p_2, p_3, p_4, p_5 \rangle \in \{0, 1\}^5$, где:
   - $p_1(c) \in \{0, 1\}$: равен $1 \iff c.\text{playbackState}?.state == \text{PlaybackState.STATE\_PLAYING}$.
   - $p_2(c) \in \{0, 1\}$: равен $1 \iff \big((c.\text{playbackState}?.actions \ ?: \ 0\text{L}) \ \& \ (\text{PlaybackState.ACTION\_PLAY} \mid \text{PlaybackState.ACTION\_PAUSE})\big) \neq 0\text{L}$.
   - $p_3(c) \in \{0, 1\}$: равен $1 \iff (c.\text{playbackState}?.actions \ ?: \ 0\text{L}) > 0\text{L}$.
   - $p_4(c) \in \{0, 1\}$: равен $1 \iff \big((c.\text{playbackState}?.actions \ ?: \ 0\text{L}) \ \& \ \text{PlaybackState.ACTION\_SEEK\_TO}\big) \neq 0\text{L}$.
   - $p_5(c) \in \{0, 1\}$: равен $1 \iff c.\text{metadata} \neq \text{null}$.

   Интегральная скалярная функция оценки $S(c) \in \mathbb{Z}_{\ge 0}$:
   $$S(c) = 10000 \cdot p_1(c) + 1000 \cdot p_2(c) + 100 \cdot p_3(c) + 50 \cdot p_4(c) + 10 \cdot p_5(c)$$
   Поскольку каждый старший весовой коэффициент строго превосходит сумму всех младших:
   $$10000 > 1000 + 100 + 50 + 10 = 1160, \quad 1000 > 100 + 50 + 10 = 160, \quad 100 > 50 + 10 = 60, \quad 50 > 10,$$
   отношение порядка по скаляру $S(c)$ строго изоморфно лексикографическому упорядочению кортежей $V(c)$.

---

### Публичный API

#### 1. Функция `resolveTargetController(controllers: List<MediaController>): MediaController?`

- **Сигнатура:**
  ```kotlin
  fun resolveTargetController(controllers: List<MediaController>): MediaController?
  ```
  *(Размещается в `MediaSessionCollector.Companion` для детерминированного вызова и прямого unit-тестирования).*

- **Предусловия:**
  1. Вызывающий передает список `controllers: List<MediaController>`.
  2. Элементы списка не равны `null` (гарантируется системой типов Kotlin).
  3. Список может быть пустым (`controllers.isEmpty() == true`).

- **Постусловия:**
  1. Если `controllers.isEmpty() == true` $\implies$ возвращает `null`.
  2. Если `controllers.isNotEmpty() == true` $\implies$ возвращает ровно один контроллер $c^* \in controllers$, удовлетворяющий условию:
     $$c^* = \arg\max_{c \in controllers} S(c)$$
  3. При равенстве оценок $S(c_i) = S(c_j)$ для $i < j$ возвращается первый встретившийся контроллер $c_i$ (стабильный выбор).

- **Поведение:**
  1. Проверить базовое условие пустоты: если `controllers.isEmpty()`, немедленно вернуть `null`.
  2. Инициализировать переменные максимальной оценки:
     `var bestController: MediaController? = null`
     `var maxScore = -1`
  3. Для каждого `controller` в `controllers`:
     a. Извлечь `state = controller.playbackState`.
     b. Извлечь `actions = state?.actions ?: 0L`.
     c. Вычислить предикаты:
        - `isPlaying = (state?.state == PlaybackState.STATE_PLAYING)`
        - `hasPlayPauseAction = (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)) != 0L`
        - `hasPositiveActions = (actions > 0L)`
        - `hasSeekToAction = (actions and PlaybackState.ACTION_SEEK_TO) != 0L`
        - `hasMetadata = (controller.metadata != null)`
     d. Вычислить скалярный балл:
        ```kotlin
        val score = (if (isPlaying) 10000 else 0) +
                    (if (hasPlayPauseAction) 1000 else 0) +
                    (if (hasPositiveActions) 100 else 0) +
                    (if (hasSeekToAction) 50 else 0) +
                    (if (hasMetadata) 10 else 0)
        ```
     e. Если `score > maxScore`:
        - `maxScore = score`
        - `bestController = controller`
  4. Вернуть `bestController`.

- **Ошибки:**
  - Исключений не бросает. Любые системные `DeadObjectException` при обращении к свойствам контроллера внутри системного сервиса нивелируются безопасными вызовами Kotlin (`?.`). Если обращение к свойству выбросит runtime-исключение, блок расчета трактует свойство как отсутствующее (`null` / `0L`).

- **Побочные эффекты:**
  - Побочных эффектов нет. Чистая функция ранжирования (I/O, логирование и мутации отсутствуют).

- **Граничные случаи:**
  1. `controllers = emptyList()` $\implies$ возвращает `null`.
  2. Все контроллеры имеют `playbackState == null` и `metadata == null` $\implies$ у всех `score = 0`, возвращается первый элемент списка.
  3. Несколько контроллеров находятся в состоянии `STATE_PLAYING`: выбор производится по наличию `ACTION_PLAY/ACTION_PAUSE`, затем по `ACTION_SEEK_TO` (в Telegram `telegramAudioPlayer` с `actions = 822` строго побеждает `MediaSessionHelper` с `actions = 567`), затем по наличию метаданных.
  4. Ни один контроллер не играет (`STATE_PAUSED` / `STATE_STOPPED`): выбор производится по поддержке действий управления и метаданных.

- **Примеры:**
  1. *Сессии Telegram при активном воспроизведении:*
     - $c_1$ (`MediaSessionHelper/42`): `state = STATE_PLAYING`, `actions = 567`, `metadata = null`. Score = $10000 + 1000 + 100 + 0 + 0 = 11100$.
     - $c_2$ (`telegramAudioPlayer/41`): `state = STATE_PLAYING`, `actions = 822`, `metadata = non-null`. Score = $10000 + 1000 + 100 + 50 + 10 = 11160$.
     - $c_3$ (`MediaSessionHelper/16`): `state = null`, `actions = 0`, `metadata = null`. Score = $0$.
     - **Выход:** $c_2$ (`telegramAudioPlayer/41`).
  2. *Одиночный контроллер Яндекс.Музыки:*
     - $c_1$: `state = STATE_PLAYING`, `actions = 519`, `metadata = non-null`. Score = $11160$.
     - **Выход:** $c_1$.
  3. *Пустой список контроллеров:*
     - `controllers = emptyList()` $\implies$ **Выход:** `null`.

- **Сложность / ограничения:**
  - Временная сложность: $\mathcal{O}(N)$, где $N$ — количество контроллеров (в Android обычно $N \in [1, 5]$).
  - Пространственная сложность: $\mathcal{O}(1)$ дополнительной памяти (аллокаций нет).

---

#### 2. Функция `togglePlayPause(): Boolean`

- **Сигнатура:**
  ```kotlin
  fun togglePlayPause(): Boolean
  ```
  *(Размещается в `MediaSessionCollector.Companion`).*

- **Предусловия:**
  1. Вызывается из UI-потока или фонового корутин-скоупа при нажатии кнопки Play/Pause.
  2. Состояние кэша контроллеров и/или `sessionManager` актуализировано.

- **Постусловия:**
  1. Возвращает `true` тогда и только тогда, когда команда успешно отправлена в целевой плеер (через `transportControls` либо через резервный `sendMediaButtonFallback`).
  2. Возвращает `false`, если нет доступных контроллеров или все попытки отправки IPC завершились сбоем.
  3. `LivePlaybackSnapshot` в `_livePlaybackFlow` оптимистично обновлен значением `nextPlaying = !isCurrentlyPlaying` (если snapshot не `null`).
  4. Команда отправляется **строго в один** контроллер, выбранный через `resolveTargetController`. Рассылка по списку контроллеров категорически запрещена.
  5. При успешном выполнении `transportControls.pause()` / `transportControls.play()` метод `sendMediaButtonFallback()` **не вызывается**.
  6. При операции паузы (`nextPlaying == false`) отправка события `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE` **категорически запрещена**.

- **Пошаговое поведение:**
  1. Получить текущий снимок: `val snapshot = _livePlaybackFlow.value`.
  2. Определить целевой пакет: `val targetPackage = snapshot?.packageName`.
  3. Получить список контроллеров: `val controllers = getControllers(targetPackage)`.
  4. Если `controllers.isEmpty()`:
     - Залогировать предупреждение: `Timber.tag(TAG).w("togglePlayPause: no active controllers found")`.
     - Вернуть `false`.
  5. Выбрать целевой контроллер через функцию скоринга:
     `val targetController = resolveTargetController(controllers) ?: return false`
  6. Вычислить текущий статус воспроизведения:
     ```kotlin
     val isCurrentlyPlaying = (targetController.playbackState?.state == PlaybackState.STATE_PLAYING) ||
                              (snapshot?.isPlaying == true)
     ```
  7. Вычислить целевой статус:
     `val nextPlaying = !isCurrentlyPlaying`
  8. Выполнить оптимистичное обновление состояния UI (Zero-Latency UI Feedback):
     a. Получить текущую позицию трека:
        `val currentPos = snapshot?.currentPositionMs() ?: targetController.playbackState?.position ?: 0L`
     b. Если `snapshot != null`:
        ```kotlin
        _livePlaybackFlow.value = snapshot.copy(
            isPlaying = nextPlaying,
            basePositionMs = currentPos,
            lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
        )
        ```
     c. Синхронизировать фоновый heartbeat:
        `instance?.syncHeartbeat(nextPlaying, targetController.packageName)`
  9. Отправить команду первого эшелона в `targetController.transportControls`:
     ```kotlin
     var transportSuccess = false
     try {
         if (isCurrentlyPlaying) {
             targetController.transportControls.pause()
         } else {
             targetController.transportControls.play()
         }
         transportSuccess = true
     } catch (e: Exception) {
         Timber.tag(TAG).w(e, "transportControls failed on %s, trying fail-safe fallback", targetController.packageName)
     }
     ```
  10. Иерархия Fail-Safe:
      - Если `transportSuccess == true` $\implies$ вернуть `true` (**фоллбэк НЕ вызывается**).
      - Если `transportSuccess == false` $\implies$ вызвать метод второго эшелона:
        `return sendMediaButtonFallback(targetController, isCurrentlyPlaying)`

- **Ошибки:**
  - Исключения IPC (`RemoteException`, `SecurityException`, `DeadObjectException`) перехватываются внутри блоков `try-catch`, логируются в `Timber.w` / `Timber.e` и не просачиваются вызывающему коду.

- **Побочные эффекты:**
  - Мутация `_livePlaybackFlow.value`.
  - Вызов `syncHeartbeat` экземпляра `MediaSessionCollector`.
  - Отправка Binder IPC вызовов в удаленный сервис медиаплеера.
  - Логирование событий в `Timber`.

- **Граничные случаи:**
  1. `snapshot == null`, но контроллеры присутствуют: `isCurrentlyPlaying` определяется строго по `targetController.playbackState?.state == PlaybackState.STATE_PLAYING`. Команда отправляется успешно, snapshot не перезаписывается.
  2. Контроллеры отсутствуют вообще: возвращает `false`, UI не ломается.
  3. Плеер завис и бросает исключение на `transportControls.pause()`: управление передается в `sendMediaButtonFallback`, где отправляется аппаратный код `KEYCODE_MEDIA_PAUSE`.
  4. Сессия Telegram имеет два контроллера: выбирается `telegramAudioPlayer`, `pause()` отправляется только ему. Второй контроллер `MediaSessionHelper` не затрагивается, что предотвращает состояние гонки (Race Condition).

- **Примеры:**
  1. *Пользователь нажимает Паузу в Telegram (играет трек):*
     - Вход: `snapshot.isPlaying = true`, `controllers = [MediaSessionHelper, telegramAudioPlayer]`.
     - Шаг 5: Выбран `telegramAudioPlayer`.
     - Шаг 6: `isCurrentlyPlaying = true`, `nextPlaying = false`.
     - Шаг 8: `_livePlaybackFlow.value.isPlaying` становится `false`.
     - Шаг 9: Вызов `telegramAudioPlayer.transportControls.pause()`. Вызов успешен (`transportSuccess = true`).
     - Шаг 10: Фоллбэк не вызывается.
     - **Выход:** `true`.
  2. *Пользователь нажимает Play (плеер на паузе):*
     - Вход: `snapshot.isPlaying = false`, `targetController.state = STATE_PAUSED`.
     - Шаг 6: `isCurrentlyPlaying = false`, `nextPlaying = true`.
     - Шаг 8: `_livePlaybackFlow.value.isPlaying` становится `true`.
     - Шаг 9: Вызов `targetController.transportControls.play()`. Вызов успешен.
     - **Выход:** `true`.
  3. *Вызов при отсутствии активных плееров:*
     - Вход: `controllers = emptyList()`.
     - Шаг 4: Контроллеров нет.
     - **Выход:** `false`.
  4. *Сбой `transportControls` с переходом на безопасный фоллбэк:*
     - Вход: `isCurrentlyPlaying = true`, `targetController.transportControls.pause()` бросает `RemoteException`.
     - Шаг 9: `transportSuccess = false`.
     - Шаг 10: Вызов `sendMediaButtonFallback(targetController, true)`.
     - Отправка `KeyEvent.KEYCODE_MEDIA_PAUSE`.
     - **Выход:** `true`.

---

### Внутренние функции

#### 3. Функция `sendMediaButtonFallback(controller: MediaController, wasPlaying: Boolean): Boolean`

- **Сигнатура:**
  ```kotlin
  internal fun sendMediaButtonFallback(controller: MediaController, wasPlaying: Boolean): Boolean
  ```
  *(Размещается в `MediaSessionCollector.Companion`).*

- **Предусловия:**
  1. `controller != null`.
  2. `wasPlaying: Boolean` указывает состояние ДО попытки переключения (`true` = плеер играл, требуется ПАУЗА; `false` = плеер стоял, требуется ВОСПРОИЗВЕДЕНИЕ).

- **Постусловия:**
  1. Если `wasPlaying == true` (намерение: ПАУЗА):
     - Допустимы **исключительно** идемпотентные коды остановки:
       - Основной: `KeyEvent.KEYCODE_MEDIA_PAUSE`.
       - Резервный (если `dispatchMediaButtonEvent` вернул `false`): `KeyEvent.KEYCODE_MEDIA_STOP`.
     - **ИНВАРИАНТ БЕЗОПАСНОСТИ:** Отправка `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE` при `wasPlaying == true` **СТРОГО ЗАПРЕЩЕНА** ни при каких обстоятельствах.
  2. Если `wasPlaying == false` (намерение: ВОСПРОИЗВЕДЕНИЕ):
     - Основной: `KeyEvent.KEYCODE_MEDIA_PLAY`.
     - Резервный (если `dispatchMediaButtonEvent` вернул `false`): `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE`.
  3. Для каждого передаваемого `keyCode` обязательно генерируется пара событий: `KeyEvent.ACTION_DOWN` и `KeyEvent.ACTION_UP`.
  4. Метод возвращает `true`, если хотя бы одно событие обработано (`handled == true`) или безопасно отправлено; возвращает `false` только при фатальном исключении.

- **Пошаговое поведение:**
  1. Обернуть исполнение в блок `try-catch (e: Exception)`.
  2. Если `wasPlaying == true`:
     a. Сформировать события основного кода:
        `val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)`
        `val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)`
     b. Отправить события:
        `val downHandled = controller.dispatchMediaButtonEvent(downEvent)`
        `val upHandled = controller.dispatchMediaButtonEvent(upEvent)`
        `val directHandled = downHandled || upHandled`
     c. Если `!directHandled`:
        - Отправить вторичный резерв `KEYCODE_MEDIA_STOP`:
          `val stopDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP)`
          `val stopUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP)`
          `controller.dispatchMediaButtonEvent(stopDown) || controller.dispatchMediaButtonEvent(stopUp)`
     d. Иначе вернуть `true`.
  3. Если `wasPlaying == false`:
     a. Сформировать события основного кода:
        `val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)`
        `val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)`
     b. Отправить события:
        `val downHandled = controller.dispatchMediaButtonEvent(downEvent)`
        `val upHandled = controller.dispatchMediaButtonEvent(upEvent)`
        `val directHandled = downHandled || upHandled`
     c. Если `!directHandled`:
        - Отправить вторичный резерв `KEYCODE_MEDIA_PLAY_PAUSE`:
          `val toggleDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)`
          `val toggleUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)`
          `controller.dispatchMediaButtonEvent(toggleDown) || controller.dispatchMediaButtonEvent(toggleUp)`
     d. Иначе вернуть `true`.
  4. При возникновении `Exception`:
     - Залогировать: `Timber.tag(TAG).e(e, "dispatchMediaButtonEvent failed completely")`.
     - Вернуть `false`.

- **Ошибки:**
  - Любые исключения перехватываются, логируются и возвращают `false`.

- **Побочные эффекты:**
  - Отправка `KeyEvent` через IPC в системный `MediaSession`.

- **Граничные случаи:**
  - Контроллер отклоняет `KEYCODE_MEDIA_PAUSE` (`directHandled == false`) при попытке паузы $\implies$ отправляется `KEYCODE_MEDIA_STOP`, но ни в коем случае не `PLAY_PAUSE`.
  - Удаленный плеер завершил процесс во время IPC $\implies$ ловится `Exception`, возврат `false`.

- **Примеры:**
  1. Вход: `controller` от Telegram, `wasPlaying = true` $\implies$ отправка `KEYCODE_MEDIA_PAUSE`. Выход: `true`.
  2. Вход: `controller` от устаревшего плеера (не принял `KEYCODE_MEDIA_PAUSE`), `wasPlaying = true` $\implies$ отправка `KEYCODE_MEDIA_STOP`. Выход: `true`.
  3. Вход: `controller`, `wasPlaying = false` $\implies$ отправка `KEYCODE_MEDIA_PLAY`. Выход: `true`.

---

### Зависимости

- **Межзонный контракт:** [.sdd/contracts/ingress__core.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/ingress__core.md) (`FROZEN v1`).
- **Android Framework:**
  - `android.media.session.MediaController`
  - `android.media.session.PlaybackState`
  - `android.view.KeyEvent`
- **Внешние библиотеки:**
  - `com.jakewharton.timber:timber:5.0.1`

---

### Вне скоупа

1. Модификация методов перемотки `seekTo(Long)` и `seekRelative(Long)`.
2. Изменение структуры кэша `lastTrackMap`, дебаунса `MediaDebounceFilter` или базы данных Room.
3. Изменение UI компонентов `NowPlayingScreen.kt` или контрактов Compose.
4. Парсинг текстовых уведомлений в `NotificationListener.kt`.
