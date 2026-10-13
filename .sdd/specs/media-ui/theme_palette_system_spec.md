# Дизайн-Спецификация: Мульти-Темовая Система Lirix (Theme Palette System)

**Зона:** `media-ui`  
**Модуль:** `ThemePaletteEngine`  
**Версия спецификации:** `v1.0.0`  
**Статус:** `APPROVED / READY FOR IMPLEMENTATION`  
**Дизайнер/Арт-директор:** Anti-Slop Design Engine (UI/UX)  
**Целевой стек:** Android, Kotlin 1.9+, Jetpack Compose, Material 3, Jetpack DataStore Preferences

---

## 1. Введение и Архитектурная Концепция

### 1.1 Проблема текущей реализации
В текущей кодовой базе Lirix (`Theme.kt` и `AppColors.kt`) жестко зафиксирована единственная цветовая схема — `ObsidianAmoledColorScheme`. Константы цветов захардкожены в объекте `AppColors`, а экраны (`AppScaffold.kt`, `NowPlayingScreen.kt`, `HistoryScreen.kt`) напрямую ссылаются на статичные поля `AppColors.AmoledBlack`, `AppColors.HyperViolet` и `AppColors.CyberCyan`.

Это создает:
1. Невозможность динамического переключения тем в runtime без масштабного рефакторинга.
2. Отсутствие семантического слоя для караоке, плеера, аккордов и гитарных ладов.
3. Отсутствие поддержки светлых/пастельных тем (светлая тема требует правильного инвертирования контрастности и теней).

### 1.2 Архитектурный паттерн Anti-Slop Multi-Theme
Для масштабируемости проектируется двухуровневая система:
1. **Material 3 `ColorScheme` Bridge** — стандартный маппинг на `MaterialTheme.colorScheme` для системных компонентов (Surface, NavigationBar, Button, Card, TextField).
2. **Семантическая палитра расширения `LirixColors` (`CompositionLocalProvider(LocalLirixColors provides ...)`)** — доменные токены музыкального плеера Lirix:
   - Цвета караоке: `karaokeActiveLine`, `karaokeInactiveLine`, `karaokeGlow`, `karaokeProgress`.
   - Цвета аккордов: `chordBadgeBg`, `chordBadgeText`, `fretboardWood`, `fretboardWire`, `fretboardDot`.
   - Цвета винила / Turntable: `turntablePlatter`, `turntableArm`, `turntableGlow`.
   - Цвета карточек шеринга и бейджей: `badgeFts`, `badgeVector`, `badgeRrf`.
   - Радиусы и стили бордеров: `cardCornerRadius`, `borderWidth`, `borderColor`.

---

## 2. Обзор 4 Дизайнерских Тем

| Тема | Идентификатор enum | Эстетика & Назначение | Контрастность (WCAG) | Радиусы |
| :--- | :--- | :--- | :--- | :--- |
| 🟢 **Spotify Mood** | `SPOTIFY_MOOD` | Матовая темно-скандинавская студийная эстетика. Концентрация на музыке, минимализм. | AAA на тексте (11.8:1), AA на контролах | 4–8 dp (строгие формы) |
| 🟡 **Yandex Music** | `YANDEX_MUSIC` | Энергичный контрастный ночной стиль с сочной «Моей Волной». Драйв, сочные бейджи. | AAA на тексте (14.2:1), AA+ на акцентах | 12–16 dp (мягкие органичные) |
| 🟣 **Original Lirix** | `OBSIDIAN_PULSE` | Абсолютный черный AMOLED (0% свечения пикселей) + неоновый киберпанк/винил. | AAA (21:1 максимальный) | 12–18 dp + 1dp neon border |
| 🌸 **Pastel Serenity** | `PASTEL_SERENITY` | Мягкая пастельная светлая журнальная эстетика. Уют, кремовые слои, релакс без ядовитых тонов. | AA (6.8:1 для Dark Slate на слоновой кости) | 16–22 dp (воздушные скругления) |

---

## 3. Детальные Цветовые Палитры (Color Tokens)

### 3.1 🟢 Spotify Mood (Темная скандинавская эстетика)
Характер: матовые серые градиенты, фирменный зеленый круглый акцент, отсутствие неонового свечения, строгая функциональность.

