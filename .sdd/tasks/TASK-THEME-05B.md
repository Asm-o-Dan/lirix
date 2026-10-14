# TASK-THEME-05B: Миграция LibraryScreen и WrappedScreen на LirixThemeTokens

**Зона:** `media-ui`  
**Спецификация:** [`.sdd/specs/media-ui/theme_palette_system_spec.md`](../specs/media-ui/theme_palette_system_spec.md) (раздел 8, 9)  
**Файлы для модификации:**  
- `app/src/main/java/com/lirix/app/ui/LibraryScreen.kt`  
- `app/src/main/java/com/lirix/app/ui/WrappedScreen.kt`  
**Статус:** `READY`

---

## Контекст и цель
В экранах `LibraryScreen.kt` и `WrappedScreen.kt` корневые контейнеры используют `.background(AppColors.AmoledBlack)`, а подложки списков и заголовки — жесткие неоновые константы. Необходимо связать экраны с `LirixThemeTokens.colors`.

## Детерминированные требования к исполнителю:
1. `LibraryScreen.kt`:
   - Внедрить `val lirixColors = LirixThemeTokens.colors`.
   - Заменить корневой фон `.background(AppColors.AmoledBlack)` на `.background(lirixColors.appBackground)`.
   - Заменить заголовки `AppColors.TextPrimary` на `lirixColors.textPrimary`.
   - Подложку переключателя вкладок ([Избранное] | [История]) красить в `lirixColors.surfacePrimary` с бордером `lirixColors.borderSubtle`.
   - Карточки треков в списке: фон `lirixColors.surfaceCard`, скругление `lirixColors.cardCornerRadius`, бордер `lirixColors.borderSubtle`.
2. `WrappedScreen.kt`:
   - Внедрить `val lirixColors = LirixThemeTokens.colors`.
   - Заменить корневой фон `.background(AppColors.AmoledBlack)` на `.background(lirixColors.appBackground)`.
   - Заменить фон карточек статистики и ачивок на `lirixColors.surfaceCard` / `lirixColors.surfaceElevated`.
   - Скругления карточек: `lirixColors.cardCornerRadius`.
   - Текстовые поля: `lirixColors.textPrimary` и `lirixColors.textSecondary`.

## Критерии приемки (DoD):
- Сборка `./gradlew compileDebugKotlin` без ошибок.
- Тесты `./gradlew testDebugUnitTest` PASS.
- Вкладки «Медиатека» и «Итоги» отображаются в палитре выбранной темы (включая светлый фон `PastelSerenity`).
