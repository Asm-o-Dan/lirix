# Intake: Внедрение системы жестов управления в NowPlayingScreen

## 1. Контекст и согласованные требования (/grill-me)
Пользователь утвердил следующий набор жестов:
1. **Жесты переключения режимов текста (Основной сценарий):**
   - Замена статического `Crossfade` контента на `HorizontalPager` с 4 страницами (`NowPlayingMode`: Караоке, Текст, Аккорды, Заметки).
   - Двусторонняя синхронизация: выбор плашки в сегментированном переключателе анимирует пейджер (`animateScrollToPage`), а свайп пейджера переключает активную плашку с тактильным откликом `HapticFeedbackType.TextHandleMove`.
2. **Жесты управления треками и воспроизведением (Вспомогательный сценарий):**
   - **Свайп по винилу / обложке:**
     - Горизонтальный свайп влево $\to$ следующий трек (`skipToNext()`).
     - Горизонтальный свайп вправо $\to$ предыдущий трек (`skipToPrevious()`).
   - **Двойной тап по карточке текста:**
     - Быстрый дабл-тап переключает Play/Pause (`MediaSessionCollector.togglePlayPause()`) с виброоткликом.

## 2. Границы и зоны (Scope)
- **Зона `media-ingress`:**
  - Добавление безопасных методов `skipToNext()` и `skipToPrevious()` в `MediaSessionCollector.kt` с использованием `resolveTargetController()` и fallback keycodes (`KEYCODE_MEDIA_NEXT` / `KEYCODE_MEDIA_PREVIOUS`).
- **Зона `media-ui`:**
  - Добавление `HorizontalPager` и `rememberPagerState()` в `NowPlayingScreen.kt`.
  - Добавление жестов свайпа по `AnalogTurntable` и дабл-тапа по контейнеру текста.

## 3. Definition of Done (DoD)
- [ ] В `MediaSessionCollector` реализованы `skipToNext()` и `skipToPrevious()` с fail-safe.
- [ ] В `NowPlayingScreen` режимы переключаются плавным свайпом через `HorizontalPager`.
- [ ] Свайп по винилу переключает треки вперед/назад в плеере.
- [ ] Все юнит-тесты компилируются и проходят без ошибок (`./gradlew testDebugUnitTest`).
- [ ] Сборка Release APK успешна (`./gradlew assembleRelease`).
