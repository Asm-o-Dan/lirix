package com.example.npc.feature.diagnostics

import androidx.compose.ui.graphics.Color
import com.example.npc.feature.diagnostics.model.BreakerInfoUi
import com.example.npc.feature.diagnostics.model.BreakerUiState
import com.example.npc.feature.diagnostics.model.DiagnosticsUiState
import com.example.npc.feature.diagnostics.model.QuarantinedTemplateUi
import com.example.npc.feature.diagnostics.model.TraceRowUi
import com.example.npc.feature.diagnostics.ui.BreakerColorClosed
import com.example.npc.feature.diagnostics.ui.BreakerColorHalfOpen
import com.example.npc.feature.diagnostics.ui.BreakerColorOpen
import com.example.npc.feature.diagnostics.ui.breakerStateColor
import com.example.npc.feature.diagnostics.ui.breakerStateLabel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BreakerStatusRowTest {

    @Test
    fun `breakerStateColor returns green for CLOSED, amber for HALF_OPEN, and red for OPEN`() {
        breakerStateColor(BreakerUiState.CLOSED) shouldBe BreakerColorClosed
        breakerStateColor(BreakerUiState.CLOSED) shouldBe Color(0xFF4CAF50)

        breakerStateColor(BreakerUiState.HALF_OPEN) shouldBe BreakerColorHalfOpen
        breakerStateColor(BreakerUiState.HALF_OPEN) shouldBe Color(0xFFFFB300)

        breakerStateColor(BreakerUiState.OPEN) shouldBe BreakerColorOpen
        breakerStateColor(BreakerUiState.OPEN) shouldBe Color(0xFFE53935)
    }

    @Test
    fun `breakerStateLabel returns correct human readable status string`() {
        breakerStateLabel(BreakerUiState.CLOSED) shouldBe "CLOSED"
        breakerStateLabel(BreakerUiState.HALF_OPEN) shouldBe "HALF_OPEN"
        breakerStateLabel(BreakerUiState.OPEN) shouldBe "OPEN"
    }

    @Test
    fun `BreakerInfoUi preserves node state and failure count`() {
        val breaker = BreakerInfoUi(
            nodeId = "stage-bank-finance",
            state = BreakerUiState.OPEN,
            failureCount = 5,
            lastFailureTimestamp = 1_700_000_123L
        )

        breaker.nodeId shouldBe "stage-bank-finance"
        breaker.state shouldBe BreakerUiState.OPEN
        breaker.failureCount shouldBe 5
        breaker.lastFailureTimestamp shouldBe 1_700_000_123L
    }

    @Test
    fun `TraceRowUi data model holds correct trace attributes`() {
        val trace = TraceRowUi(
            seq = 42L,
            eventId = 1001L,
            nodeId = "extract.universal",
            outcome = "PASS",
            durationUs = 1250L,
            templateId = "tmpl-maib-01"
        )

        trace.seq shouldBe 42L
        trace.eventId shouldBe 1001L
        trace.nodeId shouldBe "extract.universal"
        trace.outcome shouldBe "PASS"
        trace.durationUs shouldBe 1250L
        trace.templateId shouldBe "tmpl-maib-01"
    }

    @Test
    fun `QuarantinedTemplateUi preserves quarantine details`() {
        val quarantined = QuarantinedTemplateUi(
            id = "tmpl-err-01",
            sourceKey = "md.maib.app",
            pattern = "^Oplata (?<amount>\\d+)",
            reason = "Regexp syntax exception",
            updatedAt = 1_700_000_500L
        )

        quarantined.id shouldBe "tmpl-err-01"
        quarantined.sourceKey shouldBe "md.maib.app"
        quarantined.pattern shouldBe "^Oplata (?<amount>\\d+)"
        quarantined.reason shouldBe "Regexp syntax exception"
        quarantined.updatedAt shouldBe 1_700_000_500L
    }

    @Test
    fun `DiagnosticsUiState default values adhere to contracts specification`() {
        val state = DiagnosticsUiState()

        state.isLiveUpdateEnabled shouldBe true
        state.ingestQueueSize shouldBe 0
        state.ingestEventsPerMinute shouldBe 0
        state.activePipelineRevision shouldBe 0L
        state.activeBankVersion shouldBe 0L
        state.activeTemplatesCount shouldBe 0
        state.quarantinedTemplatesCount shouldBe 0
        state.financeWithoutPayloadAlertCount shouldBe 0
        state.breakers.isEmpty() shouldBe true
        state.recentTraces.isEmpty() shouldBe true
        state.quarantinedTemplates.isEmpty() shouldBe true
        state.selectedNodeFilter shouldBe null
        state.isPollingActive shouldBe false
    }
}
