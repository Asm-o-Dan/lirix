# TASK-CON-HANDOFF-06: Машина состояний Handoff (FSM, кроссфейд 300мс, откат и тайм-аут)

## 1. Контекст и Описание задачи
В соответствии со спецификацией **`SPEC-CONNECT-01 (§4.6)`**, реализовать доменный движок бесшовной передачи воспроизведения (Handoff) между устройствами (Desktop <-> Android).
Движок управляет переходами состояний источника и цели при передаче трека, очереди, интерполированной позиции и синхронизированного караоке.

---

## 2. Архитектурные требования и FSM (§4.6)
```
IDLE ──► REQUESTED ──► (HandoffAck accepted) ──► TARGET_STARTING ──► TARGET_PLAYING ──► SOURCE_MUTED (CONTROLLER)
                     ├──► (accepted=false, suggestCast) ──► Предложить Cast ──► CASTING
                     └──► Таймаут 5 с ──► IDLE (Звук остаётся на источнике)
```

1. **Инициирование Handoff (`HandoffRequest`)**:
   - Формирует `HandoffRequest` с текущим снимком: `track: TrackRef`, `positionMs`, `positionAtMonoMs`, `queue`, `playbackRate`, `epoch`.
   - Переводит состояние источника в `HandoffState.REQUESTED`.
   - Запускает таймер ожидания ответа 5000 мс. При истечении таймера — откат в `IDLE` с ошибкой `TIMEOUT`, локальный звук не прерывается.

2. **Обработка ответа (`HandoffAck`)**:
   - Если `accepted == true`: переход в `TARGET_STARTING`.
   - Если `accepted == false`: если `suggestCast == true`, переход в `SUGGEST_CAST`, иначе откат в `IDLE` с указанием причины `HandoffFailure` (`NO_PROVIDER`, `NO_LOCAL_FILE`, `BUSY`, `USER_DECLINED`).

3. **Синхронный запуск и кроссфейд 300 мс**:
   - Источник продолжает играть локально, пока от цели не поступит `RemoteStateUpdate(isPlaying = true)`.
   - После подтверждения старта цели источник выполняет плавное приглушение звука (кроссфейд 300 мс) и переходит в режим контроллера `SOURCE_MUTED` (`isLocalPlayback = false`, отправка `MediaAction` на удалённый хост).

4. **Возврат воспроизведения (`HandoffReturn`)**:
   - Запускает обратный процесс передачи с инкрементом `epoch`.

5. **Потеря связи при управлении**:
   - При дисконнекте контроллер переходит в `RECONNECTING` (таймаут 10 с). При невозможности переподключения — оповещение `CONNECTION_LOST` с вариантом «Возобновить локально».

---

## 3. Целевые файлы реализации
- `shared/src/commonMain/kotlin/com/lirix/app/connect/handoff/HandoffStateMachine.kt`
- `shared/src/commonMain/kotlin/com/lirix/app/connect/handoff/HandoffModels.kt`
- `shared/src/commonTest/kotlin/com/lirix/app/connect/HandoffStateMachineTest.kt`

---

## 4. Критерии приёмки (DoD)
1. Реализована чистая StateFlow-машина `HandoffStateMachine` в `:shared` без прямых зависимостей от Android/AWT.
2. Поддерживаются все состояния FSM: `IDLE`, `REQUESTED`, `TARGET_STARTING`, `TARGET_PLAYING`, `SOURCE_MUTED`, `SUGGEST_CAST`, `FAILED`.
3. Реализованы тайм-ауты (5 с на ответ цели, 10 с на реконнект) с возможностью подмены диспетчера времени в тестах (`CoroutineDispatcher` / `TestScope`).
4. Написаны исчерпывающие unit-тесты (`HandoffStateMachineTest.kt`) с покрытием:
   - Успешный сценарий (Request -> Ack -> Target playing -> Muted).
   - Отказ цели с `suggestCast = true`.
   - Таймаут 5 с без ответа цели -> сохранение воспроизведения на источнике.
   - Обратный возврат `HandoffReturn`.
5. `./gradlew :shared:desktopTest` проходит успешно.
