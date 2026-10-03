## Задача QA-SMS: Написание тестового набора для модуля :ingest:sms

**Файлы:**
- `ingest/sms/src/test/kotlin/com/example/npc/ingest/sms/mapper/SmsMapperTest.kt`
- `ingest/sms/src/test/kotlin/com/example/npc/ingest/sms/controller/SmsIngestControllerTest.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-sms/overview.md#детальная-спецификация-классов-и-методов` (v1)

**Зависит от:** `SMS-001` (GATE 5: TDD — тесты пишутся ДО реализации)

**Поведение:**
1. `SmsMapperTest`:
   - Тест сборки multipart SMS в `SmsContract`.
   - Тест формирования канонического `toPayloadJson` (экранирование кавычек, переносов, спецсимволов).
   - Тест маппинга в `RawEvent` с правильным `SourceId.SMS` и `packageName`.
   - Тест нормализации в `Event` (очистка невидимых символов, определение языка RU/EN, формирование `ThreadKey`).
2. `SmsIngestControllerTest`:
   - Тест `enqueueSms` с инкрементом `seq` и передачей в `Channel`.
   - Тест `processPayload`: немедленная вставка `RawEvent` до нормализации.
   - Тест отсечения дубликатов через `findDuplicate` (возврат существующего id без повторной вставки).
   - Тест обновления `SourceHealth` с актуальной глубиной очереди.

**Критерий приёмки:**
- Тесты компилируются (после объявления типов) и проверяют все требования спеки.
