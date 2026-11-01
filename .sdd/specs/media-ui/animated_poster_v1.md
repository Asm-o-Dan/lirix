# Animated Share Posters v1 — Specification

**ID:** `SPEC-ANIM-POSTER-01`  
**Зона:** `media-ui` / `share`  
**Статус:** APPROVED  
**Версия:** v1.0  

---

## 1. Scope

Три анимированных постера, экспортируемых как MP4 (H.264, 1080p, 24fps, 4 сек loop):

| # | Постер | Анимация | Описание |
|---|--------|----------|----------|
| 1 | **NowPlaying Vinyl** | Vinyl Spin + Lyrics Typewriter | Обложка вращается как пластинка, строка текста печатается посимвольно |
| 3 | **Wrapped Counter** | Odometer Roll | Числа (минуты, треки, артисты) крутятся до финального значения |
| 4 | **Wrapped Pulse** | Bar Growth + Spring Bounce | Столбики «Пульс суток» растут с упругим отскоком |

## 2. Цветовая палитра

Все цвета наследуются из активной темы `LirixThemeTokens.colors`:
- `accentPrimary` → неон, glow, акцентные элементы
- `surfacePrimary` → фон постера
- `surfaceCard` → фон карточек/столбиков
- `textPrimary` → основной текст
- `textSecondary` → подписи, мета
- `borderSubtle` → разделители, рамки

Никаких хардкод-цветов. Один и тот же постер в теме Spotify будет зелёным, в Yandex — жёлтым, в Obsidian — синим.

## 3. Технический стек

```
AnimatedPosterEngine (новый пакет: feature/share/animation/)
├── AnimationSpec          — data class: duration, fps, width, height, loopCount
├── PosterAnimator         — abstract: fun renderFrame(canvas: Canvas, t: Float, w: Int, h: Int)
│   ├── VinylSpinAnimator          — анимация #1
│   ├── CounterRollAnimator        — анимация #3  
│   └── PulseBarAnimator           — анимация #4
├── Mp4FrameEncoder        — MediaCodec(H.264) + MediaMuxer → File
├── AnimatedPosterPipeline — orchestrates: for t in frames → render → encode → mux
└── ThemeAnimationPalette  — snapshot LirixThemeTokens → immutable ARGB ints для Canvas
```

### 3.1 AnimationSpec
```kotlin
data class AnimationSpec(
    val durationMs: Long = 4000L,
    val fps: Int = 24,
    val width: Int = 1080,
    val height: Int = 1080, // или 1920 для Stories
    val loopable: Boolean = true
) {
    val totalFrames: Int get() = (durationMs * fps / 1000).toInt()
}
```

### 3.2 PosterAnimator (абстракция)
```kotlin
abstract class PosterAnimator(val palette: ThemeAnimationPalette) {
    /** t ∈ [0.0 .. 1.0] — нормализованное время кадра */
    abstract fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int)
}
```

### 3.3 Mp4FrameEncoder
- `MediaCodec.createEncoderByType("video/avc")` с `COLOR_FormatSurface`
- `MediaMuxer(path, OutputFormat.MUXER_OUTPUT_MPEG_4)`
- Input Surface: `encoder.createInputSurface()` → `Canvas` через `lockHardwareCanvas()`
- Bitrate: 4 Mbps (высокое качество для 1080p при 24fps)
- I-frame interval: 1 сек
- Profile: Baseline (максимальная совместимость)

### 3.4 ThemeAnimationPalette
```kotlin
data class ThemeAnimationPalette(
    val background: Int,    // surfacePrimary → toArgb()
    val surface: Int,       // surfaceCard
    val accent: Int,        // accentPrimary  
    val textPrimary: Int,   // textPrimary
    val textSecondary: Int, // textSecondary
    val border: Int,        // borderSubtle
    val glowColor: Int      // accentPrimary с alpha 40%
)
```

## 4. Детали анимаций

### 4.1 Vinyl Spin + Lyrics Typewriter (NowPlaying)

