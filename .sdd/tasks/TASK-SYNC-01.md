# TASK: TASK-SYNC-01 — Реализация интерактивной студии разметки караоке LRC Tap-to-Sync

## Описание
Создать модуль `LrcSyncEngine.kt` и диалог `LrcTapSyncStudioDialog.kt` в Jetpack Compose, связав его с вкладкой `Текст` в `NowPlayingScreen.kt`.

## Статус: DONE ✅
- [x] Создан `app/src/main/java/com/eventengine/app/feature/lyrics/LrcSyncEngine.kt` с валидацией монотонности, парсингом и `[mm:ss.xx]`.
- [x] Написаны и успешно пройдены unit-тесты `app/src/test/java/com/eventengine/app/LrcSyncEngineTest.kt` (100% test pass).
- [x] Реализован Compose-компонент `app/src/main/java/com/eventengine/app/ui/components/LrcTapSyncStudioDialog.kt` (Obsidian Pulse, телесуфлер с фокусной плашкой, управление плеером, Undo, кнопка Тап, предпросмотр).
- [x] Интеграция в `NowPlayingScreen.kt` с кнопкой вызова студии, Dual-Write сохранением в Room (`lyricsDao` + `musicDao`) и автопереключением в караоке.
- [x] Сборка `assembleDebug`, тесты `testDebugUnitTest` зеленые, проведено визуальное тестирование на Xiaomi POCO M7 (`2440cbe2`).
