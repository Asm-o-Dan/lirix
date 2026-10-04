# Отчет инженера по развертыванию и сборке (DevOps / Android Build & Release Specialist)

## 1. Сводка выполнения: Track C — Плавающий оверлей караоке (Floating Lyrics Overlay)
- **Проект**: Lirix / Android Event Engine (`com.eventengine.app.debug`)
- **Статус сборки и тестов**: **УСПЕШНО СОБРАН APK И ВСЕ ЮНИТ-ТЕСТЫ ЗЕЛЕНЫЕ (BUILD SUCCESSFUL)**
- **Реализованные задачи**:
  - `TASK-FLT-01`: Системный сервис `FloatingLyricsService` на `SYSTEM_ALERT_WINDOW` с защитой Foreground Service (`mediaPlayback`).
  - `TASK-FLT-02`: Плавающий виджет `FloatingLyricsOverlayView` (Dynamic Island pill + 320dp Mini-Player) с кинетическим перетаскиванием и магнитным прилипанием (`FloatingSnapCalculator`).
  - `TASK-FLT-03`: Переключатель оверлея в `NowPlayingScreen.kt` с системной проверкой и запросом разрешений.
- **Целевое устройство**: Xiaomi Poco M7 (ID: `2440cbe2`, HyperOS 2 / Android 15, API 35)
- **Дата и время**: 2026-10-04 15:03:00

---

## 2. Сборка и деплой

### 2.1 Компиляция
- **Команда**:
  ```powershell
  $env:JAVA_HOME = "C:\Users\DaniilTuT\.jdks\temurin-21"
  $env:ANDROID_HOME = "C:\Users\DaniilTuT\AppData\Local\Android\Sdk"
  .\gradlew.bat assembleDebug
  ```
- **Результат**: `BUILD SUCCESSFUL in 40s` (37 actionable tasks: 6 executed, 31 up-to-date).

### 2.2 Установка на Poco M7
- **Команда**:
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 2440cbe2 install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- **Результат**: `Performing Streamed Install -> Success`.

### 2.3 Авторизация и запуск
- Выдано разрешение `NotificationListener`:
  ```powershell
  adb -s 2440cbe2 shell cmd notification allow_listener com.eventengine.app.debug/com.eventengine.app.ingestion.NotificationListener 0
  ```
- Выдано разрешение `SYSTEM_ALERT_WINDOW` для оверлея:
  ```powershell
  adb -s 2440cbe2 shell appops set com.eventengine.app.debug SYSTEM_ALERT_WINDOW allow
  ```
- Перезапущена `MainActivity`.
- **Новый активный PID**: **`25628`**.

---

## 3. Живой лог Logcat
```text
09-25 11:53:39.890 25628 25628 I EventEngineApp: Android Event Engine application initialized.
09-25 11:53:39.924 25628 25628 I MediaSessionCollector: MediaSessionCollector started listening successfully.
09-25 11:53:40.592 25628 25812 I MediaSessionCollector: Recorded Media Event: [com.shaiban.audioplayer.mplayer] Михаил Злобин - Глава 18 (UPDATE)
```
- Крашей, исключений и ошибок нет.
- Сервисы приложения функционируют в штатном режиме.