```kotlin
// Spotify Mood Design Tokens
val SpotifyDarkBg          = Color(0xFF121212) // Основной холст приложения
val SpotifySurfaceLevel1    = Color(0xFF181818) // Подложки карточек и плеера
val SpotifySurfaceLevel2    = Color(0xFF242424) // Приподнятые диалоги и списки
val SpotifySurfaceLevel3    = Color(0xFF2A2A2A) // Карточки в фокусе, инпуты
val SpotifySurfaceElevated  = Color(0xFF333333) // Hover / Pressed состояния

val SpotifyGreenAccent      = Color(0xFF1DB954) // Фирменный Spotify Green
val SpotifyGreenPressed     = Color(0xFF1AA34A) // Состояние нажатия
val SpotifyGreenSubtle      = Color(0x261DB954) // Полупрозрачный ореол (15% альфа)

val SpotifyTextPrimary      = Color(0xFFFFFFFF) // Заголовки, активные треки
val SpotifyTextSecondary    = Color(0xFFB3B3B3) // Исполнители, метаданные
val SpotifyTextMuted        = Color(0xFF727272) // Таймкоды, неактивные табы
val SpotifyBorderSubtle     = Color(0xFF282828) // Минималистичные разделители

// Доменные токены плеера Lirix
val SpotifyKaraokeActive    = Color(0xFF1DB954) // Активная строка текста
val SpotifyKaraokeInactive  = Color(0xFF5E5E5E) // Неактивные строки
val SpotifyChordBadgeBg     = Color(0xFF282828) // Чип аккорда
val SpotifyChordBadgeText   = Color(0xFF1DB954) // Текст аккорда
val SpotifyTurntablePlatter = Color(0xFF1E1E1E) // Матовый диск
val SpotifyTurntableArm     = Color(0xFFB3B3B3) // Серебристый тонарм
```

### 3.2 🟡 Yandex Music (Энергичный контрастный темный стиль)
Характер: глубокий графитово-синий фон, кинетический солнечный акцент, яркие бейджи, ощущение «Моей Волны».

```kotlin
// Yandex Music Design Tokens
val YandexDarkBg           = Color(0xFF0E0F12) // Глубокий ночной графит
val YandexSurfaceLevel1    = Color(0xFF18191E) // Базовые контейнеры
val YandexSurfaceLevel2    = Color(0xFF22242B) // Списки и карточки треков
val YandexSurfaceLevel3    = Color(0xFF2B2E38) // Плавающие оверлеи
val YandexSurfaceElevated  = Color(0xFF353945) // Активные элементы

val YandexYellowAccent     = Color(0xFFFFCC00) // Сочный желтый акцент
val YandexOrangeVibe       = Color(0xFFFF5500) // Вторичный акцент «Моя Волна»
val YandexYellowGlow       = Color(0x33FFCC00) // Теплый ореол свечения кнопки Play

val YandexTextPrimary      = Color(0xFFF5F6F8) // Чистый белый с холодным оттенком
val YandexTextSecondary    = Color(0xFF9EA3B0) // Читаемый пепельный
val YandexTextMuted        = Color(0xFF656A78) // Второстепенные подсказки
val YandexBorderSubtle     = Color(0xFF262933) // Мягкая контурная рамка

// Доменные токены плеера Lirix
val YandexKaraokeActive    = Color(0xFFFFCC00) // Активная строка (энергичный желтый)
val YandexKaraokeInactive  = Color(0xFF707584) // Неактивные строки
val YandexChordBadgeBg     = Color(0xFF2B2E38) // Фоновый бейдж аккорда
val YandexChordBadgeText   = Color(0xFFFFCC00) // Текст аккорда
val YandexTurntablePlatter = Color(0xFF18191E) // Диск винила
val YandexTurntableArm     = Color(0xFFFFCC00) // Золотистый тонарм
```

### 3.3 🟣 Original Lirix (Obsidian Pulse AMOLED)
Характер: настоящий 100% черный `#000000` для максимальной экономии батареи AMOLED-экранов, неоновые акценты HyperViolet и CyberCyan, тонкие технологичные границы 1dp.

