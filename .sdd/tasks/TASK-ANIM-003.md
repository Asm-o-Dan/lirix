# TASK-ANIM-003: AnimatedPosterPipeline

**Спецификация:** SPEC-ANIM-POSTER-01 §3  
**Зона:** media-ui/share  
**Файл:** `app/src/main/java/com/lirix/app/feature/share/animation/AnimatedPosterPipeline.kt`  
**Зависимости:** TASK-ANIM-001, TASK-ANIM-002

## Что сделать
1. `AnimatedPosterPipeline` — оркестратор: принимает `PosterAnimator` + `AnimationSpec`
2. Метод `suspend fun generate(animator, spec, outputFile, onProgress: (Float) → Unit): File`
3. Цикл: for frame in 0..totalFrames → создать Bitmap → canvas → animator.renderFrame(canvas, t) → encoder.encodeFrame(bitmap) → recycle bitmap
4. Работает на `Dispatchers.Default`, не блокирует UI
5. Поддержка cancellation через `currentCoroutineContext().ensureActive()`

## DoD
- Компилируется
- Unit-тест: onProgress вызывается totalFrames раз с нарастающим значением
- Pipeline с mock-animator (заливка цветом) генерирует файл
