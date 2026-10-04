# Задача TASK-SYNC-02: Двунаправленный транспортный контроль над внешней MediaSession

- **ID задачи:** `TASK-SYNC-02`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ingress` / `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/ingestion/MediaSessionCollector.kt` (изменить)
  2. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (изменить)
  3. `app/src/test/java/com/eventengine/app/LivePlaybackSyncTest.kt` (добавить тесты)
- **Спека:** [.sdd/specs/media-ui/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/overview.md) (v1), [.sdd/contracts/ingress__core.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/ingress__core.md)
- **Приоритет:** CRITICAL (Требование пользователя: двунаправленное управление воспроизведением и перемотка по субтитрам)

---

## 1. Архитектурный Root Cause и цели задачи

### 1.1 Выявленные дефекты в текущей реализации:
1. **Задержка отклика Play/Pause:**
   При нажатии кнопки Play/Pause в `NowPlayingScreen.kt`, метод `MediaSessionCollector.togglePlayPause()` отправлял команду во внешний плеер, но **не обновлял оптимистично** `_livePlaybackFlow`. Состояние `isPlaying` зависало в прежнем значении до тех пор, пока внешний плеер (Яндекс Музыка, Spotify, VK) не пришлет асинхронный коллбэк через Android `MediaController.Callback` (задержка от 300 до 1000 мс). Пользователь визуально не видел реакции, а винил продолжал вращаться.
2. **Отсутствие кликабельности самого винила:**
   Компонент виниловой пластинки в `NowPlayingScreen.kt` не имел модификатора `.clickable`, лишая пользователя интуитивного тапа по пластинке для остановки/запуска музыки.
3. **Рывки и залипание перемотки (Seek ±10s и клик по субтитрам):**
   При вызове `seekTo`:
   - Если `MediaSessionCollector.seekTo(...)` возвращал `true`, в самом экране `NowPlayingScreen` локальные переменные `playbackPositionMs` и `localPlaybackPositionMs` не обновлялись немедленно, ожидая пока корутина интерполяции подхватит следующий кадр `withFrameMillis`.
   - Внешний плеер после вызова `controller.transportControls.seekTo(ms)` может слать устаревшие позиции в течение первых 100–200 мс, вызывая визуальный откат ползунка и субтитров назад («резиновый эффект»).
4. **Ненадежность поиска `MediaController`:**
   Если плеер был запущен в фоне до старта приложения или экран был открыт после смены трека, `activeMediaControllers[snapshot.packageName]` мог оказаться `null`. Отсутствовал динамический запрос активных сессий через `sessionManager.getActiveSessions(...)` при промахе кэша.
5. **Некоторые внешние плееры игнорируют `transportControls.pause()`:**
   Ряд плееров на Android реагируют исключительно на аппаратные/гарнитурные медиа-кнопки (`KeyEvent(ACTION_DOWN, KEYCODE_MEDIA_PLAY_PAUSE)` / `ACTION_UP`). Требуется многоуровневый fallback.

---

## 2. Спецификация изменений в `MediaSessionCollector.kt`

### 2.1 Динамическое обнаружение и удержание активного `MediaController`
Добавить метод получения контроллера с авто-дозапросом активных сессий:
```kotlin
fun getActiveController(packageName: String? = null): MediaController? {
    if (packageName != null) {
        val cached = activeMediaControllers[packageName]
        if (cached != null) return cached
    }
    
    // Динамический опрос MediaSessionManager при отсутствии в кэше
    try {
        val manager = sessionManager ?: (context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager)
        val componentName = ComponentName(context, NotificationListener::class.java)
        val sessions = manager?.getActiveSessions(componentName)
        if (sessions != null) {
            for (controller in sessions) {
                activeMediaControllers[controller.packageName] = controller
            }
            if (packageName != null) {
                val found = activeMediaControllers[packageName]
                if (found != null) return found
            }
            // Если пакет не задан или не найден, вернуть плеер с состоянием PLAYING или первый активный
            return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Could not refresh active sessions from MediaSessionManager")
    }

    return activeMediaControllers.values.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: activeMediaControllers.values.firstOrNull()
}
```

### 2.2 Надежный `togglePlayPause()` с оптимистичным UI-апдейтом и KeyEvent-фоллбеком
```kotlin
fun togglePlayPause(): Boolean {
    val snapshot = _livePlaybackFlow.value
    val controller = getActiveController(snapshot?.packageName) ?: return false

    val isCurrentlyPlaying = snapshot?.isPlaying 
        ?: (controller.playbackState?.state == PlaybackState.STATE_PLAYING)
    val nextPlaying = !isCurrentlyPlaying
    val currentPos = snapshot?.currentPositionMs() 
        ?: controller.playbackState?.position 
        ?: 0L

    // 1. ОПТИМИСТИЧНОЕ ОБНОВЛЕНИЕ SNAPSHOT (мгновенный отклик UI)
    if (snapshot != null) {
        _livePlaybackFlow.value = snapshot.copy(
            isPlaying = nextPlaying,
            basePositionMs = currentPos,
            lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
        )
    }

    // 2. ОТПРАВКА КОМАНДЫ ВО ВНЕШНИЙ ПЛЕЕР (Цепочка: transportControls -> dispatchMediaButtonEvent)
    return try {
        if (isCurrentlyPlaying) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
        true
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "transportControls play/pause failed, trying KeyEvent fallback")
        sendMediaButtonFallback(controller, isCurrentlyPlaying)
    }
}

