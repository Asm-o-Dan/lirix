## Задача UI-SYNC-005: Отображение ревизии конвейера в TimelineItemRow и EventDetailsDialog

**Модуль:** `:ui:timeline`  
**Целевые файлы:**  
- `ui/timeline/src/main/kotlin/com/example/npc/ui/timeline/ui/components/TimelineItemRow.kt`
- `ui/timeline/src/main/kotlin/com/example/npc/ui/timeline/ui/components/EventDetailsDialog.kt`  

### Требования:
1. В `TimelineItemRow.kt`:
   - Если `event.pipelineRevisionId != null`, отобразить рядом с языковым бейджем компактный бейдж ревизии:
     ```kotlin
     if (event.pipelineRevisionId != null) {
         Surface(
             shape = RoundedCornerShape(4.dp),
             color = MaterialTheme.colorScheme.secondaryContainer,
             modifier = Modifier.padding(end = 6.dp)
         ) {
             Text(
                 text = "rev #${event.pipelineRevisionId}",
                 style = MaterialTheme.typography.labelSmall,
                 modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                 color = MaterialTheme.colorScheme.onSecondaryContainer
             )
         }
     }
     ```
2. В `EventDetailsDialog.kt`:
   - В блоке метаданных под движком классификации отобразить:
     ```kotlin
     if (event.pipelineRevisionId != null) {
         Text(
             text = "Ревизия конвейера: #${event.pipelineRevisionId}",
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.outline
         )
     }
     ```

### Критерии приёмки (DoD):
- [x] Корректный рендеринг ревизии конвейера на карточке и в диалоге.
- [x] UI-тесты проходят успешно.
