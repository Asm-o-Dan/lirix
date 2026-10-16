# TASK-CON-PROTO-03: CBOR/Envelope-протокол LirixConnectMsg, версионирование и ConnectSessionManager

## 1. Контекст и Цель
Реализовать уровень протокола сообщений Lirix Connect в модуле `:shared` по стандарту **SPEC-CONNECT-01 (§4.1, §4.3, §4.4)**:
1. Сериализуемый конверт `Envelope` с монотонным счётчиком `msgId`, идентификатором сессии `sessionId`, поколением владения `epoch` и меткой времени `sentAtMonoMs`.
2. Полная иерархия полиморфных сообщений `LirixConnectMsg`:
   - Рукопожатие: `Hello`, `Welcome`, `Reject`, `Error`, `Bye`.
   - Сопряжение: `PairRequest`, `PairResult`.
   - Время и связность: `Ping`, `Pong`, `DeviceHeartbeat`.
   - Handoff: `HandoffRequest`, `HandoffAck`, `HandoffReturn`.
   - Пульт ДУ: `RemoteMediaAction`, `RemoteStateUpdate`.
   - Audio Cast: `SetAudioCast`, `CastStatus`.
3. Кодек сообщений `ConnectMessageCodec`: упаковка `Envelope` в конверт с u32 LE префиксом длины (максимум 64 КБ) с защитой от переполнения буфера.
4. Менеджер сессии `ConnectSessionManager`:
   - Роли: `DeviceRole.PLAYER` (источник истины воспроизведения) и `DeviceRole.CONTROLLER` (пульт ДУ).
   - Арбитраж `epoch`: приём команд только с `epoch >= currentEpoch`; отбрасывание устаревших с ошибкой `STALE_EPOCH`.
   - Инкремент `epoch` при каждом успешном handoff или передаче владения.

---

## 2. Затрагиваемые файлы и целевые пакеты
Модуль `shared/src/commonMain/kotlin/com/lirix/app/connect/`:
1. `proto/Envelope.kt` — класс конверта и `DeviceRole`.
2. `proto/LirixConnectMsg.kt` — запечатанный интерфейс сообщений и модели (`Capabilities`, `TrackRef`, `LyricsRef`, `QueueSnapshot`, `SessionSnapshot`).
3. `proto/ConnectMessageCodec.kt` — сериализация/десериализация с u32 LE длиной.
4. `session/ConnectSessionManager.kt` — управление сессией, сменой ролей и валидацией `epoch`.
5. Тесты: `shared/src/commonTest/kotlin/com/lirix/app/connect/ConnectProtoTest.kt`.

---

## 3. Критерии приёмки (DoD)
1. **Полнота моделей**: все сообщения из §4.4 аннотированы `@Serializable` и компилируются в Kotlin Multiplatform (`commonMain`).
2. **Фрейминг**: `ConnectMessageCodec.encode` возвращает `[4 байта u32 LE длины] + [payload]`. Сообщения > 64 КБ вызывают `IllegalArgumentException`.
3. **Round-trip десериализация**: тест кодирования/декодирования каждого типа сообщений (особенно `HandoffRequest` и `RemoteStateUpdate`) без потери полей.
4. **Валидация epoch в ConnectSessionManager**:
   - `handleRemoteAction(action)` отклоняет команды с `epoch < currentEpoch` с `Error(ErrorCode.STALE_EPOCH)`.
   - При `epoch >= currentEpoch` команда принимается, а `epoch` обновляется.
5. **Тестирование (TDD)**: все сценарии покрыты в `ConnectProtoTest.kt` и успешно проходят через `./gradlew :shared:desktopTest`.
