# TASK-BUG-FLT-04: Восстановление и сквозная интеграция плавающего оверлея караоке (Floating Lyrics Overlay) на Android

## 1. Контекст и Проблематика
Пользователь сообщил о критическом дефекте: «не работает оверлэй на телефоне».
В ходе архитектурного анализа кодовой базы выявлены следующие коренные причины:
1. **Разрыв UI-связи в `NowPlayingScreen.kt`**:
   - В компоненте `NowPlayingOverflowMenu.kt` реализован пункт «Плавающий оверлей» с параметрами `isOverlayActive` и `onToggleOverlay()`.
   - Однако в `NowPlayingScreen.kt` (строки 1417–1442) компонент `NowPlayingOverflowMenu` вызывается **без** передачи этих параметров. Нажатие на пункт меню вызывало пустую лямбду `{}` по умолчанию. Метод `FloatingLyricsService.start(context)` / `toggle(context)` физически никогда не вызывался из UI.
2. **Отсутствие UX обработки разрешения `SYSTEM_ALERT_WINDOW`**:
   - На Android 6+ показ поверх окон требует системного разрешения. При отсутствии разрешения сервис тихо завершался через `stopSelf()`, а пользователь не получал обратной связи (диалога с объяснением и кнопкой перехода в настройки).
3. **Совместимость ComposeView в WindowManager**:
   - В `FloatingLyricsOverlayView.kt` для `ComposeView` необходимо явно зафиксировать `ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool` для предотвращения сбоев Recomposer при пересоздании окон.
4. **Безопасность Foreground Service на Android 13/14+**:
   - Защита вызова `startForeground()` от `SecurityException` при отсутствии разрешения `POST_NOTIFICATIONS`.

---

## 2. Затронутые файлы
- `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`
- `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt`
- `app/src/main/java/com/lirix/app/ui/components/FloatingLyricsOverlayView.kt`
- `app/src/main/java/com/lirix/app/service/FloatingLyricsService.kt`
- `app/src/test/java/com/lirix/app/service/FloatingLyricsServiceTest.kt`

---

## 3. Критерии приёмки (DoD)
1. **Сквозное подключение в `NowPlayingScreen.kt`**:
   - Подписка на `FloatingLyricsService.isRunning.collectAsState()`.
   - Передача `isOverlayActive` и рабочего `onToggleOverlay` в `NowPlayingOverflowMenu`.
   - Отображение активного статуса оверлея (галочка/зеленый акцент) в меню.
2. **Диалог запроса разрешения (`OverlayPermissionDialog`)**:
   - Если `!Settings.canDrawOverlays(context)`, при нажатии на «Плавающий оверлей» отображается информативный Material 3 диалог:
     - Заголовок: «Отображение поверх других приложений».
     - Текст: «Для показа бегущих строк караоке поверх плееров и других приложений требуется предоставить системное разрешение».
     - Кнопки: «Настройки системы» (переход в `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`) и «Отмена».
3. **Безотказный запуск и управление сервисом**:
   - `FloatingLyricsService.toggle(context)` корректно запускает или останавливает оверлей.
   - Показ Snackbar с уведомлением: «Плавающий оверлей включён» / «Плавающий оверлей выключен».
   - Защита от крашей на Android 13/14 (`try-catch` вокруг `startForeground`).
   - `composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)`.
4. **Тесты и сборка**:
   - `./gradlew.bat :app:compileDebugKotlin` проходит со статусом **BUILD SUCCESSFUL**.
   - Unit-тесты `testDebugUnitTest` проходят со 100% успехом.
