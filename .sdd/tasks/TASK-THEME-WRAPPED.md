# TASK-THEME-WRAPPED: Глубокая адаптация экрана Музыкального Wrapped под тему

## 1. Контекст и проблема
Пользователь зафиксировал: «итоги тоже очень странно меняются».
В `app/src/main/java/com/lirix/app/ui/WrappedScreen.kt`:
Хотя внешний фон был заменен на `lirixColors.appBackground`, внутри экрана осталось **81** захардкоженное вхождение `AppColors.*`:
- Баннер архетипа (`ArchetypeBanner`): жесткий темный фон `Brush.linearGradient(listOf(Color(0xFF0A0F24), Color(0xFF000000)))` и жесткие фиолетовые/золотые рамки.
- Фильтры периода (`PeriodChip`): жесткие `AppColors.SurfaceLevel1`, `AppColors.HyperViolet`, `AppColors.BorderSubtle`.
- Сетка ключевых метрик (`MetricCard`): жесткие цвета цифр (`AppColors.CyberCyan`, `AppColors.HyperViolet`, `AppColors.ElectricMint`), фонов и рамок.
- Компас активности (`ActivityCompass`): шкалы времени и дни недели захардкожены на `AppColors.CyberCyan`, `AppColors.ElectricMint`, `AppColors.HyperViolet`.
- Кнопка «ПОСТЕР»: жесткий `AppColors.ExpenseRed` или `AppColors.CyberCyan`.

## 2. Требования к реализации
1. В `WrappedScreen.kt` получить токены:
   ```kotlin
   val lirixColors = LirixThemeTokens.colors
   ```
2. Баннер архетипа (`ArchetypeBanner`):
   - Фон:
     ```kotlin
     Brush.linearGradient(
         listOf(
             lirixColors.surfaceCard,
             lirixColors.surfaceElevated
         )
     )
     ```
   - Рамка: `lirixColors.accentPrimary.copy(alpha = 0.5f)`.
   - Заголовок и иконка трофея: `lirixColors.accentPrimary` и `lirixColors.accentSecondary`.
   - Описание: `lirixColors.textSecondary`.
3. Чипы фильтров (`PeriodChip`):
   - Активный чип: фон `lirixColors.accentPrimary.copy(alpha = 0.2f)`, бордюр `lirixColors.accentPrimary`, текст `lirixColors.accentPrimary`.
   - Неактивный чип: фон `lirixColors.surfaceElevated`, бордюр `lirixColors.borderSubtle`, текст `lirixColors.textSecondary`.
4. Карточки метрик (`MetricCard`):
   - Фон: `lirixColors.surfaceCard`.
   - Бордюр: `lirixColors.borderSubtle`.
   - Лейбл: `lirixColors.textSecondary`.
   - Числовые значения: `lirixColors.accentPrimary` для первого и второго, `lirixColors.accentSecondary` для третьего и четвертого.
5. Компас активности (`ActivityCompass`):
   - Фон контейнера: `lirixColors.surfaceCard`, бордюр `lirixColors.borderSubtle`.
   - Заголовок: `lirixColors.textPrimary`.
   - Неактивные треки шкал: `lirixColors.surfaceElevated`.
   - Активные полосы прогресса: `lirixColors.accentPrimary` и `lirixColors.accentSecondary`.
6. Кнопка «ПОСТЕР»:
   - Фон: `lirixColors.accentPrimary`.
   - Текст и иконка: `if (lirixColors.isDark) Color.Black else Color.White` (или `Color.Black` для высокой контрастности с акцентом).

## 3. Критерии приемки (DoD)
- Ни одного захардкоженного вызова `AppColors.*` в видимых элементах `WrappedScreen.kt`.
- В светлой теме (Pastel Serenity) и темных темах (Spotify, Yandex, Obsidian) карточки, баннер архетипа, графики и кнопка постера гармонично соответствуют выбранной цветовой палитре.
- `./gradlew compileDebugKotlin` успешен.
