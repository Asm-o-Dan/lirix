## Задача REPLAY-P2-003: Реализовать ReplayEventSourceDao и ReplayHistoricalEvent

**Файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/ReplayEventSourceDao.kt` (создать)  
**Модуль:** `:core:storage`  
**Спека:** `.sdd/specs/pipeline-replay/overview.md#33-доступ-к-историческим-данным-через-replayeventsourcedao`  
**Контракт:** `.sdd/contracts/pipeline-replay__runtime.md#5-контракт-чтения-исторического-корпуса-replayeventsourcedao`  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Query

data class ReplayHistoricalEvent(
    val eventId: Long,
    val rawId: Long,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val historicalCategory: String,
    val historicalConfidence: Double,
    val historicalTransactionJson: String?
)

@Dao
interface ReplayEventSourceDao {

    /**
     * Постраничная выборка (Keyset Pagination) событий без блокировки базы.
     */
    @Query("""
        SELECT e.id AS eventId, e.raw_id AS rawId, r.package_name AS packageName,
               e.title AS title, e.text AS text, e.ts AS postTime,
               e.category AS historicalCategory, e.confidence AS historicalConfidence,
               ft.amount_minor AS historicalTransactionJson
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        LEFT JOIN financial_transaction ft ON ft.event_id = e.id
        WHERE e.id > :afterId AND e.ts >= :startTime AND e.ts <= :endTime
        ORDER BY e.id ASC
        LIMIT :limit
    """)
    suspend fun getEventsAfterId(
        afterId: Long,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): List<ReplayHistoricalEvent>

    /**
     * Быстрый подсчет общего количества событий в интервале для отображения прогресса.
     */
    @Query("""
        SELECT COUNT(e.id)
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        WHERE e.ts >= :startTime AND e.ts <= :endTime
    """)
    suspend fun countEvents(
        startTime: Long,
        endTime: Long
    ): Int
}
```

**Поведение:**
1. Предоставляет высокопроизводительный Room DAO для последовательного чтения исторических данных уведомлений в симуляторе без накладных расходов и без блокировок SQLite WAL.
2. `getEventsAfterId`:
   - Реализует Keyset Pagination через условие `e.id > :afterId ORDER BY e.id ASC LIMIT :limit`.
   - Заменяет медленный `OFFSET`, обеспечивая константное время выборки $O(1)$ на страницу независимо от глубины выборки в 7-дневной базе догфудинга.
   - Объединяет (`JOIN`) данные из `event`, `raw_event` (имя пакета `package_name`) и `financial_transaction` (`amount_minor` или метаданные транзакции).
3. `countEvents`:
   - Быстро вычисляет `COUNT(e.id)` для инициализации прогресс-бара `ReplayStatus.Running(totalCount)`.

**Ошибки:**
- При сбоях чтения SQLite выбрасывает `android.database.sqlite.SQLiteException`.
- При отрицательном `limit` Room возвращает пустой список или выбрасывает исключение.

**Граничные случаи:**
- В базе нет записей за период `[startTime, endTime]` -> `countEvents` возвращает `0`, `getEventsAfterId` возвращает `emptyList()`.
- `afterId = 0L` -> выборка начинается с самого первого исторического события.
- Уведомление не имеет связанной финансовой транзакции -> `historicalTransactionJson = null` (благодаря `LEFT JOIN`).

**Запрещено:**
- Использовать оператор `OFFSET` в SQL-запросах (строгий запрет из-за деградации на eMMC/UFS Poco M7).
- Выполнять мутирующие запросы (`INSERT`, `UPDATE`, `DELETE`) — DAO строго Read-Only.
- Менять имена колонок и псевдонимов (`AS eventId`, `AS rawId`, `AS packageName`, `AS postTime`).

**Критерий приёмки:**
- Файл скомпилирован в модуле `:core:storage`.
- Добавлен метод в `AppDatabase` (или доступ через `Database`).
- Интеграционный тест Room на in-memory базе данных:
  * Вставка тестовых записей в `raw_event`, `event`, `financial_transaction`.
  * Выборка первой страницы `limit = 100` c `afterId = 0`.
  * Выборка второй страницы с `afterId = lastEventId`.
  * Проверка корректности маппинга полей и nullable `historicalTransactionJson`.
