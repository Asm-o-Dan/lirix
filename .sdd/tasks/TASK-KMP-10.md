# Задача TASK-KMP-10: Windows Desktop DeckLayout с интерактивным тонармом

- **ID задачи:** `TASK-KMP-10`
- **Роль исполнителя:** UI / Desktop Coder
- **Зона:** `media-ui`
- **Файлы:**
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ui/DesktopDeckScreen.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ui/Main.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ui/components/DesktopWindowFrame.kt`
- **Зависит от:** `TASK-KMP-09`, `TASK-KMP-08`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020), `/frontend-design` план
- **Приоритет:** HIGH

---

## 1. Цель задачи
Собрать главное двухпанельное окно приложения для Windows:
1. **Левая дека:**
   - Крупный аналоговый винил `AnalogTurntable` с обложкой трека.
   - Интерактивный кинетический тонарм-скраббер (угол наклона привязан к прогрессу трека, драг тонарма осуществляет `seek`).
   - Название, исполнитель, кнопки транспорта (Play/Pause, Prev, Next, Volume/Mute).
   - Выбор активной темы (Spotify, Yandex, Obsidian, Pastel).
2. **Правая дека:**
   - Табы: «Караоке (LRC)», «Аккорды & Табы», «Слова», «Инфо».
   - В караоке: подсветка текущей строки с плавным фокусом, клик по строке перематывает трек.
3. **Кастомный Window Frame:**
   - Темный заголовок без стандартных белых рамок Windows 98, аккуратные кнопки свернуть/развернуть/закрыть в стиле Windows 11 Fluent.

---

## 2. Критерии приемки (DoD)
- [ ] Окно запускается через `./gradlew :desktop:run`.
- [ ] При воспроизведении трека в Spotify/Яндекс Музыке на ПК винил крутится, тонарм ложится на дорожку.
- [ ] Караоке скроллится синхронно с аудиопотоком.
