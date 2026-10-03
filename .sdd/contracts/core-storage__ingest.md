# Межзонный контракт: Ingest Modules ↔ Core Storage

**Версия:** FROZEN v1  
**Дата заморозки:** 2026-09-26  
**Стороны контракта:**
- Провайдер: `zone/core-storage` (`:core:storage`)
- Потребители: `zone/ingest-notification`, `zone/ingest-sms`, `zone/ingest-media`

---

### 1. Интерфейс `StorageGateway` (сторона Ingest)

```kotlin
package com.example.npc.core.storage

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth

interface StorageGateway {
    /**
     * Немедленная вставка сырого входящего события.
     * Вызывается ПЕРВЫМ действием консьюмера очереди до любой бизнес-логики.
     * При коллизии уникального ключа (hash) перехватывает SQLiteConstraintException
     * и возвращает существующий id записи без сбоя процесса.
     *
     * @param event сырое событие с монотонным seq и вычисленным hash
     * @return положительный id сохранённой или существующей записи (> 0)
     */
    suspend fun insertRawEvent(event: RawEvent): Long

    /**
     * Вставка нормализованного структурированного события.
     *
     * @param event нормализованное событие, ссылающееся на rawId = RawEvent.id
     * @return положительный id сохранённого события (> 0)
     */
    suspend fun insertEvent(event: Event): Long

    /**
     * Атомарное обновление метрик состояния источника (upsert).
     *
     * @param health метрика источника с актуальными lastEventAt, events24h, queueDepth, lastError
     */
    suspend fun upsertSourceHealth(health: SourceHealth)

    /**
     * Быстрый индексный поиск дубликата по криптографическому хешу SHA-256.
     *
     * @param key дедупликационный ключ
     * @return id существующего RawEvent или null, если дубликат отсутствует
     */
    suspend fun findDuplicate(key: DeduplicationKey): Long?
}
```

---

### 2. Гарантии и протокол взаимодействия

1. **Потокобезопасность и контекст:**
   - Все методы `StorageGateway` являются `suspend`-функциями и гарантированно выполняются в пуле `Dispatchers.IO`.
   - Вызывающая сторона может вызывать методы из любого контекста корутин, не опасаясь блокировки Main или OS-binder потоков.

2. **Гарантии целостности:**
   - `RawEvent.id` формируется базой данных автоинкрементно.
   - Поле `raw_event.hash` защищено ограничением `UNIQUE` на уровне базы данных SQLite.
   - `Event.rawId` ссылается на `raw_event.id` с каскадным удалением `ON DELETE CASCADE`.

3. **Обработка ошибок:**
   - Коллизии дедупликации (`UNIQUE constraint failed: raw_event.hash`) обрабатываются внутри `StorageGatewayImpl` прозрачно: метод `insertRawEvent` возвращает существующий `id`.
   - При аппаратных ошибках диска или нехватке памяти выбрасывается `android.database.sqlite.SQLiteException`.
