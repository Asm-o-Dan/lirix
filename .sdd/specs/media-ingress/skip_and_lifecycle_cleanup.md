# Спецификация: Разрешение Skip-контроллеров и Очистка Жизненного Цикла Сессий

## Модуль: MediaSessionCollector   Зона: media-ingress   Версия: v1   Статус: FROZEN

### 1. Назначение
1. Гарантировать надежное срабатывание жестов переключения треков (`skipToNext` и `skipToPrevious`) во внешних плеерах с несколькими сессиями (Telegram, AyuGram, VK, YouTube Music).
2. Полностью устранить утечки памяти (`Callback` leaks) и висячие зомби-сессии в `activeMediaControllers` при закрытии медиа-приложений.
3. Предотвратить вертикальное переполнение экрана в диалоге `ShareLyricsAttachDialog`.

---

### 2. Спецификация функций

#### 2.1 `resolveSkipController(controllers: List<MediaController>, isNext: Boolean): MediaController?`
- **Вход**: Список контроллеров для целевого пакета, флаг направления `isNext` (`true` для Next, `false` для Previous).
- **Выход**: Контроллер с наивысшим приоритетом либо `null`.
- **Логика скоринга**:
  - Наличие действия `PlaybackState.ACTION_SKIP_TO_NEXT` (если `isNext`) или `ACTION_SKIP_TO_PREVIOUS` (если `!isNext`): **+10000**.
  - `state == PlaybackState.STATE_PLAYING`: **+1000**.
  - Наличие `playbackState != null`: **+100**.
  - Наличие `metadata != null`: **+10**.

#### 2.2 `skipToNext()` и `skipToPrevious()`
- Получает список контроллеров для пакета.
- Ищет целевой контроллер через `resolveSkipController(controllers, isNext) ?: resolveTargetController(controllers)`.
- Вызывает `transportControls.skipToNext()` / `skipToPrevious()` с fallback на `dispatchMediaButtonEvent`.

#### 2.3 Очистка жизненного цикла сессий в `updateControllers(controllers: List<MediaController>?)`
- Если `controllers.isNullOrEmpty()`:
  - Пробегается по всем `(pkg, callback)` в `activeControllers`, вызывает `activeMediaControllers[pkg]?.unregisterCallback(callback)`.
  - Очищает `activeControllers.clear()` и `activeMediaControllers.clear()`.
  - Сбрасывает `_livePlaybackFlow.value = null` (переход в режим IDLE).
- Если `controllers` содержит элементы:
  - Формирует множество актуальных пакетов: `val currentPkgs = controllers.map { it.packageName }.toSet()`.
  - Находит удаленные пакеты: `val removedPkgs = activeMediaControllers.keys - currentPkgs`.
  - Для каждого удаленного пакета: разрегистрирует `Callback`, удаляет из `activeMediaControllers` и `activeControllers`.
  - Если текущий отображаемый `_livePlaybackFlow.value?.packageName in removedPkgs`:
    переключает снимок на `resolveTargetController(controllers)` либо сбрасывает в `null`.

#### 2.4 Безопасная высота в `ShareLyricsAttachDialog.kt`
- Для списка недавних треков `LazyColumn`:
  добавить `modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)`, чтобы список прокручивался внутри модального окна и не выталкивал кнопку «Отмена» за экран.
