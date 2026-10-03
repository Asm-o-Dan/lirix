# Межзонный контракт: Core Storage ↔ UI Timeline 2.0

**Версия:** FROZEN v2  
**Дата заморозки:** 2026-09-27  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Провайдер: `zone/core-storage` (`:core:storage`)
- Потребитель: `zone/ui-timeline` (`:ui:timeline`), `zone/app-lifecycle` (`:app`)

---

### 1. Интерфейс `StorageGateway` (сторона UI Timeline 2.0)

```kotlin
package com.example.npc.core.storage

import com.example.npc.core.model.Event
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.FinancialTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface StorageGateway {

    /**
     * Реактивный холодный поток последних нормализованных событий для экрана Timeline.
     * Эмитит свежий список при подписке и при любой мутации таблицы event.
     *
     * @param limit максимальное количество событий в выборке (limit > 0)
     * @return Flow списков событий, отсортированных по ts DESC, id DESC
     * @throws IllegalArgumentException если limit <= 0
     */
    fun observeEvents(limit: Int): Flow<List<Event>>

    /**
     * Реактивный холодный поток последних финансовых транзакций для ленты Timeline 2.0.
     * Эмитит свежий список при любой мутации таблицы financial_transaction.
     *
     * @param limit максимальное количество транзакций (limit > 0)
     * @return Flow списков транзакций, отсортированных по occurred_at DESC, id DESC
     * @throws IllegalArgumentException если limit <= 0
     */
    fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>>

    /**
     * Реактивный холодный поток состояния здоровья всех зарегистрированных источников.
     * Эмитит список при подписке и при каждом обновлении source_health.
     *
     * @return Flow списков SourceHealth, отсортированных по имени source ASC
     */
    fun observeSourceHealth(): Flow<List<SourceHealth>>

    /**
     * Фиксация пользовательской ручной коррекции категории события (Feedback Loop):
     * 1. Обновляет категорию события в таблице event (is_user_corrected = 1, engine_used = 'USER').
     * 2. Выполняет атомарный upsert пользовательского шаблона в user_prototype (инкремент support_count).
     *
     * @param eventId Идентификатор корректируемого события.
     * @param category Новая категория, выбранная пользователем.
     */
    suspend fun recordUserCorrection(eventId: Long, category: Category)

    /**
     * Расширенная версия фиксации коррекции с явным указанием отпечатка контента и пакета.
     */
    suspend fun recordUserCorrection(
        eventId: Long,
        packageName: String,
        contentFingerprint: String,
        newCategory: Category,
        correctedAt: Instant = Instant.now()
    )

    /**
     * Экспорт событий и финансового журнала в формат JSON.
     * Выполняется в транзакции чтения (Read-Only).
     *
     * @return Валидная JSON-строка со всеми событиями и транзакциями.
     */
    suspend fun exportEventsJson(): String

    /**
     * Псевдоним для полной выгрузки всех сущностей БД (Фаза 0 + Фаза 1).
     */
    suspend fun exportAllToJson(): String = exportEventsJson()

    /**
     * Полная безопасная очистка всех пользовательских данных приложения.
     * Очищает таблицы: raw_event, event, financial_transaction, user_prototype.
     * Сбрасывает метрики source_health.
     * Выполняется в единой транзакции и уведомляет всех активных Flow-наблюдателей.
     */
    suspend fun clearAllData()

    /**
     * Псевдоним для совместимости с кодом Фазы 0.
     */
    suspend fun deleteAllData() = clearAllData()
}
```

---

### 2. Гарантии и протокол взаимодействия (GATE 3)

1. **Реактивное связывание в Timeline 2.0:**
   - ViewModel в `:ui:timeline` объединяет потоки `observeEvents` и `observeTransactions` через оператор `combine`, сопоставляя финансовые данные с событиями по `eventId`.
2. **Атомарность обратной связи:**
   - Вызов `recordUserCorrection` выполняется в транзакции Room (`withTransaction`), гарантируя согласованность флага `is_user_corrected` на событии и счетчика `support_count` в таблице прототипов.
3. **Безопасность UI:**
   - Модуль `:ui:timeline` полностью изолирован от прямого доступа к Room DAO и не генерирует прямых SQL-запросов.
