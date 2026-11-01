# TASK-ANIM-002: Mp4FrameEncoder

**Спецификация:** SPEC-ANIM-POSTER-01 §3.3  
**Зона:** media-ui/share  
**Файл:** `app/src/main/java/com/lirix/app/feature/share/animation/Mp4FrameEncoder.kt`  
**Зависимости:** TASK-ANIM-001

## Что сделать
1. `Mp4FrameEncoder(outputFile: File, spec: AnimationSpec)` — создаёт MediaCodec H.264 encoder + MediaMuxer
2. Метод `encodeFrame(bitmap: Bitmap)` — подаёт кадр в encoder
3. Метод `finish()` — flush, stop, release MediaCodec + MediaMuxer
4. Bitrate 4 Mbps, I-frame 1 sec, Baseline profile
5. Input через `Bitmap` → `Canvas` на `InputSurface` (или через ByteBuffer COLOR_FormatYUV420)

## DoD
- Компилируется в :app
- Инструментальный тест (или Robolectric mock): encoder создаётся без крэша, finish() освобождает ресурсы
- Генерация 10 одноцветных кадров → валидный .mp4 файл > 0 bytes
