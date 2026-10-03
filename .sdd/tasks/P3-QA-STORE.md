## Задача P3-QA-STORE: Тест-дизайн и реализация тестов для :core:storage и :pipeline:runtime

**Модуль:** `:core:storage`, `:pipeline:runtime`  
**Целевые файлы тестов:**  
- `core/storage/src/test/kotlin/com/example/npc/core/storage/migration/Migration3To4Test.kt`  
- `core/storage/src/test/kotlin/com/example/npc/core/storage/template/TemplateBankDaoTest.kt`  
- `core/storage/src/test/kotlin/com/example/npc/core/storage/StorageGatewayAggregatesTest.kt`  
- `pipeline/runtime/src/test/kotlin/com/example/npc/pipeline/runtime/hotswap/RuntimeGenerationSwapTest.kt`  
- `pipeline/runtime/src/test/kotlin/com/example/npc/pipeline/runtime/template/TemplateBankNodeExecutorTest.kt`  
- `pipeline/runtime/src/test/kotlin/com/example/npc/pipeline/runtime/diagnostics/SeqlockTraceReaderTest.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#5-хранение-и-жизненный-цикл-в-room-v4`  
**Контракт:** `.sdd/contracts/template-bank__runtime-store.md`  

---

### Требования к тестам:

1. **Migration3To4Test:**
   - Верификация `MIGRATION_3_4`: создание таблиц `dynamic_template`, `template_stats`, `template_bank_version`, `template_bank_membership`.
   - Проверка добавления колонок `extractorKind`, `templateId`, `bankVersion`, `isRefund` в `financial_transactions`.
   - Полное сохранение существующих данных при миграции реальной базы данных.

2. **TemplateBankDaoTest:**
   - Операции CRUD с шаблонами, поддержка состояний `ACTIVE`, `SHADOW`, `QUARANTINED`, `DISABLED`.
   - Инкремент монотонной версии банка `template_bank_version`.

3. **StorageGatewayAggregatesTest:**
   - SQL агрегация по валютам (`MDL`, `RUP`, `USD`, `EUR`, `RUB`).
   - Проверка режима `RefundCalculationMode`: вычитание возвратов из расходов (`REDUCE_EXPENSE`).
   - Фильтрация `includeSuggested`.

4. **RuntimeGenerationSwapTest:**
   - Stress-тест многопоточности: 10 000 параллельных вызовов обработки событий при 100 одновременных `swap()` вызовах `RuntimeGeneration`.
   - 0 событий с рассинхронизацией пары конвейера и банка.

5. **TemplateBankNodeExecutorTest:**
   - Быстрый префильтр по `requiredLiterals`.
   - Приоритеты: `OVERRIDE` срабатывает до статики, `FALLBACK` — после.
   - Изоляция режима `SHADOW` (не изменяет `R_TX`).

6. **SeqlockTraceReaderTest:**
   - Снятие снэпшота `TraceRing` без блокировки пишущего потока горячего цикла.

### Критерий DoD:
- 100% PASS всех тестов хранения и рантайма.