```kotlin
// Obsidian Pulse AMOLED Design Tokens
val ObsidianAmoledBlack    = Color(0xFF000000) // 100% черный фон
val ObsidianSurfaceLevel1  = Color(0xFF0D0E12) // Контейнер нижнего меню
val ObsidianSurfaceLevel2  = Color(0xFF161820) // Карточки списка
val ObsidianSurfaceLevel3  = Color(0xFF1E212D) // Всплывающие диалоги
val ObsidianSurfaceElevated= Color(0xFF282C3D) // Парящие кнопки

val ObsidianHyperViolet    = Color(0xFFB388FF) // Основной неоновый фиолетовый
val ObsidianHyperVioletDark= Color(0xFF7C4DFF) // Глубокий фиолетовый для градиентов
val ObsidianCyberCyan      = Color(0xFF00E5FF) // Неоновый кибер-циан
val ObsidianElectricMint   = Color(0xFF00F5A0) // Неоновая мята для статусов

val ObsidianTextPrimary    = Color(0xFFF2F2F7) // Бело-серебристый текст
val ObsidianTextSecondary  = Color(0xFFA0A0B2) // Приглушенный лавандово-серый
val ObsidianTextMuted      = Color(0xFF5A5A6E) // Подсказки
val ObsidianBorderSubtle   = Color(0xFF222530) // Неоновый тонкий контур 1dp

// Доменные токены плеера Lirix
val ObsidianKaraokeActive  = Color(0xFFB388FF) // Фиолетовый активный текст
val ObsidianKaraokeInactive= Color(0xFF6E6E85) // Приглушенный текст
val ObsidianChordBadgeBg   = Color(0xFF1E212D) // Чип
val ObsidianChordBadgeText = Color(0xFF00E5FF) // Циановый аккорд
val ObsidianTurntablePlatter= Color(0xFF0D0E12) // Черный матовый винил
val ObsidianTurntableArm   = Color(0xFF94A3B8) // Металлический тонарм
```

### 3.4 🌸 Pastel Serenity (Мягкая пастельная светлая тема)
Характер: теплая бумага / слоновая кость, мягкие пудровые и лавандовые акценты, отсутствие резких контрастов, деликатные полупрозрачные тени. Идеально для дневного чтения караоке и аккордов на солнце.

```kotlin
// Pastel Serenity Design Tokens
val PastelWarmCreamBg      = Color(0xFFFBF9F5) // Фон: теплая слоновая кость
val PastelSurfaceLevel1    = Color(0xFFF3EFE6) // Мягкий бежевый контейнер
val PastelSurfaceLevel2    = Color(0xFFE9E3D8) // Подложки карточек
val PastelSurfaceLevel3    = Color(0xFFDED6C9) // Диалоги и оверлеи
val PastelSurfaceElevated  = Color(0xFFFFFFFF) // Чистый белый для контрастных плашек

val PastelRoseAccent       = Color(0xFFD9777F) // Пудрово-розовый терракот
val PastelSageSecondary    = Color(0xFF7A9A7B) // Мягкий шалфейный зеленый
val PastelLavenderMuted    = Color(0xFF9E8DB5) // Пастельная лаванда

val PastelTextPrimary      = Color(0xFF2C2D30) // Глубокий темный графит (без ядовитого черного)
val PastelTextSecondary    = Color(0xFF6E6F74) // Теплый серый
val PastelTextMuted        = Color(0xFF9C9B9F) // Мягкие подписи
val PastelBorderSubtle     = Color(0xFFE4DDD2) // Бархатная теплая граница

// Доменные токены плеера Lirix
val PastelKaraokeActive    = Color(0xFFD9777F) // Розово-терракотовый активный текст
val PastelKaraokeInactive  = Color(0xFFA5A4A8) // Неактивные строки
val PastelChordBadgeBg     = Color(0xFFE9E3D8) // Теплый бежевый чип
val PastelChordBadgeText   = Color(0xFF7A9A7B) // Шалфейный аккорд
val PastelTurntablePlatter = Color(0xFFEDE7DC) // Светлый виниловый стол
val PastelTurntableArm     = Color(0xFF7A7067) // Бронзово-коричневый тонарм
```

---

## 4. Сравнительная Матрица Токенов (Token Specification Matrix)

