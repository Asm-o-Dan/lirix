package com.example.npc.pipeline.runtime

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.extract.universal.node.UniversalExtractorNodeExecutor
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.example.npc.pipeline.runtime.node.builtin.ExtractUniversalNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtractUniversalNodeTest {

    @Test
    fun `extract universal node extracts maib temu notification on fallback`() {
        val delegate = UniversalExtractorNodeExecutor(
            inTextSlot = 0,
            inPackageNameSlot = 0,
            outTxRefSlot = 1,
            nextPc = 1
        )
        val node = ExtractUniversalNode(delegate)

        val frame = Frame(FrameLayout(longSlots = 2, doubleSlots = 2, refSlots = 4, textSlots = 2, textCapacity = 256))
        val buffer = EffectBuffer(16, 32, 512)

        frame.texts[0].set("Restituire 245,90 MDL TEMU.COM Card *1234 Sold: 12 345,67 MDL")
        frame.refs[0] = "md.maib.maibank"

        val result = node.execute(frame, buffer)
        assertTrue(result.isJump)

        val tx = frame.refs[1] as? FinancialTransaction
        assertNotNull(tx)
        assertEquals(24590L, tx!!.amount.minor)
        assertEquals(CurrencyCode.MDL, tx.amount.currency)
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals("Card *1234", tx.accountMask)
        assertEquals("TEMU.COM", tx.merchant)
        assertNotNull(tx.balance)
        assertEquals(1234567L, tx.balance!!.minor)
    }

    @Test
    fun `extract universal node does not override existing transaction`() {
        val delegate = UniversalExtractorNodeExecutor(
            inTextSlot = 0,
            inPackageNameSlot = 0,
            outTxRefSlot = 1,
            nextPc = 1
        )
        val node = ExtractUniversalNode(delegate)

        val frame = Frame(FrameLayout(2, 2, 4, 2, 256))
        val buffer = EffectBuffer(16, 32, 512)

        val preExistingTx = "ExistingStaticTx"
        frame.refs[1] = preExistingTx

        frame.texts[0].set("Restituire 245,90 MDL TEMU.COM Card *1234 Sold: 12 345,67 MDL")
        frame.refs[0] = "md.maib.maibank"

        node.execute(frame, buffer)
        assertEquals(preExistingTx, frame.refs[1])
    }
}
