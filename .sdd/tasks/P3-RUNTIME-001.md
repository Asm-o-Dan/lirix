## Задача P3-RUNTIME-001: Реализовать RuntimeGeneration и атомарную горячую подмену

**Модуль:** `:pipeline:runtime`  
**Целевые файлы:**  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/hotswap/RuntimeGeneration.kt`  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/hotswap/ActiveGenerationProvider.kt`  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/hotswap/ActiveGenerationProviderImpl.kt`  
**Контракт:** `.sdd/contracts/template-bank__runtime-store.md#2-спецификация-типов-данных-и-сущностей`  

---

### Описание:
Реализовать единый атомарный контейнер поколения рантайма (**ADR-304**):
1. Класс `RuntimeGeneration(val pipeline: CompiledPipeline, val bank: CompiledTemplateBank, val generationId: Long)`.
2. Провайдер `ActiveGenerationProvider` на базе `AtomicReference<RuntimeGeneration>`:
   - Метод `current(): RuntimeGeneration` (строго 0 аллокаций, volatile чтение O(1)).
   - Метод `swap(next: RuntimeGeneration): RuntimeGeneration` (CAS-замена, монотонный `generationId`).
3. Защита от рассогласования: входящее событие NLS/SMS всегда видит синхронную пару «активный конвейер + активный банк шаблонов».

### Критерии приёмки (DoD):
- Stress-тест конкуренции: 10 000 параллельных событий при 100 одновременных вызовах `swap()` — 0 событий с рассинхронизированными ревизиями.
- Нулевое влияние на задержку горячего пути ($< 0.01$ мс).
