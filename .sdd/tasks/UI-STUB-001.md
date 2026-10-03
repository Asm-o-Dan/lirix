# Задача UI-STUB-001: Реальное сохранение шаблонов в БД и устранение заглушек

## 1. Контекст
В `TimelineScreen.kt` (строка 155):
```kotlin
val editorViewModel = remember(eventToEdit.id) {
    EditorViewModel(
        eventId = eventToEdit.id,
        packageName = eventToEdit.packageName ?: "com.example.bank",
        rawText = eventToEdit.text
    )
}
```
Параметр `onSaveTemplate: (suspend (BuiltTemplate, EditorUiState) -> Unit)?` не передан (равен `null`).
Из-за этого нажатие «Сохранить и активировать» показывает Toast, но шаблон не сохраняется в базу данных `dynamic_template`.

## 2. Требования к реализации
1. **Связка сохранения:**
   - Внедрить или передать в `TimelineScreen` (через `TimelineViewModel` или Hilt) доступ к сохранению шаблонов (`TemplateBankManager` или метод в `TimelineViewModel.saveTemplate(template, state)`).
   - Преобразовать `BuiltTemplate` в `DynamicTemplateDraft`:
     - `sourceKey` = `packageName`
     - `pattern` = `template.pattern`
     - `sampleText` = `state.rawText`
     - `bindingsJson` = сериализованный `template.decomposedSpec` (или JSON bindings)
     - `constantsJson` = сериализованный `template.constants`
     - `amountFormatJson` = сериализованный `template.amountFormat`
   - Сохранить через `TemplateBankManager.registerDraft(draft, initialState = TemplateState.ACTIVE)` и вызвать `promoteToActive(templateId)`.
2. **Аудит других UI заглушек в модуле `:ui:timeline`:**
   - Проверить `onExportJsonClicked()`, `onDeleteAllClicked()`, `CategoryCorrectionDialog`, детали транзакции — убедиться, что все действия пользователя доходят до БД/StorageGateway.
3. **Верификация:**
   - Юнит-тесты на вызов сохранения шаблона.
   - Прогон `.\gradlew.bat :ui:timeline:testDebugUnitTest --no-daemon`.
