# TASK-THEME-CHORDS-TAPSYNC: Адаптация экрана Аккордов и кнопки Tap-to-Sync под дизайн-токены

## 1. Контекст и проблема
Пользователь зафиксировал:
- «страница аккордов не меняет никак оформление»
- «кнопка создание таймкодов тоже никак не меняется»

В `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`:
1. В секции `NowPlayingMode.CHORDS` (строки 1312–1650):
   - Тулбар управления автоскроллом и заголовком использует захардкоженные цвета `AppColors.CyberCyan`, `AppColors.HyperViolet`, `AppColors.SurfaceLevel2`, `AppColors.SurfaceLevel3`, `AppColors.BorderSubtle`.
   - Кнопка автоскролла [▶ Старт / ⏸ Пауза]: жесткие цвета `AppColors.HyperViolet` и `AppColors.CyberCyan`.
   - Основной текст песни с аккордами (строка 1621): `color = AppColors.CyberCyan`. В светлой теме он кислотно-бирюзовый и не меняется!
   - Индикатор поиска «Поиск подбора на AmDm...»: жесткий `AppColors.CyberCyan`.
   - Сообщение «Аккорды AmDm не найдены»: жесткий `AppColors.TextSecondary`.
2. В секции `ReadMode` / кнопки создания таймкодов (строки 2132–2170):
   - Карточка кнопки «⏱ Создать караоке (Tap-to-Sync)»:
     `color = AppColors.SurfaceLevel2`, `border = AppColors.HyperViolet`, `tint = AppColors.CyberCyan`, `color = AppColors.HyperViolet`.

## 2. Требования к реализации
1. В `NowPlayingScreen.kt` использовать `lirixColors = LirixThemeTokens.colors`.
2. Режим `NowPlayingMode.CHORDS`:
   - Контейнер тулбара: фон `lirixColors.surfaceCard`, бордюр `lirixColors.borderSubtle`.
   - Бейдж «🎸 Аккорды AmDm»: текст `lirixColors.accentSecondary`.
   - Кнопка [▶ Старт / ⏸ Пауза]:
     - Фон: `if (isAutoscrollRunning && !isTouchPaused) lirixColors.accentPrimary.copy(alpha = 0.2f) else lirixColors.surfaceElevated`.
     - Бордюр: `if (isAutoscrollRunning && !isTouchPaused) lirixColors.accentPrimary else lirixColors.borderSubtle`.
     - Текст: `if (isAutoscrollRunning) lirixColors.accentPrimary else lirixColors.accentSecondary`.
   - Кнопки зума и темпа: фон `lirixColors.surfaceElevated`, текст `lirixColors.textPrimary`.
   - Лента аккордов песни (`Song Chords Ribbon`): карточки `lirixColors.surfaceElevated`, бордюр `lirixColors.accentPrimary.copy(alpha = 0.5f)`, текст `lirixColors.accentPrimary`.
   - Основной моноширинный текст с аккордами (`displayedChords`):
     `color = lirixColors.textPrimary` (читается контрастно на любом фоне темной и светлой темы!).
   - Прогресс-бар загрузки: `color = lirixColors.accentPrimary`.
   - Сообщения об ошибке / отсутствии аккордов: `color = lirixColors.textSecondary`.
3. Кнопка «⏱ Создать караоке (Tap-to-Sync)»:
   - Фон контейнера: `lirixColors.surfaceCard`.
   - Бордюр: `lirixColors.accentPrimary.copy(alpha = 0.6f)`.
   - Иконка `Icons.Default.MusicNote`: `tint = lirixColors.accentSecondary`.
   - Текст «⏱ Создать караоке (Tap-to-Sync)»: `color = lirixColors.accentPrimary`.

## 3. Критерии приемки (DoD)
- `./gradlew compileDebugKotlin` успешен.
- Текст аккордов контрастен в темных и светлых темах.
- Кнопка автоскролла и создания караоке окрашиваются в акценты выбранной темы.