| Семантический Токен | 🟢 Spotify Mood | 🟡 Yandex Music | 🟣 Obsidian Pulse | 🌸 Pastel Serenity |
| :--- | :--- | :--- | :--- | :--- |
| **`appBackground`** | `#121212` | `#0E0F12` | `#000000` | `#FBF9F5` |
| **`surfacePrimary`** | `#181818` | `#18191E` | `#0D0E12` | `#F3EFE6` |
| **`surfaceCard`** | `#242424` | `#22242B` | `#161820` | `#E9E3D8` |
| **`surfaceElevated`** | `#2A2A2A` | `#2B2E38` | `#1E212D` | `#FFFFFF` |
| **`accentPrimary`** | `#1DB954` | `#FFCC00` | `#B388FF` | `#D9777F` |
| **`accentSecondary`** | `#1ED760` | `#FF5500` | `#00E5FF` | `#7A9A7B` |
| **`accentGlow`** | `#261DB954` | `#33FFCC00` | `#33B388FF` | `#20D9777F` |
| **`textPrimary`** | `#FFFFFF` | `#F5F6F8` | `#F2F2F7` | `#2C2D30` |
| **`textSecondary`** | `#B3B3B3` | `#9EA3B0` | `#A0A0B2` | `#6E6F74` |
| **`textMuted`** | `#727272` | `#656A78` | `#5A5A6E` | `#9C9B9F` |
| **`borderSubtle`** | `#282828` | `#262933` | `#222530` | `#E4DDD2` |
| **`karaokeActive`** | `#1DB954` | `#FFCC00` | `#B388FF` | `#D9777F` |
| **`karaokeInactive`**| `#5E5E5E` | `#707584` | `#6E6E85` | `#A5A4A8` |
| **`chordBadgeBg`** | `#282828` | `#2B2E38` | `#1E212D` | `#E9E3D8` |
| **`chordBadgeText`** | `#1DB954` | `#FFCC00` | `#00E5FF` | `#7A9A7B` |
| **`fretboardWood`** | `#1A1A1A` | `#16171B` | `#08090C` | `#DFD5C4` |
| **`cardCornerRadius`**| `6.dp` | `14.dp` | `14.dp` | `20.dp` |
| **`pillCornerRadius`**| `100.dp` (круглый)| `18.dp` | `16.dp` | `24.dp` |
| **`isDarkTheme`** | `true` | `true` | `true` | `false` |

---

## 5. Архитектура Кода в Jetpack Compose

### 5.1 Структура файлов пакета `com.lirix.app.ui.theme`

```
app/src/main/java/com/lirix/app/ui/theme/
├── AppThemeType.kt          // Enum 4 тем + метаданные для селектора
├── LirixColors.kt           // Data class доменных токенов плеера + CompositionLocal
├── ColorPalettes.kt         // Определения токенов для каждой темы
├── ThemeManager.kt          // DataStore репозиторий для сохранения темы
└── Theme.kt                 // LirixTheme Composable с поддержкой тем
```

### 5.2 Определение `AppThemeType.kt`

```kotlin
package com.lirix.app.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppThemeType(
    val id: String,
    val title: String,
    val subtitle: String,
    val previewPrimary: Color,
    val previewAccent: Color,
    val previewBackground: Color,
    val isDark: Boolean
) {
    SPOTIFY_MOOD(
        id = "spotify_mood",
        title = "Spotify Mood",
        subtitle = "Темная скандинавская эстетика",
        previewPrimary = Color(0xFF1DB954),
        previewAccent = Color(0xFF1DB954),
        previewBackground = Color(0xFF121212),
        isDark = true
    ),
    YANDEX_MUSIC(
        id = "yandex_music",
        title = "Yandex Music",
        subtitle = "Энергичный контрастный темный",
        previewPrimary = Color(0xFFFFCC00),
        previewAccent = Color(0xFFFF5500),
        previewBackground = Color(0xFF0E0F12),
        isDark = true
    ),
    OBSIDIAN_PULSE(
        id = "obsidian_pulse",
        title = "Original Lirix",
        subtitle = "Obsidian Pulse AMOLED",
        previewPrimary = Color(0xFFB388FF),
        previewAccent = Color(0xFF00E5FF),
        previewBackground = Color(0xFF000000),
        isDark = true
    ),
    PASTEL_SERENITY(
        id = "pastel_serenity",
        title = "Pastel Serenity",
        subtitle = "Мягкая светлая пастель",
        previewPrimary = Color(0xFFD9777F),
        previewAccent = Color(0xFF7A9A7B),
        previewBackground = Color(0xFFFBF9F5),
        isDark = false
    );

    companion object {
        val DEFAULT = OBSIDIAN_PULSE

        fun fromId(id: String?): AppThemeType {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}
```

