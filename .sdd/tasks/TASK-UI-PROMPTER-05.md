# Задача TASK-UI-PROMPTER-05: Сборка Teleprompter Deck (Studio, Mini, HUD) и дистрибуция

- **ID задачи:** `TASK-UI-PROMPTER-05`
- **Роль исполнителя:** UI Lead / Desktop Coder
- **Зона:** `media-ui`
- **Файлы:**
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ui/DesktopDeckScreen.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ui/components/DesktopTopBar.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ui/components/DesktopCompactWidget.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/hud/DesktopHudWindow.kt`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/Main.kt`
- **Зависит от:** `TASK-UI-PROMPTER-01`, `TASK-UI-PROMPTER-02`, `TASK-UI-PROMPTER-03`, `TASK-UI-PROMPTER-04`, `TASK-AUD-WASAPI-01`
- **Основание:** [ADR-022](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-022), `RESEARCH-UI-01`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Собрать обновлённый интерфейс «Суфлёрский Монитор» (Teleprompter Deck) во всех 3 режимах:
1. **Режим Studio ($1040\times 680\,\text{dp}$):**
   - Верхний бар: статус сессии человеческим языком (`Играет в Яндекс Музыке ▾`, `Играет в Spotify ▾`), текстовые тумблеры `Студия`, `Мини`, `Поверх окон`, кнопка `📌`.
   - Блок контекста: квадратная обложка альбома ($88\times 88\,\text{dp}$, скругление 6 dp) без винила, название, исполнитель, источник текста (`LRCLIB, синхронно` / `AmDm, аккорды`).
   - Центральный суфлёр: `DesktopTeleprompterView` с левым выравниванием, шрифтом Literata, 3 dp планкой-маркером и резервным слотом кнопки перемотки.
   - Нижний транспорт: `DesktopTransportBar` со сдвигом караоке и честным WASAPI Loopback пик-метром.
2. **Режим Mini Deck ($380\times 110\,\text{dp}$):**
   - Ультракомпактный плеер с одной строкой текста, обложкой и тремя кнопками транспорта.
3. **Режим Поверх Окон (HUD, $520\times 140\,\text{dp}$):**
   - `WS_EX_TRANSPARENT` сквозной клик, четкая контурная обводка текста 1.5 dp (`#000000`) для идеальной читаемости поверх любых видеоигр.
   - Хоткей `Ctrl+Alt+H` / `Ctrl+Alt+L`.

---

## 2. Критерии приемки (DoD)
- [ ] Окно плавно трансформируется между режимами Studio, Mini и HUD.
- [ ] `./gradlew :desktop:createDistributable` компилирует рабочий `.exe`.
- [ ] Никаких регрессий в мобильной сборке `:app:assembleRelease`.