private fun sendMediaButtonFallback(controller: MediaController, wasPlaying: Boolean): Boolean {
    return try {
        val keyCode = if (wasPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        val downHandled = controller.dispatchMediaButtonEvent(downEvent)
        val upHandled = controller.dispatchMediaButtonEvent(upEvent)
        downHandled || upHandled
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "dispatchMediaButtonEvent failed completely")
        false
    }
}
```

### 2.3 Высокоточный `seekTo(targetPositionMs: Long)`
```kotlin
fun seekTo(positionMs: Long): Boolean {
    val snapshot = _livePlaybackFlow.value
    val controller = getActiveController(snapshot?.packageName)

    val validDuration = snapshot?.durationMs ?: controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val clampedPosition = if (validDuration > 0L) {
        positionMs.coerceIn(0L, validDuration)
    } else {
        positionMs.coerceAtLeast(0L)
    }

    // 1. ОПТИМИСТИЧНОЕ ОБНОВЛЕНИЕ: замораживаем позицию в snapshot на новом месте
    if (snapshot != null) {
        _livePlaybackFlow.value = snapshot.copy(
            basePositionMs = clampedPosition,
            lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
        )
    }

    // 2. ОТПРАВКА ВО ВНЕШНИЙ ПЛЕЕР
    return try {
        controller?.transportControls?.seekTo(clampedPosition)
        true
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to seekTo %d on controller", clampedPosition)
        false
    }
}

fun seekRelative(deltaMs: Long): Boolean {
    val snapshot = _livePlaybackFlow.value
    val current = snapshot?.currentPositionMs() 
        ?: getActiveController()?.playbackState?.position 
        ?: 0L
    return seekTo(current + deltaMs)
}
```

---

## 3. Спецификация изменений в `NowPlayingScreen.kt`

### 3.1 Тап по винилу (Vinyl Tap-to-Play/Pause)
В блоке винилового диска (размер 200.dp) добавить интерактивность:
```kotlin
Box(
    modifier = Modifier
        .size(200.dp)
        .clip(CircleShape)
        .clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val newLocal = !isPlaying
            localIsPlaying = newLocal
            MediaSessionCollector.togglePlayPause()
        }
        .background(...)
        .rotate(if (isPlaying) spinAngle else 0f),
    contentAlignment = Alignment.Center
) {
    // Внутреннее наполнение винила и обложка альбома
}
```

### 3.2 Кнопка Play/Pause
Мгновенное переключение локального состояния и отправка команды:
```kotlin
IconButton(
    onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        localIsPlaying = !isPlaying
        MediaSessionCollector.togglePlayPause()
    },
    modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(AppColors.HyperViolet)
) {
    Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Play",
        tint = AppColors.AmoledBlack,
        modifier = Modifier.size(22.dp)
    )
}
```

### 3.3 Перемотка Replay10 (`-10s`) и Forward10 (`+10s`)
Мгновенный прыжок позиции в UI и отправка в аудио-сессию:
```kotlin
// Replay -10s
IconButton(
    onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val targetMs = (playbackPositionMs - 10000L).coerceAtLeast(0L)
        val audioTargetMs = (targetMs - syncOffsetMs).coerceAtLeast(0L)
        // Мгновенное оптимистичное обновление локальных координат
        playbackPositionMs = targetMs
        localPlaybackPositionMs = targetMs
        MediaSessionCollector.seekTo(audioTargetMs)
    },
    modifier = Modifier.size(36.dp)
) {
    Icon(imageVector = Icons.Default.Replay10, contentDescription = "-10s", ...)
}

