# CON-PCM-000: CastCodec: явное объявление PCM режима

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: shared/src/commonMain/kotlin/com/lirix/app/connect/proto/LirixConnectMsg.kt
Зависимости: CON-PCM-QA

Разрешенное изменение — только enum CastCodec: сохранить OPUS, добавить PCM_S16LE. Это одна schema declaration, зарегистрированное исключение к правилу одна функция: у enum нет исполняемой функции. Остальные типы/поля/defaults не менять. Проверить serialization roundtrip enum в regression tests.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

