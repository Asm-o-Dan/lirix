# CON-PCM-004: DesktopConnectServer.openAudioLine и тестовый seam

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopConnectServer.kt
Зависимости: CON-PCM-000, CON-PCM-QA

Изменять только openAudioLine: return Boolean, exact behavior SPEC. Единственное поддерживающее изменение — последний optional constructor arg private val audioLineFactory: (AudioFormat)->SourceDataLine с default AudioSystem.getLine(DataLine.Info(...)) as SourceDataLine. Signature существующих call sites source-compatible, default values сохранить. Ошибки открытия диагностировать/cleanup. Не переписывать другие методы пока. Test fake factory успех/отказ без hardware.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

