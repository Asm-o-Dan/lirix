# CON-PCM-001: Реализовать packetizePcm16Stereo

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: shared/src/commonMain/kotlin/com/lirix/app/connect/cast/Pcm16Packetizer.kt
Зависимости: CON-PCM-QA

Создать только одну top-level функцию packetizePcm16Stereo с точной сигнатурой SPEC. Все проверки, chunk=960, payload копии, seq/ts rollover, flags0 по SPEC. Никаких других public APIs или classes. Запустить Pcm16PacketizerTest и показать lossless1920, invalid1193, rollover.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

