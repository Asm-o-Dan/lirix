# Задача TASK-KMP-12: Системный трей, глобальные хоткеи и Discord RPC

- **ID задачи:** `TASK-KMP-12`
- **Роль исполнителя:** Windows Systems Engineer
- **Зона:** `win-sys`
- **Файлы:**
  - `desktop/src/main/kotlin/com/lirix/app/desktop/system/TrayManager.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/system/GlobalHotkeysManager.kt` (JNA RegisterHotKey)
  - `desktop/src/main/kotlin/com/lirix/app/desktop/system/DiscordRpcManager.kt`
- **Зависит от:** `TASK-KMP-10`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020), Grill-me решения
- **Приоритет:** MEDIUM

---

## 1. Цель задачи
1. **Системный трей Windows (TrayIcon):**
   - Иконка Lirix в области уведомлений Windows.
   - Меню: Показать главное окно, Вкл/Выкл HUD караоке, Следующий трек, Пауза, Выход.
   - Сворачивание в трей при закрытии окна (настраиваемая опция).
2. **Глобальные хоткеи (Global Hotkeys via JNA):**
   - `Ctrl + Alt + H`: Переключение режима HUD (Locked / Unlocked / Скрыть).
   - `Ctrl + Alt + Space`: Play/Pause.
   - `Ctrl + Alt + Right` / `Left`: Next / Prev track.
3. **Discord Rich Presence (RPC):**
   - Показ в Discord профиле пользователя текущего трека: "Слушает [Название] — [Исполнитель] в Lirix", обложка альбома и прогресс-бар трека.

---

## 2. Критерии приемки (DoD)
- [ ] Приложение корректно сворачивается в системный трей.
- [ ] Хоткеи работают из любого приложения/игры.
- [ ] Discord RPC обновляется при смене трека и останавливается при паузе.
