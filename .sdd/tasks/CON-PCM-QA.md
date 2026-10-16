# CON-PCM-QA: Regression tests до реализации

Статус: READY после DONE зависимостей. Исполнитель: Antigravity, чат Connect PCM Repair.
Спека: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0 APPROVED.
Файл/область: QA test package
Зависимости: Нет

Разрешены только тестовые файлы:
- shared/src/commonTest/kotlin/com/lirix/app/connect/Pcm16PacketizerTest.kt (создать)
- app/src/test/java/com/lirix/app/connect/AndroidCastSourceTest.kt (расширить)
- desktop/src/test/kotlin/com/lirix/app/desktop/ConnectPcmReceiverTest.kt (создать)

Проверить все acceptance из SPEC. Для packetizer до появления декларации red compilation ожидаем, зафиксировать точный исход. Desktop tests допускается сначала оформить после доступности constructor seam; это явно отметить DEFERRED, затем реально запустить. Не реализовывать продуктовый код и не удалять assertions. Не открывать физическую звуковую карту. Проверять lossless bytes, call order, ошибочные размеры, state gating, partial writes, sequence/timestamp rollover. Java dynamic Proxy допустим без новых dependencies. Выделить результаты red в execution report.

## Общие ограничения и DoD

Одна функция/область этой карточки; разрешены только явно описанные supporting declarations/imports. Сигнатуры SPEC не угадывать. Нельзя менять другие фичи, добавлять dependencies, сбрасывать текущее дерево, коммитить/публиковать. При неоднозначности BLOCKED с конкретным вопросом в .sdd/reports/CONNECT_PCM_EXECUTION.md. DoD: regression tests по SPEC, отчет diff/команд/итогов; независимый review требуется отдельно, DONE исполнителя не равно принятию.

