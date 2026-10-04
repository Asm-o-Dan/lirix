# Задача TASK-ART-01: Захват и кэширование обложек альбомов (Album Art Ingress & Storage)

- **ID задачи:** `TASK-ART-01`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ingress` / `media-core`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/storage/AlbumArtStorage.kt` (создать)
  2. `app/src/main/java/com/eventengine/app/storage/MusicEntities.kt` (изменить `TrackEntity`)
  3. `app/src/main/java/com/eventengine/app/storage/AppDatabase.kt` (изменить: `version = 7`, `MIGRATION_6_7`)
  4. `app/src/main/java/com/eventengine/app/ingestion/LivePlaybackSnapshot.kt` (изменить: поле `albumArtUri`)
  5. `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt` (изменить: параметр `albumArtUri` в `recordPlaybackSignal`)
  6. `app/src/main/java/com/eventengine/app/ingestion/MediaSessionCollector.kt` (изменить: извлечение bitmap из `MediaMetadata`)
  7. `app/src/main/java/com/eventengine/app/ingestion/NotificationListener.kt` (изменить: fallback извлечения `largeIcon`)
- **Спека:** [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) (v1), [.sdd/contracts/ingress__core.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/ingress__core.md) (FROZEN v1)
- **Приоритет:** HIGH (Архитектурный фундамент для винилового лейбла и списков библиотеки)

---

## 1. Цель задачи

Реализовать сквозной захват обложек музыкальных альбомов из `MediaSession` и `NotificationListener`, их сохранение в локальное файловое хранилище приложения в компактном формате WebP (макс. 512x512, качество 85%), обновление схемы Room с версии 6 до 7 (добавление колонки `albumArtUri` в `music_tracks`), а также трансляцию пути к обложке в `LivePlaybackSnapshot` и `TrackEntity`.

---

## 2. Сигнатуры компонентов и моделей (НЕ МЕНЯТЬ)

### 2.1 Класс `AlbumArtStorage` (создать в `storage/AlbumArtStorage.kt`)
```kotlin
package com.eventengine.app.storage

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AlbumArtStorage(private val context: Context) {
    suspend fun saveBitmap(trackKey: String, bitmap: Bitmap): String?
    fun getAlbumArtPath(trackKey: String): String?
    fun deleteAlbumArt(trackKey: String): Boolean
    fun clearAll(): Boolean

    companion object {
        const val SUBDIR_NAME = "album_art"
        const val MAX_DIMENSION = 512
        const val WEBP_QUALITY = 85
    }
}
```

### 2.2 Обновление `TrackEntity` в `storage/MusicEntities.kt`
```kotlin
data class TrackEntity(
    @PrimaryKey
    val trackKey: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val sourcePackage: String,
    val playCount: Int = 1,
    val totalDurationMs: Long = 0L,
    val firstPlayedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val userNotes: String = "",
    val isFavorite: Boolean = false,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val albumArtUri: String? = null // Новый столбец Room v7
)
```

### 2.3 Обновление `AppDatabase.kt`
- Версия базы данных: `version = 7`.
- Миграция `MIGRATION_6_7`:
```kotlin
@JvmField
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `albumArtUri` TEXT DEFAULT NULL")
    }
}
```
- Регистрация в `Room.databaseBuilder`: `.addMigrations(MIGRATION_5_6, MIGRATION_6_7)`.

### 2.4 Обновление `LivePlaybackSnapshot.kt`
Добавить поле `val albumArtUri: String? = null`:
```kotlin
data class LivePlaybackSnapshot(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val isPlaying: Boolean,
    val basePositionMs: Long,
    val lastPositionUpdateTimeMs: Long,
    val playbackSpeed: Float = 1.0f,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val albumArtUri: String? = null
)
```

### 2.5 Обновление `MusicFeatureEngine.recordPlaybackSignal`
```kotlin
suspend fun recordPlaybackSignal(
    title: String,
    artist: String,
    album: String = "",
    sourcePackage: String,
    playbackState: String = "PLAYING",
    durationDeltaMs: Long = 0L,
    timestamp: Long = System.currentTimeMillis(),
    albumArtUri: String? = null
): TrackEntity?
```

---

## 3. Детальное поведение по шагам

### 3.1 Реализация `AlbumArtStorage.kt`
1. Базовая директория: `val artDir = File(context.filesDir, SUBDIR_NAME)`. При первом обращении выполнить `if (!artDir.exists()) artDir.mkdirs()`.
2. Метод `saveBitmap(trackKey: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO)`:
   - Если `trackKey.isBlank()` или `bitmap.isRecycled` $\to$ вернуть `null`.
   - Проверить размеры: если `bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION`:
     - Вычислить коэффициент масштабирования `ratio = min(512f / bitmap.width, 512f / bitmap.height)`.
     - Создать масштабированный битмап `scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)`.
   - Иначе использовать оригинальный `bitmap`.
   - Целевой файл: `val file = File(artDir, "$trackKey.webp")`.
   - Сохранить в поток `FileOutputStream(file)`:
     - Если `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` $\to$ `Bitmap.CompressFormat.WEBP_LOSSY`.
     - Иначе $\to$ `@Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP`.
     - Качество: `WEBP_QUALITY` (85).
   - Если `scaled != bitmap && !scaled.isRecycled` $\to$ вызвать `scaled.recycle()`.
   - Вернуть `file.absolutePath`.
   - При `Exception` залогировать в Timber и вернуть `null`.