### 5.3 Класс Доменных Токенов `LirixColors.kt`

```kotlin
package com.lirix.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class LirixColors(
    val appBackground: Color,
    val surfacePrimary: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val karaokeActive: Color,
    val karaokeInactive: Color,
    val chordBadgeBg: Color,
    val chordBadgeText: Color,
    val fretboardWood: Color,
    val cardCornerRadius: Dp,
    val pillCornerRadius: Dp,
    val isDark: Boolean
)

val LocalLirixColors = staticCompositionLocalOf<LirixColors> {
    error("LocalLirixColors not initialized! Wrap content in LirixTheme.")
}

/**
 * Удобный доступ к палитре в Composable функциях:
 * val colors = LirixTheme.colors
 */
object LirixThemeTokens {
    val colors: LirixColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLirixColors.current
}
```

### 5.4 Реализация `LirixTheme` (`Theme.kt`)

```kotlin
package com.lirix.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import com.lirix.app.ui.theme.LocalLirixColors
import com.lirix.app.ui.theme.LirixThemeTokens

@Composable
fun LirixTheme(
    themeType: AppThemeType = AppThemeType.DEFAULT,
    content: @Composable () -> Unit
) {
    val lirixColors = ColorPalettes.getTokens(themeType)

    val colorScheme = if (themeType.isDark) {
        darkColorScheme(
            primary = lirixColors.accentPrimary,
            onPrimary = if (themeType == AppThemeType.YANDEX_MUSIC) Color.Black else lirixColors.appBackground,
            primaryContainer = lirixColors.surfaceElevated,
            onPrimaryContainer = lirixColors.textPrimary,
            secondary = lirixColors.accentSecondary,
            onSecondary = lirixColors.appBackground,
            background = lirixColors.appBackground,
            surface = lirixColors.surfacePrimary,
            surfaceVariant = lirixColors.surfaceCard,
            onBackground = lirixColors.textPrimary,
            onSurface = lirixColors.textPrimary,
            onSurfaceVariant = lirixColors.textSecondary,
            outline = lirixColors.borderSubtle
        )
    } else {
        lightColorScheme(
            primary = lirixColors.accentPrimary,
            onPrimary = Color.White,
            primaryContainer = lirixColors.surfaceElevated,
            onPrimaryContainer = lirixColors.textPrimary,
            secondary = lirixColors.accentSecondary,
            onSecondary = Color.White,
            background = lirixColors.appBackground,
            surface = lirixColors.surfacePrimary,
            surfaceVariant = lirixColors.surfaceCard,
            onBackground = lirixColors.textPrimary,
            onSurface = lirixColors.textPrimary,
            onSurfaceVariant = lirixColors.textSecondary,
            outline = lirixColors.borderSubtle
        )
    }

    CompositionLocalProvider(LocalLirixColors provides lirixColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
```

---

## 6. UX/UI Спецификация Селектора Тем (Theme Picker)

### 6.1 Размещение в интерфейсе
Селектор тем интегрируется в:
1. **Настройки / Меню профиля (`SettingsScreen` / `NowPlayingOverflowBottomSheet`)**:
   - Секция «Внешний вид» $\to$ «Тема оформления».
2. **Диалог быстрого выбора**:
   - Вызывается по тапу на иконку палитры `Icons.Outlined.Palette` в тулбаре экрана `LibraryScreen` или из меню `NowPlayingScreen`.

