# Задача TASK-ART-02: Интеграция обложек альбомов в интерфейс (NowPlaying, History, Library)

- **ID задачи:** `TASK-ART-02`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/ui/components/AlbumArtImage.kt` (создать)
  2. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (изменить)
  3. `app/src/main/java/com/eventengine/app/ui/HistoryScreen.kt` (изменить)
  4. `app/src/main/java/com/eventengine/app/ui/LibraryScreen.kt` (изменить)
- **Спека:** [.sdd/specs/media-ui/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/overview.md) (v1), [.sdd/contracts/core__ui.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__ui.md) (FROZEN v1)
- **Приоритет:** HIGH (Визуальный UI Polish и брендинг)
- **Зависит от:** `TASK-ART-01` (DONE, коммит b85df63)

---

## 1. Цель задачи

Интегрировать захваченные обложки альбомов (`TrackEntity.albumArtUri` и `LivePlaybackSnapshot.albumArtUri`) в ключевые экраны приложения:
1. **Экран «Сейчас играет» (`NowPlayingScreen.kt`):** Круглая вращающаяся обложка в центре виниловой пластинки (яблочный лейбл 56x56 dp с неоновой каймой `HyperViolet`), вращающаяся синхронно на 360° при воспроизведении.
2. **Экран «История» (`HistoryScreen.kt`):** Миниатюра обложки (44x44 dp, скругление 10 dp, `ContentScale.Crop`) в карточках треков вместо фиктивного серого круга.
3. **Экран «Библиотека» (`LibraryScreen.kt`):** Миниатюра обложки (44x44 dp, скругление 10 dp, `ContentScale.Crop`) в каталоге треков.
4. **Хранилище в оперативной памяти (`AlbumArtBitmapCache`):** Асинхронная загрузка с `LruCache` в фоне на `Dispatchers.IO` без блокировки 120 Гц скролла.

---

## 2. Сигнатуры компонентов и моделей (НЕ МЕНЯТЬ)

### 2.1 Кэш и Composable-загрузчик в `ui/components/AlbumArtImage.kt` (создать)
```kotlin
package com.lirix.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lirix.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AlbumArtBitmapCache {
    private val lru = object : LruCache<String, ImageBitmap>(64) {}

    fun get(path: String): ImageBitmap? = synchronized(lru) { lru.get(path) }
    fun put(path: String, bitmap: ImageBitmap) {
        synchronized(lru) { lru.put(path, bitmap) }
    }
    fun clear() {
        synchronized(lru) { lru.evictAll() }
    }
}

@Composable
fun rememberAlbumArtBitmap(artUri: String?): ImageBitmap?

