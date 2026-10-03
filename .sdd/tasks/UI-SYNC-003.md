## Задача UI-SYNC-003: Устранение искажений в EventUiMapper и расширение EventUiModel

**Модуль:** `:ui:timeline`  
**Целевые файлы:**  
- `ui/timeline/src/main/kotlin/com/example/npc/ui/timeline/model/EventUiModel.kt`
- `ui/timeline/src/main/kotlin/com/example/npc/ui/timeline/mapper/EventUiMapper.kt`  

### Входные контракты / сигнатуры:

В `EventUiModel.kt`:
- Добавить поле `val pipelineRevisionId: Long? = null` в первичный и вторичный конструкторы.

В `EventUiMapper.kt`:
- Заменить захардкоженный блок строк 87-142:
```kotlin
            val category = if (event.category != Category.UNCLASSIFIED) {
                event.category
            } else if (transaction != null) {
                Category.FINANCE
            } else {
                Category.UNCLASSIFIED
            }

            val confidence = if (event.confidence > 0.0f) {
                event.confidence
            } else if (transaction != null) {
                1.0f
            } else {
                0.0f
            }

            val engineUsed = if (event.engineUsed != Engine.NONE) {
                event.engineUsed
            } else if (transaction != null) {
                Engine.RULES
            } else {
                Engine.NONE
            }

            val isUserCorrected = event.isUserCorrected
```
- Использовать `event.contentFingerprint` и `event.pipelineRevisionId`:
```kotlin
                category = category,
                confidence = confidence,
                engineUsed = engineUsed,
                isUserCorrected = isUserCorrected,
                contentFingerprint = event.contentFingerprint,
                financialData = financialDataUi,
                pipelineRevisionId = event.pipelineRevisionId
```

### Инварианты:
1. UI-карточка точно отображает `event.category`, `confidence`, `engineUsed`, `isUserCorrected` из доменного объекта.
2. Никаких поддельных regex-эвристик, перезаписывающих выбор пользователя или работу классификатора.

### Критерии приёмки (DoD):
- [x] Все тесты `:ui:timeline:test` проходят успешно (100% PASS).
