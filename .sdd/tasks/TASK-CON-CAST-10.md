# TASK-CON-CAST-10: Cast-источник Windows (WASAPI Loopback, захват звука и упаковка в QUIC датаграммы)

## 1. Контекст и Описание задачи
В соответствии со спецификацией **`SPEC-CONNECT-01 (§4.7, §6)`**, реализовать на платформе Windows Desktop источник системного аудио (Audio Cast Source).
Модуль захватывает системный стерео-звук Windows (WASAPI Loopback Capture), кодирует его или упаковывает для отправки в виде QUIC-датаграмм на подключённое устройство-приёмник.

---

## 2. Архитектурные требования
1. **WASAPI Loopback Capture**:
   - Захват звука с устройства воспроизведения по умолчанию в режиме loopback (формат 48 кГц, 16 бит или float32 стерео).
   - Низколатентный кольцевой буфер (размер фрейма 10–20 мс).
2. **Фрейминг и отправка датаграмм**:
   - Использование `CastDatagramCodec` для формирования заголовка: `seq (u16)`, `timestamp48k (u32)`, `flags (u8)`.
   - Отправка через `ConnectTransport.sendDatagram(...)`.
3. **Обработка CastStatus**:
   - Приём отчёта `CastStatus` от удалённого приёмника для адаптации битрейта и отслеживания задержки (`receiverLatencyMs`).

---

## 3. Целевые файлы реализации
- `desktop/src/main/kotlin/com/lirix/app/desktop/cast/WindowsAudioCapture.kt`
- `desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopCastSourceManager.kt`
- `desktop/src/test/kotlin/com/lirix/app/desktop/WindowsAudioCaptureTest.kt`

---

## 4. Критерии приёмки (DoD)
1. Реализован интерфейс `AudioCaptureEngine` с реализацией для Windows и dummy-fallback для не-Windows ОС.
2. Протестирована нарезка аудиопотока на фреймы по 10/20 мс и генерация датаграмм с монотонными `seq` и `timestamp48k`.
3. Сборка `:desktop:compileKotlin` и тесты `:desktop:test` проходят успешно.
