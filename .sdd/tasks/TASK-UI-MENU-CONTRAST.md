# TASK-UI-MENU-CONTRAST: Адаптация цветов текста и иконок в NowPlayingOverflowMenu под тему

**Зона:** `media-ui`  
**Спецификация:** [`.sdd/specs/media-ui/theme_palette_system_spec.md`](../specs/media-ui/theme_palette_system_spec.md) (WCAG 2.1 Контрастность)  
**Файл для модификации:** `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt`  
**Статус:** `READY`

---

## Проблема:
В светлой теме (🌸 Pastel Serenity) фон выпадающего меню `DropdownMenu` становится белым/кремовым (`tokens.surfaceElevated = #FFFFFF`). 
Однако пункты меню («Плавающий оверлей», «Поделиться постером», «Калибровка задержки», «Режим обучения», «Скрыть винил») используют жестко зашитый белый цвет текста:
`color = AppColors.TextPrimary` (`#F2F2F7`).
В результате белый текст на белом фоне становится абсолютно невидимым для пользователя.

## Детерминированные требования:
1. Заменить во всех `DropdownMenuItem`:
   - `AppColors.TextPrimary` $\to$ `tokens.textPrimary` (для Pastel Serenity это темный контрастный сланец `#2C2D30`, для темных тем — белый `#F2F2F7`).
   - Иконки и неактивные состояния: `tokens.textSecondary` или `tokens.accentPrimary`.
2. Пункт «Тема оформления» уже использует `tokens.textPrimary` — оставить эталоном.
3. Ошибки («Сменить источник текста») оставить в `AppColors.ExpenseRed` (контрастно и на белом, и на черном).

## Критерии приемки (DoD):
- В светлой теме все пункты меню четко читаются (контрастность $\ge 7:1$).
- `./gradlew testDebugUnitTest` PASS.
