# Задача TASK-SHR-01: Генерация графических карточек Wrapped & NowPlaying и шеринг через системный Android Intent

- **ID задачи:** `TASK-SHR-01`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui` / `wrapped-analytics` / `feature/share`
- **Файлы:**
  1. `app/src/main/res/xml/file_paths.xml` (создать: конфигурация FileProvider)
  2. `app/src/main/AndroidManifest.xml` (добавить FileProvider)
  3. `app/src/main/java/com/eventengine/app/feature/share/ShareCardGenerator.kt` (создать: генератор карточек на Canvas)
  4. `app/src/main/java/com/eventengine/app/feature/share/ShareManager.kt` (создать: сохранение в кэш и запуск Intent)
  5. `app/src/main/java/com/eventengine/app/ui/components/ShareFormatDialog.kt` (создать: диалог выбора формата 9:16 / 1:1)
  6. `app/src/main/java/com/eventengine/app/ui/WrappedScreen.kt` (добавить кнопку шеринга в заголовок)
  7. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (добавить кнопку шеринга в карточку трека)
  8. `app/src/test/java/com/eventengine/app/ShareCardGeneratorTest.kt` (создать: unit-тесты генерации и сохранения)
- **Спека:** [.sdd/specs/media-ui/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/overview.md) (v1), дизайн-система `UI_DESIGN_SPEC.md`
- **Приоритет:** HIGH (Новая ключевая фича: вирусный шеринг музыкальных итогов и цитат)

---

## 1. Архитектурная цель и UX-сценарии

Пользователь требует возможность визуального шеринга:
*«Нет кнопки поделиться и генерации красивых картинок для этого».*

Функционал включает два типа карточек в двух форматах:
1. **Карточка Wrapped (Музыкальные итоги):**
   - Генерируется на экране `WrappedScreen.kt` с учетом выбранного таймфрейма (`TODAY`, `WEEK`, `MONTH`, `ALL_TIME`).
   - Отображает: Архетип слушателя («НОЧНОЙ СТРАННИК», «ВЕРНЫЙ ФАНАТ» и др.), 4 ключевые KPI метрики (время, число треков, топ-артист, процент караоке), карточку «Главная одержимость» с обложкой и числом повторов, суточный компас активности.
2. **Карточка Now Playing (Цитата и трек):**
   - Генерируется на экране `NowPlayingScreen.kt`.
   - Отображает: Виниловую пластинку с обложкой трека, название трека, исполнителя, бейдж плеера-источника и **текущую активную строчку караоке** крупным неоновым шрифтом в кавычках.
3. **Форматы выгрузки:**
   - 📱 **Stories (9:16, 1080x1920 px):** оптимизировано для Telegram Stories, Instagram, VK.
   - 🖼 **Square Post (1:1, 1080x1080 px):** для Telegram-каналов, постов в ленту и чатов.

---

## 2. Спецификация компонентов

### 2.1 Настройка `FileProvider` в Android

1. Файл `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_images" path="shares/" />
</paths>
```

2. Секция в `app/src/main/AndroidManifest.xml` (внутри `<application>`):
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

---

### 2.2 Модель форматов и генератор `ShareCardGenerator.kt`

```kotlin
enum class ShareCardFormat(val width: Int, val height: Int, val label: String) {
    STORIES_9_16(1080, 1920, "Истории (9:16)"),
    SQUARE_1_1(1080, 1080, "Квадрат (1:1)")
}

object ShareCardGenerator {

    /**
     * Генерирует инфографику Wrapped для выбранного таймфрейма.
     * Рендеринг через нативный Android Canvas на фоновом потоке.
     */
    fun generateWrappedCard(
        context: Context,
        stats: WrappedStats,
        timeframe: AnalyticsTimeframe,
        format: ShareCardFormat,
        obsessionBitmap: Bitmap? = null
    ): Bitmap

