# TASK-FLT-03: Интеграция переключателя оверлея в NowPlayingScreen и проверка разрешений

**Файл:** `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt`
**Зона:** `media-ui`
**Спецификация:** `.sdd/intake/TRACK_C_FLOATING_OVERLAY.md`

## 1. Цель
Интегрировать кнопку активации оверлея в плеер `NowPlayingScreen`:
- Проверка `Settings.canDrawOverlays(context)`.
- Если разрешение не выдано — интент на `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` и информационный Snackbar.
- Если выдано — переключение работы сервиса `FloatingLyricsService.toggle(context)` с неоновой индикацией активного состояния.
