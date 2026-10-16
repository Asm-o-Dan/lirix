# CON-PCM-007: DesktopConnectServer.startUdpAudioListener: gating и DatagramPacket reset

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopConnectServer.kt
Зависимости: CON-PCM-006

Изменять только private fun startUdpAudioListener(ds: DatagramSocket). packet.length=buf.size перед каждым receive. Не auto-open line. Play только activeallowed+existingline; malformed size diagnostics, nextvalid still processed, stop→lateUDP dropped. Catch CancellationException rethrow. Журналы не содержат PCM/secrets. Полный acceptance localhost ephemeral socket+fake line, no sound hardware. После этой карточки провести checks SPEC и собрать APK; evidence/status в report.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

