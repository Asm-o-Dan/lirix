package com.example.npc.domain.usecase

import com.example.npc.core.model.backfill.BackfillCriteria
import com.example.npc.core.model.backfill.BackfillReport
import com.example.npc.core.model.backfill.BackfillTargetTemplate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BackfillTemplateUseCaseTest {

    private lateinit var fakeBackfillEngine: FakeTemplateBackfillEngine
    private lateinit var useCase: BackfillTemplateUseCase

    @BeforeEach
    fun setUp() {
        fakeBackfillEngine = FakeTemplateBackfillEngine()
        useCase = BackfillTemplateUseCase(fakeBackfillEngine)
    }

    @Test
    @DisplayName("UseCase делегирует выполнение движку и возвращает отчет")
    fun `usecase delegates execution to engine and returns report`() = runTest {
        val template = BackfillTargetTemplate(
            id = "tmpl-100",
            sourcePackage = "com.apb.mobile",
            pattern = "Oplata (?P<amount>\\d+)",
            requiredLiterals = listOf("Oplata")
        )
        val criteria = BackfillCriteria(maxEventsLimit = 500)

        fakeBackfillEngine.stubReport = BackfillReport(
            templateId = "tmpl-100",
            totalAnalyzedEvents = 500,
            extractedTransactionsCount = 420,
            skippedManualEditsCount = 35,
            executionDurationMs = 150L
        )

        val result = useCase(template, criteria)

        assertEquals("tmpl-100", result.templateId)
        assertEquals(500, result.totalAnalyzedEvents)
        assertEquals(420, result.extractedTransactionsCount)
        assertEquals(35, result.skippedManualEditsCount)
        assertEquals(1, fakeBackfillEngine.invocations.size)
        assertEquals(template, fakeBackfillEngine.invocations[0].first)
        assertEquals(criteria, fakeBackfillEngine.invocations[0].second)
    }

    private class FakeTemplateBackfillEngine : TemplateBackfillEngine {
        val invocations = mutableListOf<Pair<BackfillTargetTemplate, BackfillCriteria>>()
        var stubReport = BackfillReport("test", 0, 0, 0)

        override suspend fun executeBackfill(
            template: BackfillTargetTemplate,
            criteria: BackfillCriteria
        ): BackfillReport {
            invocations.add(template to criteria)
            return stubReport
        }
    }
}
