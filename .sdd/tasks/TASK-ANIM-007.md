# TASK-ANIM-007: ShareFormatDialog — Animated Poster Integration

**Спецификация:** SPEC-ANIM-POSTER-01 §5  
**Зона:** media-ui  
**Файлы:**
- `app/src/main/java/com/lirix/app/ui/components/ShareFormatDialog.kt` (модификация)
- `app/src/main/java/com/lirix/app/feature/share/ShareManager.kt` (модификация)
**Зависимости:** TASK-ANIM-003, TASK-ANIM-004, TASK-ANIM-005, TASK-ANIM-006

## Что сделать
1. Добавить toggle «Статика / Анимация» в `ShareFormatDialog`
2. При выборе «Анимация»:
   - Preview: Compose `Canvas` с `rememberInfiniteTransition`, масштабированный до превью
   - Кнопка «Сохранить» запускает `AnimatedPosterPipeline.generate()` в фоне
   - `LinearProgressIndicator` с прогрессом генерации
3. `ShareManager.shareAnimatedPoster(context, file: File)`:
   - MIME `video/mp4`
   - FileProvider URI
   - `Intent.ACTION_SEND`
4. Определить какой аниматор запускать:
   - Если NowPlaying → VinylSpinAnimator
   - Если Wrapped (section == FULL/TRACKS) → CounterRollAnimator
   - Если Wrapped (section == PULSE) → PulseBarAnimator

## DoD
- Компилируется :app:assembleDebug
- Toggle переключает режим без крэша
- Прогресс-бар отображается во время генерации
- Результат .mp4 шарится через Intent
