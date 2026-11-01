# TASK-ANIM-004: VinylSpinAnimator (NowPlaying)

**Спецификация:** SPEC-ANIM-POSTER-01 §4.1  
**Зона:** media-ui/share  
**Файл:** `app/src/main/java/com/lirix/app/feature/share/animation/VinylSpinAnimator.kt`  
**Зависимости:** TASK-ANIM-001

## Что сделать
1. Extends `PosterAnimator`
2. Конструктор: `(palette, albumArt: Bitmap?, trackTitle: String, artistName: String, lyricLine: String?)`
3. `renderFrame(canvas, t, w, h)`:
   - Фон: `palette.background` + радиальный glow (`palette.glowColor`) с альфой пульсирующей по sin(t)
   - Виниловая пластинка: круглый clip обложки, вращение 360° * t, чёрное кольцо с тонкими бороздками
   - Тонарм: arc path под углом 25°, статичный
   - Title: typewriter эффект `t=[0.35..0.55]` — показываем title[0..progress]
   - Artist: fade-in `t=[0.55..0.70]` — альфа от 0 до 255
   - Lyric: typewriter `t=[0.70..1.0]` цветом `palette.accent`
   - Курсор `|` мерцает (видим при sin(t*20) > 0)

## DoD
- Компилируется
- Unit-тест: при t=0 ничего не отрисовано кроме фона; при t=1 все элементы полностью видны
- Визуальная проверка на устройстве (скриншот кадров t=0.0, 0.3, 0.5, 0.8, 1.0)
