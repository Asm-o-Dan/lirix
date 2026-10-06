# Карточка Задачи: TASK-QA-09

- **ID**: `TASK-QA-09`
- **Зона**: `qa`
- **Зависит от**: `TASK-BUG-09A`, `TASK-BUG-09B`
- **Целевой файл**: `app/src/test/java/com/lirix/app/MusicDatabaseTest.kt`
- **Исполнитель**: QA Engineer
- **Статус**: READY

---

## 1. Контекст и Описание Задачи
Для фиксации нового контракта жизненного цикла сессий и проверки предотвращения накрутки `playCount`:
1. В `MusicDatabaseTest.kt` в тесте `testRecordPlaybackSignalSessionCollapsing` обновить устаревшее утверждение (строка 241):
   было: `assertTrue("Session must be marked completed on PAUSED", pausedSession.isCompleted)`
   стало: `assertFalse("Session must NOT be marked completed on PAUSED", pausedSession.isCompleted)`
2. Добавить новый тест `testPauseDoesNotIncrementPlayCountAfterCooldown()`:
   - Трек запускается в состоянии PLAYING (playCount становится 1).
   - Трек ставится на паузу PAUSED на 65 секунд (кулдаун 60с прошел).
   - Трек возобновляется в состоянии PLAYING.
   - Проверяется: `assertEquals(1, updatedTrack.playCount)` (счетчик НЕ накручен!).
3. Добавить unit-тест `testResolveSeekControllerPrioritizesSeekAction()`:
   - Проверяется выбор контроллера с `ACTION_SEEK_TO` среди нескольких контроллеров одного пакета.

---

## 2. Критерии Приемки (Definition of Done)
1. `./gradlew testDebugUnitTest` выполняется и проходит успешно на 100%.
