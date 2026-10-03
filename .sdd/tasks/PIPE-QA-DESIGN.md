# Задача PIPE-QA-DESIGN: Написать тестовый сьют EventProcessingOrchestratorTest

**Файл:** `app/src/test/kotlin/com/example/npc/app/pipeline/EventProcessingOrchestratorTest.kt` (создать)
**Спека:** `.sdd/specs/app-pipeline/overview.md#секция-9`

## Сценарии тестирования (QA-ORCH-001..QA-ORCH-008):
1. **test_inTour_spam_classified_as_advertisement_and_zero_transactions()** (DoD 1):
   - Пакет Telegram (`com.radolyn.ayugram`), текст спама InTour.
   - Результат: категория `ADVERTISEMENT`, в `financial_transaction` 0 записей.
2. **test_apb_bank_notification_extracted_successfully()** (DoD 6):
   - Реальный пуш Агропромбанка ПМР.
   - Результат: категория `FINANCE`, транзакция создана с валютой `RUP`, расход, маппинг `eventId`.
3. **test_maib_declined_transaction_parsed()** (DoD 6):
   - Пуш MAIB с отказом по карте.
   - Результат: статус `TransactionStatus.DECLINED`, валюта `MDL`.
4. **test_parallel_submit_idempotency()** (DoD 4):
   - 20 параллельных корутин вызывают `submit(id)` для одного события.
   - Ровно 1 завершение, остальные 19 skipped.
5. **test_recovery_sweep_picks_up_unclassified_events()** (DoD 5):
   - В fake storage лежат 3 события со статусом UNCLASSIFIED.
   - Вызов `triggerRecoverySweep().join()`.
   - Все 3 события успешно обработаны.
6. **test_graceful_shutdown()** (DoD 8):
   - Запуск `start()`, отправка 10 событий, вызов `stop()`.
   - Состояние `STOPPED`, очередь опустошена.

## Критерий приёмки:
- Все тесты в файле проходят: `./gradlew.bat :app:testDebugUnitTest --tests "*.EventProcessingOrchestratorTest"`.
