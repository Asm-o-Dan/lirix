# TASK-CON-REG-05: Реестр устройств и мониторинг присутствия (Device Registry, Heartbeat, Online status)

## 1. Контекст и Описание задачи
В соответствии со спецификацией **`SPEC-CONNECT-01 (§4.4)`**, реализовать доменный реестр устройств (`DeviceRegistry`) и менеджер присутствия в локальной сети / P2P.
Компонент объединяет сопряжённые устройства из `TrustedDeviceStore` и динамический статус активности (Online / Offline / Playing), обрабатывает входящие/исходящие `DeviceHeartbeat` (раз в 10 с, таймаут 25 с) и поставляет реактивный список для UI Popover и BottomSheet.

---

## 2. Архитектурные требования
1. **DeviceRegistry**:
   - Хранит актуальный список `ConnectedDeviceInfo`:
     - `deviceId`: String / Bytes (Ed25519 public key hex)
     - `deviceName`: String
     - `deviceType`: DeviceType (WINDOWS_PC, ANDROID_PHONE, ANDROID_TABLET)
     - `isOnline`: Boolean
     - `isPlaying`: Boolean
     - `currentTrack`: TrackRef?
     - `lastSeenEpochMs`: Long
   - Источником доверенных устройств является `TrustedDeviceStore`.
2. **Мониторинг присутствия (Heartbeat)**:
   - Периодическая отправка `DeviceHeartbeat` каждые 10 секунд активным соединённым пирам.
   - Если от устройства не поступало Heartbeat / сообщений дольше 25 секунд (`HEARTBEAT_TIMEOUT_MS`), его статус переключается в `isOnline = false`.
3. **Обновление состояния сессии**:
   - При получении `RemoteStateUpdate` или `SessionSnapshot` обновляются поля `isPlaying` и `currentTrack`.

---

## 3. Целевые файлы реализации
- `shared/src/commonMain/kotlin/com/lirix/app/connect/registry/DeviceRegistry.kt`
- `shared/src/commonMain/kotlin/com/lirix/app/connect/registry/DeviceHeartbeatMonitor.kt`
- `shared/src/commonTest/kotlin/com/lirix/app/connect/DeviceRegistryTest.kt`

---

## 4. Критерии приёмки (DoD)
1. Реализован `DeviceRegistry` с реактивным `StateFlow<List<ConnectedDeviceInfo>>`.
2. Реализована логика отсечки по таймауту 25 с при отсутствии Heartbeat.
3. Unit-тесты в `:shared:desktopTest` покрывают:
   - Регистрацию устройства и обновление онлайн-статуса при получении Heartbeat.
   - Переход в offline при истечении 25 секунд.
   - Обновление метаданных трека при получении `RemoteStateUpdate`.
4. `./gradlew :shared:desktopTest` проходит успешно (0 ошибок).
