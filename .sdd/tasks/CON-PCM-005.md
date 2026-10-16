# CON-PCM-005: DesktopConnectServer.handleMessage: формат и правдивое состояние

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopConnectServer.kt
Зависимости: CON-PCM-004

Изменять только private suspend fun handleMessage(msg: LirixConnectMsg, output: DataOutputStream). Менять лишь SetAudioCast branch: выключение/unsupported/open-failed statefalse, PCM48000/2 успешно только по openAudioLine result. Другие cases pairing иvolume оставить byte-for-byte. Unsupported OPUS диагностировать. Это не security fix.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

