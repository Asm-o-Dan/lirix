# Задача TASK-UI-MOB-01: Внедрение палитр Graphite/Paper и шрифтов Literata/Golos на Android

- **ID задачи:** `TASK-UI-MOB-01`
- **Роль исполнителя:** Android UI Coder
- **Зона:** `media-ui`
- **Файлы:**
  - `app/src/main/java/com/lirix/app/ui/theme/Theme.kt`
  - `app/src/main/java/com/lirix/app/ui/theme/Color.kt`
  - `app/src/main/java/com/lirix/app/ui/theme/Type.kt`
- **Зависит от:** [ADR-023](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-023), `TASK-UI-PROMPTER-01`, `TASK-UI-PROMPTER-02`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Перевести Android-приложение на единую дизайн-систему суфлёра:
1. **Палитры:**
   - Подключение тем «Graphite & Chalk» (`#1B1E24` / `#F2EFE9`) и «Paper & Ink» (`#F5F2EB` / `#181B20`) из общего модуля `:shared`.
   - Поддержка динамического извлечения `Accent` из обложки с гарантией контраста $\ge 4.5:1$ через `ContrastClampHelper`.
2. **Типографика:**
   - `Literata Medium` для текста песен.
   - `Golos Text` для интерфейса с поддержкой табличных цифр `FontFeature.tabularFigures()`.

---

## 2. Критерии приемки (DoD)
- [ ] Темы и типографика подключены в Compose Theme Android-модуля.
- [ ] Сборка `./gradlew :app:assembleRelease` успешна.
