# Задача TASK-UI-PROMPTER-01: Палитры Graphite & Chalk и Paper & Ink с Contrast Clamp

- **ID задачи:** `TASK-UI-PROMPTER-01`
- **Роль исполнителя:** UI / Design System Coder
- **Зона:** `media-ui`
- **Файлы:**
  - `shared/src/commonMain/kotlin/com/lirix/shared/ui/theme/LirixThemeTokens.kt`
  - `shared/src/commonMain/kotlin/com/lirix/shared/ui/theme/ContrastClampHelper.kt`
  - `shared/src/commonTest/kotlin/com/lirix/shared/ui/theme/ThemeContrastTest.kt`
- **Зависит от:** [ADR-022](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-022), `RESEARCH-UI-01`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Реализовать выверенную цветовую систему суфлёра (общую для Desktop и Android):
1. **Тёмная тема (Graphite & Chalk):**
   - Базовый холст: `Graphite` (`#1B1E24`)
   - Поверхность карточек: `Slate` (`#242830`)
   - Разделители (1 dp): `Hairline` (`#3A404B`)
   - Активный текст: `Chalk` (`#F2EFE9`, контраст 13.4:1 на Graphite)
   - Неактивный текст/таймкоды: `Mist` (`#8B93A1`, контраст 5.4:1 на Graphite)
   - Клиппинг звука: `Clip` (`#E5484D`)
   - Запасной акцент: `AccentFallbackDark` (`#7FA6F0`, контраст 6.6:1 на Graphite)
2. **Светлая тема (Paper & Ink) — подключается СРАЗУ:**
   - Базовый холст: `Parchment` (`#F5F2EB`, тёплая матовая бумага)
   - Поверхность карточек/транспорта: `Vellum` (`#EAE6DC`)
   - Разделители (1 dp): `HairlineLight` (`#D6D0C2`)
   - Активный текст: `Ink` (`#181B20`, контраст 13.8:1 на Parchment)
   - Неактивный текст/грифель: `Pencil` (`#5C6370` — строго единое значение во всех файлах; контраст 5.1:1 на Parchment и 4.8:1 на Vellum)
   - Клиппинг звука: `ClipLight` (`#C92A2A`)
   - Запасной акцент: `AccentFallbackLight` (`#29549E` — выверенный оттенок, гарантирующий честный контраст 5.9:1 на Vellum и 6.5:1 на Parchment)
3. **Изоляция динамического акцента (Contrast Clamp):**
   - Динамический `Accent` извлекается из обложки трека **ТОЛЬКО на экране плеера**.
   - В Медиатеке, Итогах и списках всегда используется фиксированный `AccentFallback` (`#7FA6F0` / `#29549E`), исключая перекрашивание интерфейса при смене трека в фоне.
   - Алгоритм HSL `clampAccentContrast(color, isDark)` нормализует цвет обложки для гарантии контраста WCAG AA ($\ge 4.5:1$) на `Graphite` и `Parchment`.

---

## 2. Критерии приемки (DoD)
- [ ] Токены тёмной и светлой палитр объявлены в `:shared`. Значение `Pencil` строго `#5C6370`.
- [ ] Написан Unit-тест `ThemeContrastTest`, математически проверяющий WCAG контраст всех пар:
  - `Chalk` на `Graphite` ($\ge 7.0:1$)
  - `Mist` на `Graphite` ($\ge 4.5:1$) и `Slate` ($\ge 4.5:1$)
  - `AccentFallbackDark` (`#7FA6F0`) на `Graphite` ($\ge 4.5:1$)
  - `Ink` на `Parchment` ($\ge 7.0:1$)
  - `Pencil` (`#5C6370`) на `Parchment` ($\ge 4.5:1$) и `Vellum` ($\ge 4.5:1$)
  - `AccentFallbackLight` (`#29549E`) на `Parchment` ($\ge 4.5:1$) и `Vellum` ($\ge 4.5:1$)
  - Динамический акцент после `clampAccentContrast` на `Graphite` и `Parchment` ($\ge 4.5:1$)
- [ ] Сборка `./gradlew :shared:allTests` проходит с кодом 0.
