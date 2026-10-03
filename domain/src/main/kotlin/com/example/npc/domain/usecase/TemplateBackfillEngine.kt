package com.example.npc.domain.usecase

import com.example.npc.core.model.backfill.BackfillCriteria
import com.example.npc.core.model.backfill.BackfillReport
import com.example.npc.core.model.backfill.BackfillTargetTemplate

/**
 * Интерфейс движка исторического применения шаблонов (Backfill Execution Engine).
 */
interface TemplateBackfillEngine {

    /**
     * Выполняет бэкфилл нового/активированного шаблона по историческим событиям того же источника.
     *
     * @param template Спецификация целевого шаблона.
     * @param criteria Параметры выборки исторических событий.
     * @return [BackfillReport] с результатами анализа и применения.
     */
    suspend fun executeBackfill(
        template: BackfillTargetTemplate,
        criteria: BackfillCriteria = BackfillCriteria.Default
    ): BackfillReport
}
