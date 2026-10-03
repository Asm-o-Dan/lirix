## Задача P3-STORE-002: Расширить StorageGateway мультивалютными агрегациями

**Модуль:** `:core:storage`  
**Целевые файлы:**  
- `core/model/src/main/kotlin/com/example/npc/core/storage/StorageGateway.kt`  
- `core/storage/src/main/kotlin/com/example/npc/core/storage/StorageGatewayImpl.kt`  
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/FinancialTransactionDao.kt`  
**Контракт:** `.sdd/contracts/ui-features__domain-contracts.md#3-расширенный-контракт-storagegateway-для-аналитики`  

---

### Описание:
Реализовать эффективные SQL-запросы для финансовой аналитики без загрузки всех транзакций в память приложения:
1. Метод `observeAggregatedTotals`:
   ```sql
   SELECT 
       currency,
       SUM(CASE WHEN transaction_type = 'DEBIT' THEN amount_minor ELSE 0 END) AS expenseMinor,
       SUM(CASE WHEN transaction_type = 'CREDIT' AND is_refund = 0 THEN amount_minor ELSE 0 END) AS incomeMinor,
       SUM(CASE WHEN transaction_type = 'CREDIT' AND is_refund = 1 THEN amount_minor ELSE 0 END) AS refundMinor,
       COUNT(*) AS txCount
   FROM financial_transactions
   WHERE timestamp >= :fromMs AND timestamp < :toMs
     AND (:statusFilter IS NULL OR status = :statusFilter)
   GROUP BY currency
   ```
2. Поддержка режима `RefundCalculationMode`:
   - `REDUCE_EXPENSE`: чистый расход = `expenseMinor - refundMinor`.
   - `TREAT_AS_INCOME`: доход = `incomeMinor + refundMinor`.
3. Поддержка фильтра `includeSuggested`.
4. Метод `pagedTransactionsByPeriod`: постраничный доступ через AndroidX Paging 3.

### Критерии приёмки (DoD):
- `EXPLAIN QUERY PLAN` подтверждает использование индекса `(timestamp, currency, transaction_type)`.
- Время выполнения агрегации на 10 000 записей $\le 5$ мс.
- Реактивный Flow обновляется при вставке новых транзакций.
