# Задача TASK-KMP-07: C# .NET 8.0 Native AOT мост к WinRT GSMTC (lirix-media-bridge.exe)

- **ID задачи:** `TASK-KMP-07`
- **Роль исполнителя:** Windows Systems Engineer
- **Зона:** `win-ingress`
- **Файлы:**
  - `native/lirix-media-bridge/LirixMediaBridge.csproj`
  - `native/lirix-media-bridge/Program.cs`
  - `native/lirix-media-bridge/GsmtcWatcher.cs`
  - `native/lirix-media-bridge/ProtocolModels.cs`
- **Зависит от:** `TASK-KMP-01`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** HIGH

---

## 1. Цель задачи
Создать автономный нативный консольный бинарник `lirix-media-bridge.exe` (~3–5 МБ) на C# .NET 8.0 Native AOT (под установленный SDK 8.0.417), осуществляющий двусторонний мост между Windows 10/11 WinRT GSMTC API и JVM-приложением Lirix через `stdin` / `stdout` (newline-delimited JSON).

### Поддерживаемые события (stdout -> Lirix):
1. `session`: активная сессия изменилась (app, title, artist, album, durationMs, positionMs, state: playing/paused, thumbnailPath).
2. `timeline`: обновление текущей позиции трека `positionMs`.
3. `closed`: все сессии плееров закрыты (переход в IDLE).

### Поддерживаемые команды (stdin <- Lirix):
1. `{"cmd": "playPause"}`
2. `{"cmd": "seek", "positionMs": 120000}`
3. `{"cmd": "skipNext"}`
4. `{"cmd": "skipPrevious"}`

---

## 2. Критерии приемки (DoD)
- [x] Проект компилируется через `dotnet publish -c Release -r win-x64 /p:PublishAot=true`.
- [x] Размер `lirix-media-bridge.exe` $\le 5$ МБ, запуск не требует установленного .NET Runtime.
- [x] Мост корректно считывает текущий трек из любого активного плеера (Spotify, Яндекс Музыка, Chrome/YouTube, Telegram).
- [x] Написан базовый юнит/интеграционный тест протокола.
