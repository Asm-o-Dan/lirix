## Задача P3-RUNTIME-005: Реализовать OrchestratorProbe и seqlock-чтение TraceRing

**Модуль:** `:pipeline:runtime`  
**Целевые файлы:**  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/diagnostics/OrchestratorProbe.kt`  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/diagnostics/OrchestratorProbeImpl.kt`  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/diagnostics/SeqlockTraceReader.kt`  
**Контракт:** `.sdd/contracts/template-bank__runtime-store.md#3-контракты-интерфейсов`  

---

### Описание:
Реализовать механизм неблокирующего чтения диагностических данных рантайма:
1. Lock-Free Seqlock алгоритм для считывания кольцевого буфера `TraceRing` (128 последних записей):
   - Писатель (горячий путь конвейера) инкрементирует счетчик `seq` до и после записи в слот.
   - Читатель (`OrchestratorProbe`) копирует данные слота и повторяет попытку, если `seq` был нечетным или изменился во время чтения.
   - Писатель **никогда не блокируется** на чтении мониторинга.
2. Сбор агрегированного снэпшота `DiagnosticsSnapshot`:
   - Статистика очередей (размер, пропускная способность).
   - Текущее поколение конвейера и банка шаблонов.
   - Состояние предохранителей `NodeCircuitBreaker` (Closed / Open / Half-Open).
   - Список трейсов без PII (текст уведомлений не выводится в трейсы).
3. Метод `resetCircuitBreaker(nodeId)` для ручного сброса из UI.

### Критерии приёмки (DoD):
- Многопоточный тест: снятие снэпшота при 100 000 параллельных записей в буфер выполняется без взаимных блокировок.
- Время выполнения `takeSnapshot()` $\le 0.5$ мс.