3. Метод `getAlbumArtPath(trackKey: String): String?`:
   - `val file = File(artDir, "$trackKey.webp")`
   - Если `file.exists() && file.length() > 0L` $\to$ вернуть `file.absolutePath`, иначе `null`.
4. Метод `deleteAlbumArt(trackKey: String): Boolean`:
   - `File(artDir, "$trackKey.webp").delete()`.

### 3.2 Извлечение обложки в `MediaSessionCollector.kt`
1. В `MediaSessionCollector` инициализировать экземпляр `private val albumArtStorage = AlbumArtStorage(context)`.
2. В `handlePlaybackChange` и `handleMetadataChange`:
   - Попытаться извлечь Bitmap из метаданных:
     ```kotlin
     val rawBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
         ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
     val artUriStr = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
         ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
     ```
   - Вычислить `trackKey = MusicFeatureEngine.computeTrackKey(title, artist, album)`.
   - Если `rawBitmap != null`:
     - Сохранить через `val savedPath = albumArtStorage.saveBitmap(trackKey, rawBitmap)`.
     - Итоговый `finalArtUri = savedPath ?: artUriStr ?: albumArtStorage.getAlbumArtPath(trackKey)`.
   - Иначе:
     - `finalArtUri = artUriStr ?: albumArtStorage.getAlbumArtPath(trackKey)`.
   - Передать `finalArtUri` в `LivePlaybackSnapshot(..., albumArtUri = finalArtUri)`.
3. В методе `persistMediaEvent`:
   - Передать `albumArtUri = finalArtUri` в вызов `musicEngine.recordPlaybackSignal(..., albumArtUri = finalArtUri)`.

### 3.3 Извлечение обложки в `NotificationListener.kt` (Fallback)
1. В `NotificationListener` инициализировать `private val albumArtStorage by lazy { AlbumArtStorage(applicationContext) }`.
2. В методе `processNotification`:
   - Если `isOngoing` и трек распознан (`parsed.title.isNotBlank()`):
   - Попытаться извлечь Bitmap из нотификации:
     ```kotlin
     val largeIconBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
         notification.getLargeIcon()?.loadDrawable(applicationContext)?.let { drawable ->
             (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
         }
     } else null ?: (extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
     ```
   - Если `largeIconBitmap != null`:
     - `val trackKey = MusicFeatureEngine.computeTrackKey(parsed.title, parsed.artist, parsed.album)`
     - `val savedPath = albumArtStorage.saveBitmap(trackKey, largeIconBitmap)`
   - Передать `albumArtUri = savedPath` в `musicEngine.recordPlaybackSignal(..., albumArtUri = savedPath)`.

### 3.4 Защита от затирания в `MusicFeatureEngine.kt`
В `recordPlaybackSignal`:
```kotlin
val effectiveAlbumArtUri = albumArtUri ?: existingTrack?.albumArtUri
val track = TrackEntity(
    trackKey = trackKey,
    title = title,
    artist = cleanArtist,
    album = album,
    sourcePackage = sourcePackage,
    playCount = updatedPlayCount,
    totalDurationMs = updatedTotalDuration,
    firstPlayedAt = existingTrack?.firstPlayedAt ?: timestamp,
    lastPlayedAt = timestamp,
    userNotes = existingTrack?.userNotes.orEmpty(),
    isFavorite = isFavorite,
    albumArtUri = effectiveAlbumArtUri
)
```

---

## 4. Ошибки и граничные случаи

- **Плеер не предоставляет обложку (например, аудиокниги или радио без тегов):** `albumArtUri` равен `null`, плеер не падает, UI использует дефолтную SVG-иконку.
- **Огромный Bitmap (например, 3000x3000px Hi-Res):** `AlbumArtStorage` пропорционально сжимает его до 512x512 перед сжатием WebP, предотвращая `OutOfMemoryError`.
- **Существующая база v6 на устройстве:** `MIGRATION_6_7` добавляет колонку `albumArtUri TEXT DEFAULT NULL` без потери истории треков и сессий.
- **Очистка памяти битмапа:** масштабированная копия освобождается вызовом `scaled.recycle()`.

---

## 5. Критерии приёмки (DoD)

1. [ ] Класс `AlbumArtStorage` реализован, сохраняет изображения в `files/album_art/{trackKey}.webp` со сжатием до 512x512 и качеством 85%.
2. [ ] Написаны unit-тесты на `AlbumArtStorage` (сохранение, даунскейлинг, получение пути, удаление).
3. [ ] В `TrackEntity` добавлено поле `albumArtUri: String? = null`.
4. [ ] `AppDatabase` обновлен до версии 7, зарегистрирована `MIGRATION_6_7`. Написан unit-тест миграции с v6 на v7.
5. [ ] В `LivePlaybackSnapshot` добавлено поле `albumArtUri: String?`.
6. [ ] `MediaSessionCollector` и `NotificationListener` успешно извлекают битмапы и передают путь в `MusicFeatureEngine`.
7. [ ] В `music_tracks` в базе данных сохраняется абсолютный путь к файлу WebP (`SELECT albumArtUri FROM music_tracks`).
8. [ ] Сборка `./gradlew assembleDebug` и все unit-тесты `./gradlew testDebugUnitTest` проходят успешно без ошибок.
