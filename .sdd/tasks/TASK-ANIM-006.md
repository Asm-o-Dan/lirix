# TASK-ANIM-006: PulseBarAnimator (Wrapped)

**Спецификация:** SPEC-ANIM-POSTER-01 §4.3  
**Зона:** media-ui/share  
**Файл:** `app/src/main/java/com/lirix/app/feature/share/animation/PulseBarAnimator.kt`  
**Зависимости:** TASK-ANIM-001

## Что сделать
1. Extends `PosterAnimator`
2. Конструктор: `(palette, distribution: Map<TimeOfDaySlot, Float>)` — fraction по слотам суток
3. `renderFrame(canvas, t, w, h)`:
   - Заголовок «Когда звучала музыка» fade-in `t=[0.0..0.15]`
   - 4 вертикальных RoundRect столбика, stagger 0.1:
     - Ночью: `t=[0.15..0.40]` высота = fraction * maxH * springBounce(localT)
     - Утром: `t=[0.25..0.50]`
     - Днём: `t=[0.35..0.60]`
     - Вечером: `t=[0.45..0.70]`
   - Цвет столбика: вертикальный LinearGradient от `palette.accent` (верх) к `palette.surface` (низ)
   - Проценты над столбиками: fade-in `t=[0.65..0.80]`, текст «XX%» цветом `palette.accent`
   - Подписи под столбиками: «Ночью», «Утром», «Днём», «Вечером» цветом `palette.textSecondary`
   - Glow sweep `t=[0.80..1.0]`: полупрозрачная полоса `palette.glowColor` движется слева→направо по столбикам

## DoD
- Компилируется
- Unit-тест: springBounce overshoots > 1.0 в середине, converges к 1.0 в конце
- Unit-тест: при t=0 высота всех столбиков = 0; при t=1 высота = fraction * maxH
