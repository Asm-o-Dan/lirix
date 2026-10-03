## Задача P3-QA-EDITOR: Тест-дизайн и реализация тестов для :feature:editor

**Модуль:** `:feature:editor`  
**Целевые файлы тестов:**  
- `feature/editor/src/test/kotlin/com/example/npc/feature/editor/vm/EditorViewModelTest.kt`  
- `feature/editor/src/test/kotlin/com/example/npc/feature/editor/reducer/EditorReducerTest.kt`  
- `feature/editor/src/androidTest/kotlin/com/example/npc/feature/editor/ui/TemplateEditorUiTest.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#61-one-tap-template-editor-human-in-the-loop`  
**Контракт:** `.sdd/contracts/ui-features__domain-contracts.md#21-one-tap-template-editor`  

---

### Требования к тестам:

1. **EditorReducerTest:**
   - Чистые функции редьюсера: обработка интентов `AssignTokenRole`, `ToggleRefund`, `ChangeOpType`.
   - Проверка неизменяемости состояния (`ImmutableList`).

2. **EditorViewModelTest (Turbine):**
   - Запуск живой валидации с задержкой `debounce(150ms)`: отмена предыдущих запросов при быстром вводе.
   - Корректная блокировка кнопки «Сохранить» при обнаружении ошибок компиляции или конфликтов.
   - Обработка интента `SaveAndActivate`: отправка сайд-эффекта `SavedSuccessfully`.
   - Проверка отмены сохранения (Undo) в течение 8 секунд.

3. **TemplateEditorUiTest (Compose):**
   - Отображение интерактивной токенной сетки.
   - Тап по токену открывает меню назначения ролей.
   - Сквозной тест сценария: открытие диалога из `EventDetailsDialog` -> выбор ролей -> сохранение -> появление в списке активных правил.

### Критерий DoD:
- 100% PASS юнит- и Compose UI тестов редактора.
