# Задача TASK-KMP-11: Прозрачный Click-Through HUD караоке поверх окон и игр

- **ID задачи:** `TASK-KMP-11`
- **Роль исполнителя:** Windows Systems Engineer
- **Зона:** `media-ui` & `win-sys`
- **Файлы:**
  - `desktop/src/main/kotlin/com/lirix/app/desktop/hud/DesktopHudWindow.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/hud/WindowsWindowStyles.kt` (JNA User32 SetWindowLong WS_EX_TRANSPARENT, WS_EX_LAYERED, WS_EX_TOPMOST)
- **Зависит от:** `TASK-KMP-10`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020), Grill-me решения
- **Приоритет:** MEDIUM

---

## 1. Цель задачи
Реализовать компактный плавающий оверлей караоке для геймеров и мультитаскеров на Windows:
1. Окно Compose Desktop с прозрачным фоном (`transparent = true`, `undecorated = true`, `alwaysOnTop = true`).
2. Применение стилей Windows через JNA:
   - `WS_EX_LAYERED | WS_EX_TRANSPARENT` — клики мыши проходят насквозь в игру или браузер под оверлеем.
3. Горячая клавиша `Ctrl + Alt + H` (или кнопка в трее) переключает режим:
   - **Locked (Click-Through):** мышь проходит насквозь, отображаются 2-3 строки караоке с легким размытым фоном.
   - **Unlocked (Interactive):** появляется рамка и ручка перетаскивания, можно передвинуть HUD в любой угол экрана или изменить размер.
4. Позиция и размер сохраняются в конфигурации пользователя.

---

## 2. Критерии приемки (DoD)
- [ ] Оверлей висит поверх всех окон и не мешает кликам в полноэкранных окнах/играх в режиме Locked.
- [ ] В режиме Unlocked оверлей свободно перетаскивается.
- [ ] Караоке отображает предыдущую, текущую и следующую строки с акцентом текущей темы.