// Forward +10s
IconButton(
    onClick = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val duration = currentTrack.totalDurationMs.takeIf { it > 0L } 
            ?: (livePlayback?.durationMs ?: 0L)
        val targetMs = if (duration > 0L) {
            (playbackPositionMs + 10000L).coerceAtMost(duration)
        } else {
            playbackPositionMs + 10000L
        }
        val audioTargetMs = (targetMs - syncOffsetMs).coerceAtLeast(0L)
        // Мгновенное оптимистичное обновление локальных координат
        playbackPositionMs = targetMs
        localPlaybackPositionMs = targetMs
        MediaSessionCollector.seekTo(audioTargetMs)
    },
    modifier = Modifier.size(36.dp)
) {
    Icon(imageVector = Icons.Default.Forward10, contentDescription = "+10s", ...)
}
```

### 3.4 Перемотка по клику на субтитры караоке
В вызове `HighFidelityKaraokePlayer`:
```kotlin
onSeekTo = { targetMs ->
    val audioTargetMs = (targetMs - syncOffsetMs).coerceAtLeast(0L)
    // Мгновенный перенос фокуса и тайминга в UI
    playbackPositionMs = targetMs
    localPlaybackPositionMs = targetMs
    // Перемотка реального трека в плеере
    MediaSessionCollector.seekTo(audioTargetMs)
}
```

Внутри `HighFidelityKaraokePlayer`:
- При клике на строку с таймкодом `line.timestampMs`:
  1. Вызвать `haptic.performHapticFeedback(HapticFeedbackType.LongPress)`
  2. Вызвать `onSeekTo(line.timestampMs)`
  3. Строка мгновенно становится активной (`activeIndex` вычисляется по обновленному `playbackPositionMs`), получает неоновое свечение и плавно центрируется списком через `lazyListState.animateScrollToItem(index)`.

---

## 4. План тестирования и критерии приемки (DoD)

1. **Unit-тесты в `LivePlaybackSyncTest.kt`:**
   - `testSeekTo_updatesSnapshotPositionAndTimestampOptimistically`: проверка, что после вызова `seekTo(pos)` snapshot возвращает новую позицию сразу же, без дрифта.
   - `testTogglePlayPause_invertsPlayingStateOptimistically`: проверка оптимистичной смены состояния с `true` на `false` и обратно.
   - `testSeekRelative_clampsToDurationAndZero`: проверка корректности перемотки на -10с и +10с с защитой от вылета за границы `[0, durationMs]`.
2. **Интеграционная проверка:**
   - Все 80+ тестов в `./gradlew test` проходят успешно.
   - Нажатие на Play/Pause мгновенно останавливает вращение винила и ставит внешний плеер на паузу.
   - Нажатие на винил работает аналогично кнопке Play/Pause.
   - Нажатие на `-10с` и `+10с` мгновенно сдвигает субтитры и время на таймере ровно на 10 секунд, синхронизируя трек в плеере.
   - Тап по любой строке караоке немедленно перематывает плеер на этот момент песни и подсвечивает строку.
