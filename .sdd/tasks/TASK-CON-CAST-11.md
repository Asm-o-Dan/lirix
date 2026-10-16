# TASK-CON-CAST-11: Android Cast-источник (AudioPlaybackCapture, MediaProjection, Foreground Service)

## 1. Контекст и Описание задачи
В соответствии со спецификацией **`SPEC-CONNECT-01 (§4.7, §6)`**, реализовать на платформе Android источник системного аудио (Audio Cast Source).
Модуль захватывает медиа-звук системы через Android 10+ `AudioPlaybackCaptureConfiguration` или `MediaProjection`, кодирует фреймы аудио и отправляет их через QUIC-датаграммы `CastDatagramCodec` на удалённый компьютер / планшет.

---

## 2. Архитектурные требования (§4.7)
1. **Захват аудио (`AndroidAudioCaptureEngine`)**:
   - Использование `AudioRecord.Builder` с `AudioPlaybackCaptureConfiguration` (Android 10+, API 29+).
   - Формат захвата: 48 кГц, 16 бит PCM, 2 канала (стерео).
   - Захват фреймов по 10/20 мс (1920 байт).
2. **Foreground Service (`AudioCastForegroundService`)**:
   - Фоновая служба с типом `mediaProjection` для удержания процесса при блокировке экрана.
   - Уведомление в шторке: «Трансляция звука на <Устройство>» с кнопкой «Остановить».
3. **Обработка системного разрешения**:
   - Запуск системного диалога `MediaProjectionManager.createScreenCaptureIntent()`.
   - Показ понятного предупреждения пользователю о DRM-ограничениях (некоторые защищенные приложения могут запрещать захват звука).
4. **Отправка QUIC-датаграмм**:
   - Упаковка через `CastDatagramCodec` и отправка через `ConnectTransport.sendDatagram`.

---

## 3. Целевые файлы реализации
- `app/src/main/java/com/lirix/app/connect/cast/AndroidAudioCaptureService.kt`
- `app/src/main/java/com/lirix/app/connect/cast/AndroidCastSourceManager.kt`
- `app/src/test/java/com/lirix/app/connect/AndroidCastSourceTest.kt`

---

## 4. Критерии приёмки (DoD)
1. Реализован `AndroidAudioCaptureService` с соблюдением требований Android 14+ (`FOREGROUND_SERVICE_MEDIA_PROJECTION`).
2. Корректная обработка жизненного цикла службы и отмена трансляции по кнопке в уведомлении.
3. Unit-тесты покрывают нарезку буферов, генерацию датаграмм и обработку телеметрии `CastStatus`.
4. `./gradlew.bat :app:compileDebugKotlin` проходит со статусом **BUILD SUCCESSFUL**.
