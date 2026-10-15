# Задача TASK-KMP-08: Desktop Ingress сервис и арбитраж сессий поверх моста

- **ID задачи:** `TASK-KMP-08`
- **Роль исполнителя:** Windows / Desktop Engineer
- **Зона:** `win-ingress`
- **Файлы:**
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ingress/BridgeProcessManager.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ingress/DesktopGsmtcIngressProvider.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ingress/BridgeProtocolJson.kt`
- **Зависит от:** `TASK-KMP-07`, `TASK-KMP-05`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** HIGH

---

## 1. Цель задачи
Реализовать JVM-слой управления процессом `lirix-media-bridge.exe`:
1. Автоматический запуск дочернего процесса при старте Desktop приложения.
2. Чтение событий из `stdout` в корутине (Dispatchers.IO), парсинг JSON в `IngressPlaybackEvent`.
3. Реализация интерфейса `MediaIngressProvider` (Play/Pause, Seek, SkipNext, SkipPrevious) через отправку JSON команд в `stdin` моста.
4. Автоматический рестарт процесса при неожиданном падении (watchdog с exponential backoff).
5. Корректное завершение процесса при выходе из приложения (`Process.destroy()`).

---

## 2. Критерии приемки (DoD)
- [ ] `BridgeProcessManager` гарантированно стартует и глушит бинарник моста.
- [ ] События GSMTC бесшовно транслируются в `MusicFeatureEngine` через StateFlow/SharedFlow.
- [ ] Нажатия кнопок управления в Desktop UI мгновенно вызывают команды в плеере Windows.
