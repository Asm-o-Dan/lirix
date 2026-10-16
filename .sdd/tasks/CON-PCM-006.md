# CON-PCM-006: DesktopConnectServer.writePcm: целые frames и output errors

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopConnectServer.kt
Зависимости: CON-PCM-005

Изменять только private fun writePcm(data: ByteArray), @Synchronized сохранить. Без active allowed cast не открывать line. Размер%4 валидировать до write, partial aligned writes обслужить циклом, при exception/zero/negative/unaligned return диагностировать, clearplaying иclose. Нельзя ни copyOf обрезкой, ни бесконечный retry. Acceptance с fake line960/960, invalid1193, partial, exception.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

