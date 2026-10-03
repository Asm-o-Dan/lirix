## Задача MEDIA-003: MediaPayloadMapper (сериализация и нормализация)

**Файлы:**
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/mapper/MediaPayloadMapper.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-media/overview.md#класс-mediapayloadmapper` (v1)

**Зависит от:** `MEDIA-002`

**Поведение:**
1. `toPayloadJson(session: ActiveMediaSession, endReason: MediaSessionEndReason?, endedAt: Instant?): String`:
   - Детерминированная сериализация полей сессии в JSON по RFC 8259 с правильным экранированием строк.
2. `toRawEvent(session: ActiveMediaSession, seq: Long, receivedAt: Instant, endReason: MediaSessionEndReason, endedAt: Instant): RawEvent`:
   - Вычисляет `dedupKey = EventNormalizer.computeDeduplicationKey(SourceId.MEDIA, session.packageName, json)`.
   - Формирует `RawEvent(id = 0L, seq = seq, source = SourceId.MEDIA, packageName = session.packageName, receivedAt = receivedAt, payloadJson = json, hash = dedupKey)`.
3. `toDomainEvent(rawEvent: RawEvent, session: ActiveMediaSession, endedAt: Instant): Event?`:
   - Если эффективная длительность `< 5000L` (5 сек) -> сессия считается пропуском трека/микро-сессией.
   - Заголовок `title = session.metadata.title ?: "Unknown Track"`.
   - Текст `text = if (session.metadata.artist != null) "${session.metadata.artist} - $title" else title`.
   - `threadKey = ThreadKey("${session.packageName}:media:${session.sessionId}")`.
   - Нормализует через `EventNormalizer.normalize(...)`.

**Критерий приёмки:**
- Канонический JSON экранирует все спецсимволы.
- Дедупликационный ключ детерминирован и устойчив к повторному формированию.
