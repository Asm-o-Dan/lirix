## Задача P3-RUNTIME-006: Реализовать Backfill Execution Engine для новых шаблонов

**Модуль:** `:feature:replay`, `:domain`  
**Целевые файлы:**  
- `feature/replay/src/main/kotlin/com/example/npc/feature/replay/backfill/TemplateBackfillEngine.kt`  
- `domain/src/main/kotlin/com/example/npc/domain/usecase/BackfillTemplateUseCase.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#34-горячая-активация-транзакционность`  

---

### Описание:
Реализовать фоновый процесс применения нового шаблона к историческим событиям:
1. Поиск исторических событий того же источника (`sourcePackage`), у которых отсутствует финансовый блок или статус `SUGGESTED`.
2. Прогон скомпилированного шаблона через `VirtualEffectEvaluator` (Sandbox-режим).
3. Идемпотентный upsert в таблицу `financial_transactions`:
   - Защита пользовательских данных: события со статусом `USER_EDITED` или `USER_CONFIRMED` **никогда не перезаписываются**.
   - Обновление статуса на `CONFIRMED_AUTO` с указанием нового `templateId` в провенансе.
4. Отчет о результате: количество обновленных транзакций.

### Критерии приёмки (DoD):
- Пользовательские правки не затираются автоматическим бэкфиллом.
- Тест на корректность бэкфилла по 100 историческим событиям.
