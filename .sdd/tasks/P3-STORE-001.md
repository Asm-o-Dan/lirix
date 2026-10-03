## Задача P3-STORE-001: Реализовать Room v4 миграцию и сущности динамических шаблонов

**Модуль:** `:core:storage`  
**Целевые файлы:**  
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/DynamicTemplateEntity.kt`  
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/TemplateStatsEntity.kt`  
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/TemplateBankVersionEntity.kt`  
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/DynamicTemplateDao.kt`  
- `core/storage/src/main/kotlin/com/example/npc/core/storage/migration/MIGRATION_3_4.kt`  
**Контракт:** `.sdd/contracts/template-bank__runtime-store.md#2-спецификация-типов-данных-и-сущностей`  

---

### Описание:
Реализовать аддитивную миграцию базы данных с версии 3 на версию 4:
1. Создание таблиц `dynamic_template`, `template_stats`, `template_bank_version`, `template_bank_membership`.
2. Создание индексов:
   - `idx_dyn_tmpl_src_state` по `(sourceKey, state)`.
   - Уникальный индекс `idx_dyn_tmpl_hash` по `canonicalHash`.
3. Добавление колонок в `financial_transactions`:
   - `extractorKind TEXT NOT NULL DEFAULT 'STATIC'`
   - `templateId TEXT`
   - `bankVersion INTEGER NOT NULL DEFAULT 0`
   - `isRefund INTEGER NOT NULL DEFAULT 0`
4. Написание и верификация `MIGRATION_3_4.kt` в Room MigrationTestHelper.

### Критерии приёмки (DoD):
- Миграционный тест `migrationTest_3_to_4()` проходит успешно со 100% сохранением существующих данных событий и транзакций.
- Все Room DAO скомпилированы через KSP без предупреждений.