**Timeline (4 сек = t ∈ [0..1]):**
- `t=[0.0..1.0]` — обложка непрерывно вращается (360°/4с = 1 оборот)
- `t=[0.0..0.15]` — fade-in фона и glow
- `t=[0.15..0.35]` — обложка scale-in с overshoot (0→1.05→1.0)
- `t=[0.35..0.55]` — название трека печатается посимвольно (typewriter)
- `t=[0.55..0.70]` — имя артиста fade-in
- `t=[0.70..1.0]` — строка текста печатается + glow пульсирует

**Элементы на Canvas:**
- Фон: `palette.background` + радиальный glow от центра (`palette.glowColor`)
- Виниловая пластинка: круглый clip обложки с чёрным кольцом и бороздками
- Тонарм: тонкая линия с шариком, статичная под углом ~25°
- Текст: `palette.textPrimary` (title), `palette.textSecondary` (artist), `palette.accent` (lyric line)
- Мерцающий курсор `|` после последнего напечатанного символа

### 4.2 Counter Roll (Wrapped)

**Timeline (4 сек):**
- `t=[0.0..0.1]` — fade-in заголовка «Итоги»
- `t=[0.1..0.25]` — заголовок периода slide-in слева
- `t=[0.25..0.75]` — три счётчика одновременно крутятся (разные скорости):
  - Минуты: быстро → замедление (ease-out-cubic)
  - Треки: средне
  - Артисты: медленно (приходит последним)
- `t=[0.75..0.85]` — топ-3 трека появляются каскадом (0.03с задержка между)
- `t=[0.85..1.0]` — glow pulse на акцентных числах

**Одометр-эффект:** Цифры рисуются с вертикальным смещением. Для числа N:
- Каждый разряд прокручивает столбик цифр 0-9 сверху вниз
- `clipRect` обрезает видимую область до высоты одной цифры
- Финальное значение фиксируется в `t=0.75`

### 4.3 Pulse Bar Growth (Wrapped)

**Timeline (4 сек):**
- `t=[0.0..0.15]` — fade-in заголовка «Когда звучала музыка»
- `t=[0.15..0.65]` — 4 столбика растут последовательно (stagger 0.1):
  - Столбик «Ночью»: `t=[0.15..0.40]` с spring overshoot
  - Столбик «Утром»: `t=[0.25..0.50]`
  - Столбик «Днём»: `t=[0.35..0.60]`
  - Столбик «Вечером»: `t=[0.45..0.70]`
- `t=[0.65..0.80]` — проценты fade-in над столбиками
- `t=[0.80..1.0]` — glow sweep слева→направо по столбикам

**Spring Bounce:** `f(t) = 1 - e^(-6t) * cos(4πt)` — затухающие колебания, перелёт ~15% и 1 отскок.

**Столбики:** Вертикальные `RoundRect` от нижней оси. Высота пропорциональна проценту прослушиваний. Цвет: `palette.accent` с градиентом к `palette.surface` у основания.

## 5. Интеграция в UI

### ShareFormatDialog
- Добавить третий формат: `ANIMATED_SQUARE` (1080×1080, MP4) и `ANIMATED_STORIES` (1080×1920, MP4)
- Или: toggle «Статика / Анимация» в существующем диалоге
- Preview: Compose `Canvas` с `rememberInfiniteTransition` проигрывает анимацию в реальном времени (масштабированную до размера превью)
- Export: фоновый `CoroutineWorker` с `ProgressBar`, результат → `Intent.ACTION_SEND` с `video/mp4`

### ShareManager
- Новый метод `shareAnimatedPoster(context, file: File)` с MIME `video/mp4`
- FileProvider URI для безопасной передачи

## 6. Ограничения и DoD

- Минимум Android 8.0 (API 26) — `MediaCodec` и `MediaMuxer` доступны
- Генерация в фоновом потоке (`Dispatchers.Default`), UI не блокируется
- Прогресс-бар: `$currentFrame / $totalFrames`
- Размер MP4 < 3 MB для Square, < 5 MB для Stories
- Длительность генерации < 10 секунд на среднем устройстве
- Unit-тесты: AnimationSpec, ThemeAnimationPalette, spring easing math
- Инструментальный тест: Mp4FrameEncoder создаёт валидный MP4 файл
