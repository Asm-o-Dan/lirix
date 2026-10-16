# TASK-CON-CAST-09: Live Audio Cast Приёмник и Адаптивный Джиттер-Буфер (40-80мс, PLC/FEC, CastStatus)

## 1. Контекст и Описание задачи
В соответствии со спецификацией **`SPEC-CONNECT-01 (§4.7)`**, реализовать кроссплатформенный движок приёма Live Audio Cast в `:shared` (`com.lirix.app.connect.cast.*`).
Приёмник принимает QUIC-датаграммы сырого Opus-аудио, управляет адаптивным джиттер-буфером с целевой задержкой 40–80 мс, компенсирует потери пакетов (PLC/FEC) и каждую секунду формирует сетевой отчёт `CastStatus`.

---

## 2. Архитектурные требования (§4.7)
1. **Парсинг QUIC-датаграмм**:
   - Формат датаграммы ($\le 1200$ байт):
     - `seq`: 2 байта (`u16`, монотонный номер пакета с циклическим переполнением)
     - `timestamp48k`: 4 байта (`u32`, временная метка дискретизации 48 кГц)
     - `flags`: 1 байт (`u8`, биты: bit 0 = FEC present, bit 1 = DTX silence)
     - `opusPayload`: оставшиеся байты пакета
2. **Адаптивный джиттер-буфер (`AdaptiveJitterBuffer`)**:
   - Целевая задержка буферизации: 40–80 мс (в зависимости от измеренного сетевого джиттера).
   - Восстановление правильного порядка пакетов (`reordering`) по `seq`.
   - Обнаружение пропущенных пакетов: при отсутствии пакета к моменту воспроизведения генерация маркера потери для активации PLC (Packet Loss Concealment).
3. **Мониторинг качества (`CastMetricsCollector`)**:
   - Периодичность 1 раз в секунду:
     - Подсчёт `receivedPackets`, `lostPackets`, `jitterMs`, `receiverLatencyMs`.
     - Формирование и передача в сессию сообщения `LirixConnectMsg.CastStatus`.
4. **Интерфейс аудио-вывода (`CastAudioRenderer`)**:
   - Интерфейс `CastAudioSink` с методами `writePcm(data: ShortArray, offset: Int, size: Int)` и `setVolume(volume: Float)`.

---

## 3. Целевые файлы реализации
- `shared/src/commonMain/kotlin/com/lirix/app/connect/cast/AdaptiveJitterBuffer.kt`
- `shared/src/commonMain/kotlin/com/lirix/app/connect/cast/CastDatagramCodec.kt`
- `shared/src/commonMain/kotlin/com/lirix/app/connect/cast/CastReceiverEngine.kt`
- `shared/src/commonTest/kotlin/com/lirix/app/connect/CastReceiverEngineTest.kt`

---

## 4. Критерии приёмки (DoD)
1. Реализован кодек датаграмм `CastDatagramCodec` с round-trip тестами сериализации/десериализации.
2. `AdaptiveJitterBuffer` корректно устраняет перестановочные задержки (reordering) и дубликаты пакетов.
3. При потере пакетов генерируется флаг потери для алгоритма PLC.
4. Отчёт `CastStatus` корректно подсчитывает процент потерь и джиттер.
5. Unit-тесты в `:shared:desktopTest` проходят успешно (100% PASS).
