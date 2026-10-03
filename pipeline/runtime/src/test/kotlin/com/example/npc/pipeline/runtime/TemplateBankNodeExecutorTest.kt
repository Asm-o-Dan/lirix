package com.example.npc.pipeline.runtime

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.compiler.CompiledStage
import com.example.npc.pipeline.compiler.DebugInfo
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.example.npc.pipeline.runtime.hotswap.ActiveGenerationProvider
import com.example.npc.pipeline.runtime.hotswap.CompiledTemplate
import com.example.npc.pipeline.runtime.hotswap.CompiledTemplateBank
import com.example.npc.pipeline.runtime.hotswap.RuntimeGeneration
import com.example.npc.pipeline.runtime.node.builtin.TemplateBankNodeExecutor
import com.google.re2j.Pattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateBankNodeExecutorTest {

    private fun createPipeline(): CompiledPipeline {
        return CompiledPipeline(
            pipelineId = "test-pipe",
            revision = 1L,
            canonicalHash = "hash-1",
            compiledAtTimestamp = 1000L,
            dslVersion = 1,
            compilerVersion = 1,
            packageWhitelistSet = setOf("md.maib.maibank"),
            requiredInputMask = 0L,
            layout = FrameLayout(2, 2, 4, 2, 256),
            stages = arrayOf(object : CompiledStage {
                override val stageId: String = "s0"
                override val stageIndex: Int = 0
                override fun execute(frame: Frame): Int = Signal.PASS
            }),
            patterns = emptyArray(),
            debugInfo = DebugInfo(emptyMap(), emptyMap())
        )
    }

    @Test
    fun `template bank matches maib refund push and extracts transaction`() {
        val patternStr = """(?i)\Qrestituire\E\s+(?P<amount>\d+(?:,\d{2})?)\s+(?P<curr>mdl)\s+(?P<merchant>temu\.com)\s+\Qcard\E\s+(?P<card>\*\d{4})\s+\Qsold:\E\s+(?P<bal>\d+[\s\d]*(?:,\d{2})?)"""
        val template = CompiledTemplate(
            id = "tmpl-maib-1",
            sourceKey = "md.maib.maibank",
            priority = 100,
            pattern = Pattern.compile(patternStr),
            requiredLiterals = listOf("restituire", "card", "sold:"),
            constants = mapOf("opType" to "CREDIT", "isRefund" to "true"),
            bindingsJson = "{}"
        )

        val bank = CompiledTemplateBank(
            bankVersion = 1L,
            overrideTemplatesBySource = mapOf("md.maib.maibank" to listOf(template))
        )

        val generation = RuntimeGeneration(createPipeline(), bank, 1L)
        val provider = ActiveGenerationProvider.create(generation)

        val executor = TemplateBankNodeExecutor(
            inTextSlot = 0,
            inPackageNameSlot = 0,
            outTxRefSlot = 1,
            generationProvider = provider,
            nextPc = 1
        )

        val frame = Frame(FrameLayout(longSlots = 2, doubleSlots = 2, refSlots = 4, textSlots = 2, textCapacity = 256))
        val buffer = EffectBuffer(16, 32, 512)

        // Setup inputs
        frame.texts[0].set("restituire 245,90 mdl temu.com card *1234 sold: 12 345,67 mdl")
        frame.refs[0] = "md.maib.maibank"

        val result = executor.execute(frame, buffer)
        assertTrue(result.isJump)

        val tx = frame.refs[1] as? FinancialTransaction
        assertNotNull(tx)
        assertEquals(24590L, tx!!.amount.minor)
        assertEquals(CurrencyCode.MDL, tx.amount.currency)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals("*1234", tx.accountMask)
        assertEquals("temu.com", tx.merchant)
        assertNotNull(tx.balance)
        assertEquals(1234567L, tx.balance!!.minor)
    }

    @Test
    fun `prefilter skips regex matching when required literal is missing`() {
        val template = CompiledTemplate(
            id = "tmpl-maib-1",
            sourceKey = "md.maib.maibank",
            priority = 100,
            pattern = Pattern.compile(".*"), // broad pattern
            requiredLiterals = listOf("restituire"),
            constants = emptyMap(),
            bindingsJson = "{}"
        )

        val bank = CompiledTemplateBank(
            bankVersion = 1L,
            overrideTemplatesBySource = mapOf("md.maib.maibank" to listOf(template))
        )

        val generation = RuntimeGeneration(createPipeline(), bank, 1L)
        val provider = ActiveGenerationProvider.create(generation)

        val executor = TemplateBankNodeExecutor(
            inTextSlot = 0,
            inPackageNameSlot = 0,
            outTxRefSlot = 1,
            generationProvider = provider,
            nextPc = 1
        )

        val frame = Frame(FrameLayout(2, 2, 4, 2, 256))
        val buffer = EffectBuffer(16, 32, 512)

        // Text does NOT contain "restituire"
        frame.texts[0].set("oplata 100 mdl magazin")
        frame.refs[0] = "md.maib.maibank"

        executor.execute(frame, buffer)
        assertNull(frame.refs[1])
    }

    @Test
    fun `OPT-PIPE-001 slot-decomposed pipeline matches push and extracts all transaction fields`() {
        val template = CompiledTemplate(
            id = "tmpl-decomposed-1",
            sourceKey = "md.maib.maibank",
            priority = 100,
            anchorPattern = "(?i)(?:Пополнение счета|Перевод на карту|Зачисление)",
            slotRules = mapOf(
                "amount" to """(?i)(?P<amount>\d+(?:[.,]\d{2})?)\s*(?P<curr>RUP|MDL|USD|EUR|RUB)?""",
                "card" to """(?i)(?:карте|карту)\s*(?:[A-Za-zА-Яа-яЁё]+\s+)?(?P<card>\d*\*+\d+|\*\d+)""",
                "merchant" to """(?i)(?:от|в|списано)\s+(?P<merchant>[A-ZА-ЯЁ][a-zа-яё]+(?:\s+[A-ZА-ЯЁ]\.)?)""",
                "bal" to """(?i)(?:Остаток|Баланс):\s*(?P<bal>\d+(?:[.,]\d{2})?)"""
            ),
            requiredLiterals = listOf("карте"),
            constants = mapOf("opType" to "CREDIT")
        )

        val bank = CompiledTemplateBank(
            bankVersion = 1L,
            overrideTemplatesBySource = mapOf("md.maib.maibank" to listOf(template))
        )

        val generation = RuntimeGeneration(createPipeline(), bank, 1L)
        val provider = ActiveGenerationProvider.create(generation)

        val executor = TemplateBankNodeExecutor(
            inTextSlot = 0,
            inPackageNameSlot = 0,
            outTxRefSlot = 1,
            generationProvider = provider,
            nextPc = 1
        )

        val frame = Frame(FrameLayout(longSlots = 2, doubleSlots = 2, refSlots = 4, textSlots = 2, textCapacity = 256))
        val buffer = EffectBuffer(16, 32, 512)

        // Setup notification text
        frame.texts[0].set("Зачисление 150,50 MDL от Magazin в 14:00 карте *4321. Баланс: 1200,00 MDL")
        frame.refs[0] = "md.maib.maibank"

        val result = executor.execute(frame, buffer)
        assertTrue(result.isJump)

        val tx = frame.refs[1] as? FinancialTransaction
        assertNotNull(tx)
        assertEquals(15050L, tx!!.amount.minor)
        assertEquals(CurrencyCode.MDL, tx.amount.currency)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals("*4321", tx.accountMask)
        assertEquals("Magazin", tx.merchant)
        assertNotNull(tx.balance)
        assertEquals(120000L, tx.balance!!.minor)
    }

    @Test
    fun `OPT-PIPE-001 slot-decomposed pipeline rejects text when anchor pattern does not match`() {
        val template = CompiledTemplate(
            id = "tmpl-decomposed-1",
            sourceKey = "md.maib.maibank",
            priority = 100,
            anchorPattern = "(?i)(?:Пополнение счета|Перевод на карту|Зачисление)",
            slotRules = mapOf(
                "amount" to """(?i)(?P<amount>\d+(?:[.,]\d{2})?)\s*(?P<curr>RUP|MDL|USD|EUR|RUB)?""",
                "card" to """(?i)(?:карте|карту)\s*(?:[A-Za-zА-Яа-яЁё]+\s+)?(?P<card>\d*\*+\d+|\*\d+)"""
            ),
            requiredLiterals = listOf("карте")
        )

        val bank = CompiledTemplateBank(
            bankVersion = 1L,
            overrideTemplatesBySource = mapOf("md.maib.maibank" to listOf(template))
        )

        val generation = RuntimeGeneration(createPipeline(), bank, 1L)
        val provider = ActiveGenerationProvider.create(generation)

        val executor = TemplateBankNodeExecutor(
            inTextSlot = 0,
            inPackageNameSlot = 0,
            outTxRefSlot = 1,
            generationProvider = provider,
            nextPc = 1
        )

        val frame = Frame(FrameLayout(2, 2, 4, 2, 256))
        val buffer = EffectBuffer(16, 32, 512)

        // Text matches literal "карте", but does NOT match anchor ("Списание" vs "Зачисление/Пополнение")
        frame.texts[0].set("Списание комиссии 50 MDL по карте *1234")
        frame.refs[0] = "md.maib.maibank"

        executor.execute(frame, buffer)
        assertNull(frame.refs[1])
    }
}
