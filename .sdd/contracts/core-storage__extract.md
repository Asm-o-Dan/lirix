# Межзонный контракт: Core Storage ↔ Extract Finance (Персистентный финансовый реестр)

**Версия:** FROZEN v2  
**Дата заморозки:** 2026-09-27  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Провайдер: `zone/core-storage` (`:core:storage`)
- Потребители: `zone/extract-finance` (`:extract:finance`), `zone/app-lifecycle` (`:app`), `zone/ui-timeline` (`:ui:timeline`)

---

### 1. DDL схемы таблицы `financial_transaction` (Room v2)

```sql
CREATE TABLE IF NOT EXISTS `financial_transaction` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `event_id` INTEGER,
    `bank` TEXT NOT NULL,
    `direction` TEXT NOT NULL,
    `amount_minor` INTEGER NOT NULL,
    `currency` TEXT NOT NULL,
    `balance_minor` INTEGER,
    `balance_currency` TEXT,
    `merchant` TEXT,
    `account_mask` TEXT,
    `occurred_at` INTEGER NOT NULL,
    `extractor_id` TEXT NOT NULL,
    `extractor_version` INTEGER NOT NULL,
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`event_id`) REFERENCES `event` (`id`) ON UPDATE NO ACTION ON DELETE SET NULL
);

-- Индексы для ускорения выборок и целостности
CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_transaction_event_id` 
    ON `financial_transaction` (`event_id`);

CREATE INDEX IF NOT EXISTS `index_financial_transaction_occurred_at` 
    ON `financial_transaction` (`occurred_at`);

CREATE INDEX IF NOT EXISTS `index_financial_transaction_bank` 
    ON `financial_transaction` (`bank`);
```

> [!IMPORTANT]
> **Политика целостности `ON DELETE SET NULL`:**  
> При удалении старых событий из таблицы `event` (например, при ротации логов или очистке кэша) финансовая транзакция **НЕ УДАЛЯЕТСЯ**, а её колонка `event_id` обнуляется. Это гарантирует сохранность финансового журнала пользователя на устройстве Poco M7.

---

### 2. Методы `StorageGateway` для финансового реестра

```kotlin
package com.example.npc.core.storage

import com.example.npc.core.model.finance.FinancialTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface FinancialStorageGateway {

    /**
     * Сохраняет финансовую транзакцию в персистентную БД.
     *
     * @param transaction Неизменяемая доменная модель транзакции.
     * @return Первичный ключ (id) вставленной записи.
     */
    suspend fun insertTransaction(transaction: FinancialTransaction): Long

    /**
     * Реактивный холодный поток последних финансовых транзакций для ленты и аналитики.
     *
     * @param limit Максимальное количество записей (limit > 0).
     * @return Flow списка транзакций, отсортированных по occurred_at DESC, id DESC.
     */
    fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>>

    /**
     * Запрос транзакций за временной интервал (для недельной/месячной аналитики).
     */
    suspend fun getTransactionsBetween(from: Instant, to: Instant): List<FinancialTransaction>

    /**
     * Поиск транзакции по связанному event_id.
     */
    suspend fun findTransactionByEventId(eventId: Long): FinancialTransaction?
}
```

---

### 3. Инварианты персистентности (GATE 3)
1. Все денежные суммы в БД хранятся в неделимых целочисленных единицах `amount_minor INTEGER NOT NULL` (копейки, центы).
2. Валюты хранятся в виде текстового кода (`RUP`, `MDL`, `RUB`, `EUR`, `USD`).
3. При накате миграции `MIGRATION_1_2` выполняется валидация `PRAGMA foreign_key_check`, гарантирующая отсутствие битых ссылок.
