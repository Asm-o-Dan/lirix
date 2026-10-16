# CON-PCM-003: Очистка AndroidCastSourceManager.stopCast

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: app/src/main/java/com/lirix/app/connect/cast/AndroidCastSourceManager.kt
Зависимости: CON-PCM-002

Изменять только fun stopCast(): Unit, supporting imports допускаются. Cleanup channel/worker выполняется и если isCasting=false. SetAudioCast disabled с PCM_S16LE, cancellation не подавлять, логи ошибок. Сохранять прочее поведение и lifecycle. startCast в этой карточке не менять. Проверить stop и restart test.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

