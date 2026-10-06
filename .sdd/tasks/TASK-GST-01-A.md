# Задача TASK-GST-01-A: Реализация skipToNext и skipToPrevious в MediaSessionCollector

- **ID задачи:** `TASK-GST-01-A`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ingress`
- **Файл:** `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`
- **Спека:** `.sdd/specs/media-ui/gestures_spec.md#1`

## Сигнатуры
```kotlin
fun skipToNext(): Boolean
fun skipToPrevious(): Boolean
```

## Пошаговое поведение
Внутри `companion object` после метода `togglePlayPause`:
1. `skipToNext()`:
   - Получить `snapshot = _livePlaybackFlow.value`
   - Найти контроллер через `resolveTargetController(getControllers(snapshot?.packageName)) ?: return false`
   - Вызвать `controller.transportControls.skipToNext()`
   - При `Exception` вызвать резервную отправку `KeyEvent.KEYCODE_MEDIA_NEXT` (DOWN + UP) через `controller.dispatchMediaButtonEvent`
2. `skipToPrevious()`:
   - Получить `snapshot = _livePlaybackFlow.value`
   - Найти контроллер через `resolveTargetController(getControllers(snapshot?.packageName)) ?: return false`
   - Вызвать `controller.transportControls.skipToPrevious()`
   - При `Exception` вызвать резервную отправку `KeyEvent.KEYCODE_MEDIA_PREVIOUS` (DOWN + UP) через `controller.dispatchMediaButtonEvent`
