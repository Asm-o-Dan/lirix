# Карточка Задачи: TASK-BUG-09B

- **ID**: `TASK-BUG-09B`
- **Зона**: `media-core`
- **Зависит от**: `SPEC-BUG-09B` (`.sdd/specs/media-core/pause_session_lifecycle.md`)
- **Целевой файл**: `app/src/main/java/com/lirix/app/feature/MusicFeatureEngine.kt`
- **Исполнитель**: Coder (Android / Media Core)
- **Статус**: READY

---

## 1. Контекст и Описание Задачи
При постановке воспроизведения на паузу (`playbackState == "PAUSED"`) в текущей логике сессия помечается как `isCompleted = true`.
Это приводит к тому, что при снятии с паузы (спустя кулдаун > 60 секунд) срабатывает проверка `isTrackReplayAfterCompletion`, и трек получает ложный инкремент `playCount += 1`.

Необходимо:
1. Исключить `"PAUSED"` из условия `isCompleted = true` при обновлении активной сессии в `recordPlaybackSignal` и `processMusicEvent`.
2. Сессия должна завершаться (`isCompleted = true`) только при `"STOPPED"` или при реальной смене трека.

---

## 2. Детальные Инструкции по Реализации

### 2.1 Изменение в `recordPlaybackSignal`
В файле `MusicFeatureEngine.kt`:
Заменить строку (примерно строка 111):
```kotlin
isCompleted = (playbackState == "PAUSED" || playbackState == "STOPPED")
```
на:
```kotlin
isCompleted = (playbackState == "STOPPED")
```

### 2.2 Изменение в `processMusicEvent`
В файле `MusicFeatureEngine.kt`:
Заменить строку (примерно строка 231):
```kotlin
isCompleted = (playbackState == "PAUSED" || playbackState == "STOPPED")
```
на:
```kotlin
isCompleted = (playbackState == "STOPPED")
```

---

## 3. Критерии Приемки (Definition of Done)
1. Код компилируется без ошибок.
2. При вызове `recordPlaybackSignal` с `playbackState = "PAUSED"` поле `isCompleted` в сохраненной сессии равно `false`.
3. При возобновлении трека после паузы через время, превышающее 60 секунд, `playCount` трека **НЕ** увеличивается.
4. Существующие тесты базы данных и фичи проходят корректно (с учетом обновления устаревшего ассерта в тесте).
