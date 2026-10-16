# Задача TASK-UI-PROMPTER-02: Типографика: Literata + Golos Text (Variable Fonts, Cyrillic, tnum)

- **ID задачи:** `TASK-UI-PROMPTER-02`
- **Роль исполнителя:** UI Coder
- **Зона:** `media-ui`
- **Файлы:**
  - `shared/src/commonMain/kotlin/com/lirix/shared/ui/theme/LirixTypography.kt`
  - Ресурсы шрифтов: `Literata-VariableFont_opsz,wght.ttf`, `GolosText-VariableFont_wght.ttf`
  - `shared/src/commonTest/kotlin/com/lirix/shared/ui/theme/TypographyCyrillicTest.kt`
- **Зависит от:** `TASK-UI-PROMPTER-01`
- **Основание:** [ADR-022](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-022), `RESEARCH-UI-01`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Подключить типографическую систему с оптимизацией размера бандла:
1. **Вариативные шрифты (Variable Fonts):**
   - Единые вариативные файлы шрифтов `Literata` и `Golos Text` вместо набора статических начертаний для экономии веса APK и дистрибутивов.
2. **Прямой шрифт Literata (Upright Roman) как основа:**
   - Чтение лирики и объемных текстов стихов на кириллице осуществляется прямым начертанием `Literata Medium`.
   - Курсивные начертания исключаются для сплошных абзацев во избежание ухудшения разборчивости кириллицы.
3. **Кириллическая пластика:**
   - Верификация рендеринга сложного кириллического текста (буква «ё», «й», «щ», «ф»).
4. **Табличные цифры (`tnum`):**
   - Настройка `FontFeature.tabularFigures()` для шрифта `Golos Text` при отображении таймкодов трекбара.

---

## 2. Критерии приемки (DoD)
- [ ] Шрифты подключены как единые Variable Fonts.
- [ ] Unit-тест `TypographyCyrillicTest` проверяет отображение сложного кириллического текста с буквой «ё» без артефактов и выпадения глифов.
- [ ] Секунды таймкодов имеют фиксированную ширину (отсутствует колебание верстки при отсчете времени).