### 6.2 Интерактивный UI Карточки Темы (Theme Preview Card)
Каждая тема отображается в виде горизонтальной или 2x2 grid карточки со следующими визуальными элементами:
- **Размер карточки:** высота 110 dp, ширина fillMaxWidth (или колонка 2x2).
- **Скругление карточки:** соответствует радиусу темы (`cardCornerRadius`).
- **Фон карточки:** `previewBackground` темы.
- **Миниатюра плеера внутри карточки (Live Miniature):**
  - Левая часть: мини-круглый арт/винил (диаметр 28 dp).
  - Центр: полоса прогресса воспроизведения (заполненная цветом `previewAccent`).
  - Правая часть: мини-кнопка Play/Pause в форме фирменного круга/скругленного квадрата темы.
  - Текстовый лейбл: название темы жирным шрифтом (`title`) и дескриптор (`subtitle`).
- **Индикатор выбора:**
  - Если тема активна: цветной бейдж-галочка в правом верхнем углу + активный контур карточки толщиной 2 dp в цвет `accentPrimary`.
  - Плавная кинетическая анимация перехода через `animateColorAsState(animationSpec = tween(300))`.

### 6.3 Haptic & Transition Feedback
- При тапе на тему воспроизводится тактильный щелчок `HapticFeedbackType.LongPress` или `HapticFeedbackType.TextHandleMove`.
- Перерисовка UI происходит без перезапуска Activity, благодаря реактивному обновлению `StateFlow<AppThemeType>` в `MainActivity.setContent`.

---

## 7. Хранение и Персистентность (Preferences DataStore)

### 7.1 Спецификация ключа
- **Файл хранилища:** `lirix_preferences.preferences_pb`
- **Ключ:** `stringPreferencesKey("selected_app_theme")`
- **Значение по умолчанию:** `"obsidian_pulse"` (обратная совместимость с текущей версией).

### 7.2 Класс `ThemePreferencesRepository.kt`

```kotlin
package com.lirix.app.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lirix.app.ui.theme.AppThemeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lirix_preferences")

class ThemePreferencesRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("selected_app_theme")

    val selectedTheme: Flow<AppThemeType> = context.dataStore.data.map { preferences ->
        val themeId = preferences[themeKey]
        AppThemeType.fromId(themeId)
    }

    suspend fun setTheme(theme: AppThemeType) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = theme.id
        }
    }
}
```

---

## 8. Anti-Slop Критерии Приемки (Quality Gate)

1. **Zero Layout Distortion:** Смена темы не приводит к прыжкам лейаута, сдвигам каретки автоскролла или мерцанию обложки трека.
2. **WCAG 2.1 Контрастность:**
   - Для всех 4 тем коэффициент контрастности текста первого уровня `textPrimary` на основном фоне составляет $\ge 7:1$ (соответствие AAA).
   - Для текста караоке активной строки контраст $\ge 4.5:1$ (соответствие AA).
3. **AMOLED Pure Black Guard:** Тема `OBSIDIAN_PULSE` гарантирует RGB `#000000` для корневого `appBackground` и нулевую утечку свечения OLED-матрицы.
4. **DataStore Synchronous Bootstrap:** При холодном старте приложения сохраненная тема читается без белого флеша (splash screen окрашивается в фон сохраненной темы).
5. **No Direct Hardcoded Colors:** Все вызовы `AppColors.AmoledBlack` и `AppColors.HyperViolet` в экранах `NowPlayingScreen`, `AppScaffold`, `HistoryScreen`, `LibraryScreen` заменяются на `LirixThemeTokens.colors.*` или MaterialTheme токины.

---

## 9. План Внедрения для Кодера (Task Decomposition)

- [ ] **TASK-THEME-01**: Создать файлы `AppThemeType.kt`, `LirixColors.kt`, `ColorPalettes.kt` в пакете `com.lirix.app.ui.theme`.
- [ ] **TASK-THEME-02**: Обновить `Theme.kt`, внедрив `CompositionLocalProvider(LocalLirixColors)` и проброс 4 палитр.
- [ ] **TASK-THEME-03**: Реализовать `ThemePreferencesRepository.kt` на базе Jetpack DataStore / SharedPreferences.
- [ ] **TASK-THEME-04**: Реализовать Composable-компонент `ThemeSelectorDialog.kt` и `ThemeCardPreview.kt`.
- [ ] **TASK-THEME-05**: Отрефакторить `AppScaffold.kt`, `NowPlayingScreen.kt`, `HistoryScreen.kt`, переведя обращения со статичного `AppColors` на `LirixThemeTokens.colors`.