    /**
     * Генерирует карточку текущего воспроизводимого трека с цитатой караоке.
     */
    fun generateNowPlayingCard(
        context: Context,
        track: TrackEntity,
        currentLyricLine: String?,
        format: ShareCardFormat,
        albumArtBitmap: Bitmap? = null
    ): Bitmap
}
```

#### Визуальные константы генератора (Design Tokens):
- **Фон:** `0xFF000000` (True AMOLED Black) с радиальным градиентом в центре (`0xFF161820` -> `0xFF000000`).
- **Свечение и акценты:**
  - `HyperViolet`: `0xFFB388FF`
  - `CyberCyan`: `0xFF00E5FF`
  - `ElectricMint`: `0xFF00F5A0`
  - `AmberGold`: `0xFFFFD700`
- **Типографика:**
  - Заголовки: `Typeface.create(Typeface.DEFAULT, Typeface.BOLD)`, цвет `0xFFF2F2F7`.
  - Подписи и метрики: `0xFFA0A0B2`.
  - Цитата караоке: `Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)`, размер 42–48sp, неоновое свечение `Paint.setShadowLayer(...)`.
- **Элементы брендинга:**
  - Внизу карточки тонкая неоновая полоска и надпись `EVENT ENGINE • MUSIC HUB`.

---

### 2.3 Модуль шеринга `ShareManager.kt`

```kotlin
object ShareManager {

    /**
     * Сохраняет Bitmap во временный кэш и вызывает Intent.ACTION_SEND
     */
    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        chooserTitle: String = "Поделиться треком"
    ) = withContext(Dispatchers.IO) {
        val sharesDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val shareFile = File(sharesDir, "share_${System.currentTimeMillis()}.png")

        FileOutputStream(shareFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
```

---

### 2.4 Диалог выбора формата `ShareFormatDialog.kt`

Легковесный диалог в стиле AMOLED (`SurfaceLevel2`, `BorderSubtle`):
- Заголовок: "Поделиться карточкой"
- Две опции:
  1. `[📱 Истории (9:16)]` — подпись: "Идеально для Telegram Stories, Instagram, VK"
  2. `[🖼 Квадрат (1:1)]` — подпись: "Для постов, чатов и Telegram-каналов"
- При выборе опции запускается корутина генерации в `Dispatchers.Default` с показом короткого спиннера, затем вызов `ShareManager.shareBitmap`.

---

### 2.5 Интеграция в экраны

#### 1. `WrappedScreen.kt`:
В верхний заголовок рядом с текстом "Музыкальный Wrapped" добавить кнопку шеринга:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        text = "Музыкальный Wrapped",
        style = MaterialTheme.typography.headlineMedium,
        color = AppColors.TextPrimary
    )
    IconButton(
        onClick = { showShareDialog = true },
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(AppColors.SurfaceLevel2)
            .border(1.dp, AppColors.BorderSubtle, CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Поделиться итогами",
            tint = AppColors.CyberCyan,
            modifier = Modifier.size(20.dp)
        )
    }
}
```

#### 2. `NowPlayingScreen.kt`:
В панель действий над треком (рядом с кнопкой `Favorite`):
```kotlin
IconButton(
    onClick = { showShareDialog = true },
    modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(AppColors.SurfaceLevel1)
        .border(1.dp, AppColors.BorderSubtle, CircleShape)
) {
    Icon(
        imageVector = Icons.Default.Share,
        contentDescription = "Поделиться треком",
        tint = AppColors.HyperViolet,
        modifier = Modifier.size(20.dp)
    )
}
```
При формировании карточки NowPlaying берется текущая активная строчка караоке:
`val activeLine = lines.getOrNull(activeIndex)?.text`
и передается в генератор.

---

## 3. Критерии приемки (DoD)

1. **Генерация битмапов:**
   - Unit-тест `ShareCardGeneratorTest` подтверждает:
     - `generateWrappedCard` генерирует Bitmap строго 1080x1920 (9:16) и 1080x1080 (1:1).
     - `generateNowPlayingCard` генерирует Bitmap строго 1080x1920 (9:16) и 1080x1080 (1:1).
     - Генератор не падает при отсутствии обложки (корректный неоновый фоллбек) или отсутствии цитаты.
2. **Шеринг через FileProvider:**
   - Файл сохраняется в `cacheDir/shares/share_*.png`.
   - `FileProvider.getUriForFile` формирует валидный `content://com.eventengine.app.fileprovider/...` URI.
   - Запускается системный диалог `Intent.createChooser` с передачей `image/png`.
3. **UI и UX:**
   - На экранах `WrappedScreen` и `NowPlayingScreen` присутствуют кнопки «Поделиться».
   - Клик открывает диалог выбора формата (Stories 9:16 / Square 1:1).
   - Генерация выполняется в фоновом потоке без блокировки UI (120 FPS сохраняется).
4. Все тесты (`./gradlew test`) завершаются со статусом SUCCESS.
