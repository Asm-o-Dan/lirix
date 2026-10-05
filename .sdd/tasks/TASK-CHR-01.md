# Задача TASK-CHR-01: Умная автопрокрутка текста аккордов с адаптивным расчётом скорости и регулятором темпа

- **ID задачи:** `TASK-CHR-01`
- **Роль исполнителя:** Кодер
- **Зона:** `lyrics-engine`, `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/feature/lyrics/ChordAutoscrollCalculator.kt` (чистый математический калькулятор адаптивной скорости и шага скролла)
  2. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (плавающая панель управления автопрокруткой, корутина smooth scroll, пауза при касании, интеграция со стейтом плеера)
  3. `app/src/test/java/com/eventengine/app/ChordAutoscrollCalculatorTest.kt` (100% TDD unit-тесты алгоритмов калькулятора)
- **Приоритет:** HIGH (Новая фича для музыкантов и гитаристов)

---

## 1. Назначение и контекст

При живом исполнении песен под гитару музыканту неудобно отрывать руки от инструмента для прокрутки текста с аккордами на экране смартфона. 
Требуется реализовать умную систему автопрокрутки (Autoscroll) для вкладки «Аккорды» в `NowPlayingScreen`:
1. **Адаптивный базовый темп (Smart Default Speed)**:
   - Если длительность трека известна из плеера (`durationMs > 0`) и текст аккордов содержит `linesCount > 0`:
     * Базовое время на одну строку: `durationMs / linesCount` (мс на строку).
     * Если известна полная высота скроллируемой области `totalScrollPx > 0`: 
       `baseSpeedPxPerSec = totalScrollPx / (durationMs / 1000f)`.
       Текст доезжает от первого до последнего аккорда ровно за время звучания песни.
   - Если длительность трека неизвестна (или трек в IDLE):
     * Фоллбэк на комфортный темп живой игры: ~2.8 секунды на строку текста.
2. **Множитель скорости (Speed Multiplier)**:
   - Диапазон: от `0.5x` до `2.0x` с шагом `0.25x` (кнопки `[-]` и `[+]`).
   - Итоговая скорость: `effectiveSpeed = baseSpeedPxPerSec * multiplier`.
3. **UX и управление автоскроллом**:
   - Компактный плавающий виджет управления на вкладке «Аккорды» в фирменном AMOLED-стиле:
     * `[ ▶ Старт / ⏸ Пауза ]`
     * Индикатор и регулировка скорости: `[-] 1.0x [+]`
     * `[ ⤾ Наверх ]` (мгновенный возврат к первой строке песни)
   - **Touch Interruption (Пауза при касании)**: если пользователь скроллит текст пальцем (`scrollState.isScrollInProgress`), автоскролл приостанавливается на 2.5 секунды, после чего плавно продолжает движение с новой позиции.
   - **Автостоп**: при достижении конца документа (`scrollState.value >= scrollState.maxValue`) воспроизведение автоскролла завершается.

---

## 2. Архитектура калькулятора `ChordAutoscrollCalculator`

Детерминированный класс без зависимостей от Android SDK для изолированного модульного тестирования:

```kotlin
package com.lirix.app.feature.lyrics

object ChordAutoscrollCalculator {
    const val DEFAULT_SECONDS_PER_LINE = 2.8f
    const val MIN_MULTIPLIER = 0.5f
    const val MAX_MULTIPLIER = 2.5f
    const val STEP_MULTIPLIER = 0.25f

    fun calculateBaseSpeedPxPerSec(
        durationMs: Long,
        linesCount: Int,
        totalScrollPx: Float,
        estimatedLineHeightPx: Float = 50f
    ): Float {
        if (totalScrollPx <= 0f) return 0f
        if (durationMs > 10_000L) {
            val durationSec = durationMs / 1000f
            return totalScrollPx / durationSec
        }
        val safeLines = if (linesCount > 0) linesCount else (totalScrollPx / estimatedLineHeightPx).toInt().coerceAtLeast(1)
        val estimatedDurationSec = safeLines * DEFAULT_SECONDS_PER_LINE
        return totalScrollPx / estimatedDurationSec
    }

    fun calculateLineDurationMs(durationMs: Long, linesCount: Int): Long {
        if (durationMs > 10_000L && linesCount > 0) {
            return durationMs / linesCount
        }
        return (DEFAULT_SECONDS_PER_LINE * 1000).toLong()
    }

    fun clampMultiplier(multiplier: Float): Float {
        return multiplier.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    fun nextMultiplier(current: Float, increase: Boolean): Float {
        val next = if (increase) current + STEP_MULTIPLIER else current - STEP_MULTIPLIER
        return (Math.round(next * 100) / 100f).coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    fun calculateStepDelta(effectiveSpeedPxPerSec: Float, frameDeltaMs: Long): Float {
        if (effectiveSpeedPxPerSec <= 0f || frameDeltaMs <= 0L) return 0f
        return effectiveSpeedPxPerSec * (frameDeltaMs / 1000f)
    }
}
```

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `ChordAutoscrollCalculatorTest.kt` (100% GREEN)**:
   - Тест расчёта скорости по длительности трека и высоте скролла.
   - Тест фоллбэка при неизвестной длительности (`durationMs = 0`).
   - Тест переключения и ограничения множителей `0.5x .. 2.5x`.
   - Тест расчёта дельты скролла за фрейм.
2. **Интерактивный UI в NowPlayingScreen (вкладка «Аккорды»)**:
   - Присутствует виджет управления автопрокруткой с кнопкой Play/Pause, множителем `[-] 1.0x [+]` и кнопкой сброса `⤾`.
   - При нажатии Play страница плавно прокручивается вниз со скоростью, рассчитанной по длительности трека.
   - Ручной скролл пальцем приостанавливает автопрокрутку на 2.5 секунды, не сбивая состояние.
   - Достижение конца текста автоматически переводит кнопку в состояние паузы.
3. Полный сьют unit-тестов проекта (`testDebugUnitTest`): 100% PASS (145+ тестов).
4. Успешная сборка `assembleDebug` и деплой на физическое устройство POCO M7 по ADB.
