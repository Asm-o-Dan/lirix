# UI & Component Specification: LRC Tap-to-Sync Studio

## 1. Назначение
Компонент Jetpack Compose для интерактивной нарезки таймкодов караоке из статичного текста песни в реальном времени.

## 2. Модель данных и движок `LrcSyncEngine`
```kotlin
data class SyncLineItem(
    val index: Int,
    val text: String,
    val timestampMs: Long? = null
)

object LrcSyncEngine {
    fun parsePlainToSyncLines(plainText: String): List<SyncLineItem> {
        return plainText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { idx, line -> SyncLineItem(index = idx, text = line) }
    }

    fun buildLrcString(lines: List<SyncLineItem>): String {
        return lines.filter { it.timestampMs != null }.joinToString("\n") { item ->
            val ms = item.timestampMs!!
            val minutes = ms / 60000
            val seconds = (ms % 60000) / 1000
            val hundredths = (ms % 1000) / 10
            String.format("[%02d:%02d.%02d] %s", minutes, seconds, hundredths, item.text)
        }
    }
}
```

## 3. Архитектура UI: `LrcTapSyncStudioDialog.kt`
* **Фокус-телесуфлер**:
  * Верхняя строка: `lines.getOrNull(currentIndex - 1)` (серый цвет, `14.sp`, `alpha = 0.5f`).
  * Активная строка: `lines.getOrNull(currentIndex)` (CyberCyan, жирный, `20.sp`, анимация пульсации при готовности к тапу).
  * Нижняя строка: `lines.getOrNull(currentIndex + 1)` (светло-серый, `15.sp`, `alpha = 0.7f`).
* **Элементы управления**:
  * Полоса прогресса разметки: `(currentIndex.toFloat() / totalLines)` с бейджем `5 / 32 строк`.
  * Мини-контроллер плеера: кнопка `Play/Pause`, текущее время `01:45 / 03:20`.
  * Большая круглая / pill кнопка по центру: `[ ТАП: СЛЕДУЮЩАЯ СТРОКА ]` с хаптической отдачей.
  * Кнопка отката `[ ↺ Шаг назад ]`: сбрасывает тайминг текущей/предыдущей строки и уменьшает `currentIndex`.
  * Кнопка `[ Завершить и сохранить ]`: открывает диалог предпросмотра LRC и сохраняет в Room.

## 4. Dual-Write сохранение
```kotlin
val lrcContent = LrcSyncEngine.buildLrcString(markedLines)
val updatedEntity = currentCache.copy(
    syncedLyricsLrc = lrcContent,
    provider = "USER:TAP_SYNC",
    updatedAt = System.currentTimeMillis()
)
db.lyricsDao().saveLyrics(updatedEntity)
db.musicDao().updateLyrics(trackKey, plainText, lrcContent)
```
