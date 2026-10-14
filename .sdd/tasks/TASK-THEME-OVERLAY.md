# TASK-THEME-OVERLAY: Адаптация плавающего оверлея (FloatingLyricsOverlayView) к темам

## 1. Контекст и проблема
Пользователь зафиксировал: «плавающий оверлэй не меняет цвет».
В `app/src/main/java/com/lirix/app/ui/components/FloatingLyricsOverlayView.kt`:
- Оверлей работает через системный `WindowManager`, но рендерится через Jetpack Compose (`ComposeView`).
- Внутри Compose-дерева оверлея найдено **33** захардкоженных вызова `AppColors.*`:
  - `AppColors.AmoledBlack.copy(alpha = 0.88f)`
  - `AppColors.BorderSubtle`
  - `AppColors.CyberCyan`
  - `AppColors.HyperViolet`
  - `AppColors.TextPrimary`, `AppColors.TextSecondary`
- Корневой Compose-контент НЕ обернут в `LirixTheme`, поэтому не реагирует на изменение выбранной темы в приложении.

## 2. Требования к реализации
1. Подключить чтение темы из репозитория:
   ```kotlin
   val context = LocalContext.current
   val themeRepo = remember { ThemePreferencesRepository.getInstance(context) }
   val currentTheme by themeRepo.selectedTheme.collectAsState()
   ```
2. Обернуть всё содержимое оверлея в `LirixTheme(themeType = currentTheme)`:
   ```kotlin
   LirixTheme(themeType = currentTheme) {
       val lirixColors = LirixThemeTokens.colors
       // ...
   }
   ```
3. Заменить жесткие `AppColors.*`:
   - Фон оверлея: `lirixColors.surfaceElevated.copy(alpha = if (lirixColors.isDark) 0.90f else 0.95f)`.
   - Граница оверлея: `lirixColors.borderSubtle`.
   - Активная караоке-строка: `lirixColors.accentPrimary` (жирный шрифт).
   - Обычные строки: `lirixColors.textSecondary`.
   - Кнопка воспроизведения/паузы: фон `lirixColors.accentPrimary`, иконка контрастного цвета (`if (!lirixColors.isDark || currentTheme == AppThemeType.YANDEX_MUSIC) Color.Black else Color.White`).
   - Кнопки перемотки и закрытия: `lirixColors.accentSecondary` и `lirixColors.textSecondary`.
   - Кнопка разворачивания в приложение: `lirixColors.accentPrimary`.

## 3. Критерии приемки (DoD)
- Плавающий оверлей реактивно меняет свой цвет, фон и акценты при переключении темы в основном приложении.
- Ни одного жесткого вызова `AppColors.*` в оверлее.
- `./gradlew compileDebugKotlin` успешен.
