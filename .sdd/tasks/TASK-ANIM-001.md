# TASK-ANIM-001: ThemeAnimationPalette + AnimationSpec

**Спецификация:** SPEC-ANIM-POSTER-01 §3.1, §3.4  
**Зона:** media-ui/share  
**Файл:** `app/src/main/java/com/lirix/app/feature/share/animation/ThemeAnimationPalette.kt`  
**Зависимости:** нет

## Что сделать
1. Создать пакет `feature/share/animation/`
2. `AnimationSpec` — data class с durationMs, fps, width, height, totalFrames
3. `ThemeAnimationPalette` — snapshot из `LirixThemeTokens.colors` в immutable ARGB int'ы
4. `PosterAnimator` — abstract class с `renderFrame(canvas, t, w, h)`
5. Spring easing: `fun springBounce(t: Float): Float` — формула затухающих колебаний

## DoD
- Компилируется в :app
- Unit-тест: AnimationSpec.totalFrames = (4000 * 24 / 1000) = 96
- Unit-тест: springBounce(0)=0, springBounce(1)≈1, overshoot > 1.0 в середине
- Unit-тест: ThemeAnimationPalette.glowColor имеет alpha ~40%
