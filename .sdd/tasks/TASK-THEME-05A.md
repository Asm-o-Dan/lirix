# TASK-THEME-05A: Миграция NowPlayingScreen на LirixThemeTokens

**Зона:** `media-ui`  
**Спецификация:** [`.sdd/specs/media-ui/theme_palette_system_spec.md`](../specs/media-ui/theme_palette_system_spec.md) (раздел 8, 9)  
**Файл для модификации:** `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`  
**Статус:** `READY`

---

## Контекст и цель
В текущей реализации `NowPlayingScreen.kt` все визуальные контейнеры жестко используют статические цвета `AppColors.AmoledBlack`, `AppColors.HyperViolet`, `AppColors.CyberCyan` и `AppColors.SurfaceLevel1..4`. Из-за этого смена темы на 🟢 Spotify Mood, 🟡 Yandex Music или 🌸 Pastel Serenity не затрагивает экран плеера.

## Детерминированные требования к исполнителю:
1. В начале Composable `NowPlayingScreen` извлечь токены текущей темы:
   ```kotlin
   val lirixColors = LirixThemeTokens.colors
   ```
2. Заменить жесткий фон экрана (строка ~690):
   - БЫЛО: `.background(AppColors.AmoledBlack)`
   - СТАЛО: `.background(lirixColors.appBackground)`
3. Перевести ключевые интерактивные элементы на палитру темы:
   - Кнопка Play/Pause (54dp): контейнер красить градиентом из `lirixColors.accentPrimary` и `lirixColors.accentSecondary`.
   - Иконка Play/Pause: `if (lirixColors.isDark) Color.Black else Color.White`.
   - Селектор режимов (Караоке / Текст / Аккорды / Заметки):
     - Активная плашка: `lirixColors.accentPrimary` со скруглением `lirixColors.cardCornerRadius`.
     - Текст активной вкладки: `if (lirixColors.isDark) Color.Black else Color.White`.
     - Текст неактивной вкладки: `lirixColors.textSecondary`.
     - Фон подложки селектора: `lirixColors.surfacePrimary` с бордером `lirixColors.borderSubtle`.
   - Карточка контента (строка ~1227):
     - Фон: `lirixColors.surfacePrimary`.
     - Бордер: `lirixColors.borderSubtle`.
     - Скругление: `lirixColors.cardCornerRadius`.
   - Таймер текущего времени: `lirixColors.accentSecondary`.
   - Общая длительность: `lirixColors.textMuted`.
   - Имя исполнителя: `lirixColors.accentPrimary`.
   - Название трека: `lirixColors.textPrimary`.
   - Караоке строки: активная строка — `lirixColors.karaokeActive`, неактивные — `lirixColors.karaokeInactive`.
4. Не изменять логику `MediaSessionCollector`, таймеров и DAO.

## Критерии приемки (DoD):
- Сборка `./gradlew compileDebugKotlin` без ошибок.
- Тесты `./gradlew testDebugUnitTest` PASS (214/214).
- При переключении на любую из 4 тем фон и акценты NowPlayingScreen мгновенно перерисовываются в палитру выбранной темы.
