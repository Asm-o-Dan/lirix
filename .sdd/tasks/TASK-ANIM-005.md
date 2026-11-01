# TASK-ANIM-005: CounterRollAnimator (Wrapped)

**Спецификация:** SPEC-ANIM-POSTER-01 §4.2  
**Зона:** media-ui/share  
**Файл:** `app/src/main/java/com/lirix/app/feature/share/animation/CounterRollAnimator.kt`  
**Зависимости:** TASK-ANIM-001

## Что сделать
1. Extends `PosterAnimator`
2. Конструктор: `(palette, totalMinutes: Int, uniqueTracks: Int, uniqueArtists: Int, periodLabel: String, topTracks: List<Triple<String,String,Int>>)`
3. `renderFrame(canvas, t, w, h)`:
   - Заголовок «Итоги» fade-in `t=[0.0..0.1]`
   - Период slide-in слева `t=[0.1..0.25]` — translateX от -width до 0 с ease-out
   - Три одометра `t=[0.25..0.75]`:
     - Каждый разряд числа рисуется в clipRect. Столбик цифр 0-9 прокручивается вертикально
     - Финальная цифра фиксируется в конце интервала
     - Минуты: ease-out-cubic, быстрая остановка
     - Треки: ease-out-quad, средняя
     - Артисты: ease-out-sine, плавная
   - Подписи под числами: «минут», «треков», «артистов» цветом `palette.textSecondary`
   - Топ-3 cascade `t=[0.75..0.85]`: translateY от +100 до 0, stagger 30ms
   - Glow pulse `t=[0.85..1.0]` на акцентных числах

## DoD
- Компилируется
- Unit-тест: odometer digit offset для числа 42 при t=1.0 → показывает «4» и «2»
- Unit-тест: при t=0 — пустой фон; при t=1 — все элементы видны
