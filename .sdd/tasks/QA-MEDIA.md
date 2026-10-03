## Задача QA-MEDIA: Написание набора тестов для модуля :ingest:media

**Файлы:**
- `ingest/media/src/test/kotlin/com/example/npc/ingest/media/model/MediaModelsTest.kt`
- `ingest/media/src/test/kotlin/com/example/npc/ingest/media/mapper/MediaPayloadMapperTest.kt`
- `ingest/media/src/test/kotlin/com/example/npc/ingest/media/detector/MediaSessionBoundaryDetectorTest.kt`
- `ingest/media/src/test/kotlin/com/example/npc/ingest/media/recovery/MediaSessionRecoveryManagerTest.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-media/overview.md#тестовые-сценарии-и-верификация` (v1)

**Зависит от:** `MEDIA-001` (GATE 5: TDD — тесты пишутся ДО реализации)

**Поведение:**
1. `MediaModelsTest`:
   - Валидация инвариантов `MediaMetadataSnapshot`, `MediaPlaybackSnapshot`, `ActiveMediaSession`.
2. `MediaPayloadMapperTest`:
   - Канонический JSON, экранирование, формирование `DeduplicationKey` и `Event`.
   - Проверка отсечения микро-сессий (< 5 сек) при конвертации в доменное событие.
3. `MediaSessionBoundaryDetectorTest`:
   - Переходы FSM: Play -> Pause -> Play (продолжение одной сессии).
   - Смена трека во время воспроизведения: закрытие предыдущей с `TRACK_CHANGED` и открытие новой.
   - Heartbeat timeout (5 мин) при отсутствии активности.
4. `MediaSessionRecoveryManagerTest`:
   - Обнаружение и закрытие висячих сессий при старте с `APP_RESTART`.

**Критерий приёмки:**
- Тесты проверяют все краевые случаи FSM медиа-сессий и гарантируют выполнение DoD пункта 4 (музыкальные сессии ≥ 95%).
