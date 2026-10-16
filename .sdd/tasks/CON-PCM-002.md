# CON-PCM-002: Интегрировать PCM в AndroidCastSourceManager.startCast

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: app/src/main/java/com/lirix/app/connect/cast/AndroidCastSourceManager.kt
Зависимости: CON-PCM-000, CON-PCM-001

Изменять только startCast и supporting private поля outgoingChunks: Channel<AudioChunk>? / senderJob: Job? и imports. Сигнатуру сохранить. Контракт control PCM1536, targetProjection, bounded single worker, per-packet seq/timestamp и диагностика по SPEC. Не трогать stopCast до CON-PCM-003. На этой промежуточной карточке cleanup старта обязателен; stop/restart acceptance после003. Не добавлять методов вне карточки. Исполнить AndroidCastSourceTest после updates QA.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

