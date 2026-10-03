package com.example.npc.domain.usecase

import com.example.npc.core.model.backfill.BackfillCriteria
import com.example.npc.core.model.backfill.BackfillReport
import com.example.npc.core.model.backfill.BackfillTargetTemplate

/**
 * UseCase фонового применения шаблона к историческим событиям (бэкфилл).
 *
 * Отвечает за:
 * 1. Запуск процесса бэкфилла для нового или обновленного шаблона.
 * 2. Обеспечение сохранности пользовательских правок (USER_EDITED, USER_CONFIRMED).
 * 3. Формирование отчета о количестве проанализированных, извлеченных и пропущенных событий.
 */
class BackfillTemplateUseCase(
    private val backfillEngine: TemplateBackfillEngine
) {

    /**
     * Запускает бэкфилл шаблона на историческом корпусе.
     *
     * @param template Целевой шаблон для применения.
     * @param criteria Критерии выборки событий (временной диапазон, лимит).
     * @return [BackfillReport] со сводной статистикой выполнения.
     */
    suspend operator fun invoke(
        template: BackfillTargetTemplate,
        criteria: BackfillCriteria = BackfillCriteria.Default
    ): BackfillReport {
        return backfillEngine.executeBackfill(template, criteria)
    }
}