@Composable
fun AlbumArtThumbnail(
    artUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    borderColor: Color = AppColors.BorderSubtle,
    contentDescription: String? = null,
    fallbackIcon: ImageVector = Icons.Default.MusicNote
)
```

---

## 3. Детальное поведение по шагам

### 3.1 Реализация `AlbumArtThumbnail` и `rememberAlbumArtBitmap`
1. `rememberAlbumArtBitmap(artUri: String?)`:
   - Если `artUri.isNullOrBlank()`, вернуть `null`.
   - Проверить наличие в `AlbumArtBitmapCache.get(artUri)`:
     - Если найдено $\to$ моментально вернуть без асинхронного ожидания (0 мс задержки при быстром скролле списков).
   - Если в кэше нет $\to$ запустить `LaunchedEffect(artUri)`:
     ```kotlin
     withContext(Dispatchers.IO) {
         try {
             val cleanPath = artUri.removePrefix("file://")
             val file = File(cleanPath)
             if (file.exists() && file.length() > 0L) {
                 val decoded = BitmapFactory.decodeFile(file.absolutePath)
                 val imageBmp = decoded?.asImageBitmap()
                 if (imageBmp != null) {
                     AlbumArtBitmapCache.put(artUri, imageBmp)
                     bitmapState = imageBmp
                 }
             }
         } catch (e: Exception) {
             // Игнорируем битые файлы
         }
     }
     ```
2. `AlbumArtThumbnail`:
   - Запрашивает `val bitmap = rememberAlbumArtBitmap(artUri)`.
   - Если `bitmap != null`:
     - Отрисовать `Image(bitmap = bitmap, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(shape).border(1.dp, borderColor, shape))`.
   - Если `bitmap == null`:
     - Отрисовать fallback: градиентный `Box(modifier = modifier.size(size).clip(shape).background(radialGradient(...)).border(1.dp, borderColor, shape))` с `Icon(imageVector = fallbackIcon, tint = AppColors.TextSecondary)`.

---

### 3.2 Интеграция в `NowPlayingScreen.kt` (Яблочный лейбл винила)
1. В `NowPlayingScreen.kt` определить актуальный URI обложки:
   ```kotlin
   val effectiveAlbumArtUri = remember(livePlayback?.albumArtUri, currentTrack?.albumArtUri) {
       livePlayback?.albumArtUri?.takeIf { it.isNotBlank() }
           ?: currentTrack?.albumArtUri?.takeIf { it.isNotBlank() }
   }
   val vinylCenterArt = rememberAlbumArtBitmap(effectiveAlbumArtUri)
   ```
2. В блоке винилового диска (строки ~320–339), внутри Box, на который наложен `.rotate(if (isPlaying) spinAngle else 0f)`:
   Заменить текущий статический Box центрального лейбла на:
   ```kotlin
   Box(
       modifier = Modifier
           .size(56.dp)
           .clip(CircleShape)
           .border(1.5.dp, AppColors.HyperViolet, CircleShape),
       contentAlignment = Alignment.Center
   ) {
       if (vinylCenterArt != null) {
           Image(
               bitmap = vinylCenterArt,
               contentDescription = "Album Art Label",
               contentScale = ContentScale.Crop,
               modifier = Modifier.fillMaxSize()
           )
           // Тонкий шпиндель (центральное отверстие пластинки)
           Box(
               modifier = Modifier
                   .size(10.dp)
                   .clip(CircleShape)
                   .background(AppColors.AmoledBlack)
                   .border(1.dp, AppColors.CyberCyan.copy(alpha = 0.6f), CircleShape)
           )
       } else {
           // Fallback: стилизованный лейбл с нотой
           Box(
               modifier = Modifier
                   .fillMaxSize()
                   .background(
                       Brush.radialGradient(
                           listOf(AppColors.HyperViolet, AppColors.HyperVioletDark, AppColors.SurfaceLevel1)
                       )
                   ),
               contentAlignment = Alignment.Center
           ) {
               Icon(
                   imageVector = Icons.Default.MusicNote,
                   contentDescription = null,
                   tint = AppColors.AmoledBlack,
                   modifier = Modifier.size(24.dp)
               )
           }
       }
   }
   ```
3. Благодаря родительскому `.rotate(...)`, яблочный лейбл автоматически плавно вращается вместе со всеми дорожками винила во время воспроизведения (`isPlaying`).

---

### 3.3 Интеграция в `HistoryScreen.kt` (Миниатюры в ленте)
1. В `HistoryTrackCard` (строки ~280–298):
   Заменить декоративный фиолетовый кружок на компонент `AlbumArtThumbnail`:
   ```kotlin
   AlbumArtThumbnail(
       artUri = track.albumArtUri,
       size = 44.dp,
       shape = RoundedCornerShape(10.dp),
       borderColor = AppColors.BorderSubtle,
       contentDescription = "${track.title} artwork"
   )
   ```
2. Если `track.albumArtUri` отсутствует или файл удален, компонент автоматически отображает аккуратный виниловый плейсхолдер.

---

### 3.4 Интеграция в `LibraryScreen.kt` (Каталог библиотеки)
1. В `LibraryTrackCard` (строки ~281–300):
   Заменить декоративный кружок на `AlbumArtThumbnail`:
   ```kotlin
   AlbumArtThumbnail(
       artUri = track.albumArtUri,
       size = 44.dp,
       shape = RoundedCornerShape(10.dp),
       borderColor = AppColors.BorderSubtle,
       contentDescription = "${track.title} library artwork"
   )
   ```

---

## 4. Ошибки и граничные случаи

- **Файл обложки поврежден или не является валидным изображением:** `BitmapFactory.decodeFile` возвращает `null`, блок переключается на fallback без падений.
- **Слишком быстрый скролл 1000 треков:** `AlbumArtBitmapCache` на 64 элемента предотвращает повторные чтения с диска, исключая микрофризы UI (120 fps).
- **Смена трека в NowPlaying:** `remember(artUri)` мгновенно перезапускает эффект загрузки, обложка в центре винила плавно сменяется без белых вспышек.

---

## 5. Критерии приёмки (DoD)

1. [ ] Создан файл `app/src/main/java/com/eventengine/app/ui/components/AlbumArtImage.kt` с `AlbumArtBitmapCache`, `rememberAlbumArtBitmap` и `AlbumArtThumbnail`.
2. [ ] В центре винила `NowPlayingScreen` отображается круглая обложка размером 56 dp с центральным отверстием шпинделя (10 dp) и каймой `HyperViolet`.
3. [ ] При `isPlaying == true` обложка в центре винила плавно вращается на 360° вместе с винилом.
4. [ ] При отсутствии обложки в центре винила показывается fallback с градиентом и иконкой ноты.
5. [ ] В `HistoryScreen` в каждой карточке трека отображается миниатюра обложки (44x44 dp, скругление 10 dp).
6. [ ] В `LibraryScreen` в каждой карточке трека отображается миниатюра обложки (44x44 dp, скругление 10 dp).
7. [ ] Сборка `./gradlew assembleDebug` и все unit-тесты `./gradlew testDebugUnitTest` проходят успешно.
