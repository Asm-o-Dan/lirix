# TASK-THEME-TURNTABLE: Сквозная адаптация пластинки и звукоснимателя к LirixThemeTokens

## 1. Контекст и проблема
Пользователь зафиксировал: «сама пластинка и звукосниматель тоже никак не меняются».
В `app/src/main/java/com/lirix/app/ui/components/AnalogTurntable.kt` цвета захардкожены на старые токены `AppColors`:
- Контур винилового диска (`sweepGradient`) захардкожен на `listOf(AppColors.HyperViolet, AppColors.CyberCyan, AppColors.HyperVioletDark, AppColors.HyperViolet)`.
- Обод шпинделя (`spindle hole`): `AppColors.CyberCyan`.
- Центральный лейбл без обложки: `AppColors.HyperViolet`, `AppColors.HyperVioletDark`.
- Головка картриджа звукоснимателя (`headshell`): `Color(0xFFA855F7)`.
- Подсветка иглы звукоснимателя (`stylus needle`): `Color(0xFF00E5FF)`.
- Кнопка сворачивания/разворачивания винила («Винил / Скрыть винил»): фон `AppColors.SurfaceLevel1`, бордюр `AppColors.BorderSubtle`, акцент `AppColors.CyberCyan`.

## 2. Требования к реализации
1. Получать токены темы:
   ```kotlin
   val tokens = LirixThemeTokens.colors
   ```
2. Виниловый диск:
   - Контур (`border`):
     ```kotlin
     Brush.sweepGradient(
         listOf(
             tokens.accentPrimary,
             tokens.accentSecondary,
             tokens.accentGlow,
             tokens.accentPrimary
         )
     )
     ```
   - Дорожки винила: `tokens.borderSubtle.copy(alpha = if (tokens.isDark) 0.6f else 0.25f)`.
   - Шпиндель: центр `tokens.appBackground`, бордюр `tokens.accentSecondary.copy(alpha = 0.6f)`.
   - Центральный лейбл без обложки: `Brush.radialGradient(listOf(tokens.accentPrimary, tokens.accentSecondary, tokens.surfaceCard))`.
3. Звукосниматель (Tonearm Canvas):
   - Обод головки картриджа: `tokens.accentPrimary`.
   - Подсветка иглы (needle): активная `tokens.accentSecondary`, свечение `tokens.accentSecondary.copy(alpha = 0.4f)`.
4. Кнопка сворачивания винила:
   - Фон: `tokens.surfaceElevated`.
   - Бордюр: `tokens.borderSubtle`.
   - Иконка и текст: `if (isCollapsed) tokens.accentSecondary else tokens.textSecondary`.

## 3. Критерии приемки (DoD)
- `./gradlew compileDebugKotlin` успешен.
- При переключении темы (Spotify Mood, Yandex Music, Obsidian Pulse, Pastel Serenity) контур пластинки, игла звукоснимателя и кнопка «Скрыть винил» синхронно окрашиваются в акцентные цвета темы.
