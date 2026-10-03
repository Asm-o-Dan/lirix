## Задача P3-RUNTIME-003: Реализовать TemplateBankManager и FSM жизненного цикла шаблонов

**Модуль:** `:core:storage`, `:pipeline:runtime`  
**Целевые файлы:**  
- `core/storage/src/main/kotlin/com/example/npc/core/storage/template/TemplateBankManagerImpl.kt`  
- `pipeline/store/src/main/kotlin/com/example/npc/pipeline/store/template/TemplateBankManager.kt`  
**Контракт:** `.sdd/contracts/template-bank__runtime-store.md#3-контракты-интерфейсов`  

---

### Описание:
Реализовать потокобезопасный контроллер жизненного цикла шаблонов на базе `Mutex`:
1. Машина состояний шаблона:
   `DRAFT -> VALIDATED -> ACTIVE / SHADOW -> QUARANTINED / DISABLED / SUPERSEDED`.
2. Транзакционный метод `saveAndActivate`:
   - Сохранение записи в Room `dynamic_template`.
   - Создание новой записи `template_bank_version` (инкремент версии банка).
   - Компиляция банка целиком в фоновом потоке.
   - Атомарная замена `RuntimeGeneration` в рантайме.
3. Восстановление при рестарте приложения:
   - Автоматическая загрузка последней версии банка из БД.
   - Проверка `compilerVersion`: если версия компилятора изменилась, выполняется перекомпиляция всех шаблонов. Сбойные шаблоны изолируются в `QUARANTINED`.

### Критерии приёмки (DoD):
- Сериализация всех операций через `Mutex` — исключены гонки при одновременных вызовах.
- Откат транзакции при сбое компиляции банка: банк возвращается к предыдущей валидной версии.
