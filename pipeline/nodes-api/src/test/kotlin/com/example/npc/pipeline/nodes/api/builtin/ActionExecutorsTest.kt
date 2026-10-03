package com.example.npc.pipeline.nodes.api.builtin

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.example.npc.pipeline.nodes.builtin.action.CreateTransactionActionExecutor
import com.example.npc.pipeline.nodes.builtin.action.DropEventActionExecutor
import com.example.npc.pipeline.nodes.builtin.action.SaveToStorageActionExecutor
import com.example.npc.pipeline.nodes.builtin.action.SetCategoryActionExecutor
import com.example.npc.pipeline.nodes.builtin.action.StopProcessingActionExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActionExecutorsTest {

    private lateinit var frame: Frame
    private lateinit var buffer: EffectBuffer

    @BeforeEach
    fun setUp() {
        val layout = FrameLayout(
            longSlots = 8,
            doubleSlots = 4,
            refSlots = 8,
            textSlots = 4,
            textCapacity = 256
        )
        frame = Frame(layout)
        buffer = EffectBuffer()
    }

    @Test
    fun setCategoryAction_writesToBufferAndJumps() {
        val executor = SetCategoryActionExecutor(
            categoryOrdinal = 3L,
            confidenceBits = java.lang.Double.doubleToRawLongBits(0.95),
            engineOrdinal = 1L,
            effectCode = EffectKindId.SET_CATEGORY,
            nextPc = 15
        )

        val res = executor.execute(frame, buffer)
        assertEquals(15, res.arg)
        assertEquals(1, buffer.effectCount)

        val view = buffer.view()
        assertEquals(EffectKindId.SET_CATEGORY, view.getKind(0))
        assertEquals(3L, view.getLongArg(0, 0))
        assertEquals(java.lang.Double.doubleToRawLongBits(0.95), view.getLongArg(0, 1))
        assertEquals(1L, view.getLongArg(0, 2))
    }

    @Test
    fun createTransactionAction_readsFrameAndWritesToBuffer() {
        val amountSlot = 0
        val curSlot = 1
        val txSlot = 0

        frame.longs[amountSlot] = 15000L
        frame.longs[curSlot] = 1L
        val dummyTx = Any()
        frame.refs[txSlot] = dummyTx

        val executor = CreateTransactionActionExecutor(
            inAmountMinorSlot = amountSlot,
            inCurrencyOrdinalSlot = curSlot,
            inTxRefSlot = txSlot,
            directionOrdinal = 0L,
            statusOrdinal = 1L,
            effectCode = EffectKindId.CREATE_FINANCIAL_TRANSACTION,
            nextPc = 20
        )

        val res = executor.execute(frame, buffer)
        assertEquals(20, res.arg)
        assertEquals(1, buffer.effectCount)

        val view = buffer.view()
        assertEquals(EffectKindId.CREATE_FINANCIAL_TRANSACTION, view.getKind(0))
        assertEquals(15000L, view.getLongArg(0, 0))
        assertEquals(1L, view.getLongArg(0, 1))
        assertEquals(0L, view.getLongArg(0, 2))
        assertEquals(1L, view.getLongArg(0, 3))
        assertEquals(dummyTx, view.getRefArg(0, 4))
    }

    @Test
    fun saveToStorageAction_writesToBufferAndJumps() {
        val executor = SaveToStorageActionExecutor(
            completeProcessing = true,
            effectCode = EffectKindId.SAVE_TO_STORAGE,
            nextPc = 30
        )

        val res = executor.execute(frame, buffer)
        assertEquals(30, res.arg)
        assertEquals(1, buffer.effectCount)

        val view = buffer.view()
        assertEquals(EffectKindId.SAVE_TO_STORAGE, view.getKind(0))
        assertEquals(1L, view.getLongArg(0, 0))
    }

    @Test
    fun dropEventAction_writesBufferAndHalts() {
        val executor = DropEventActionExecutor(
            reasonCode = 42,
            effectCode = EffectKindId.DROP_EVENT
        )

        val res = executor.execute(frame, buffer)
        assertTrue(res.isHalt)
        assertEquals(42, res.arg)
        assertEquals(1, buffer.effectCount)

        val view = buffer.view()
        assertEquals(EffectKindId.DROP_EVENT, view.getKind(0))
        assertEquals(42L, view.getLongArg(0, 0))
    }

    @Test
    fun stopProcessingAction_haltsWithoutBufferWrites() {
        val executor = StopProcessingActionExecutor(reasonCode = 99)
        val res = executor.execute(frame, buffer)

        assertTrue(res.isHalt)
        assertEquals(99, res.arg)
        assertEquals(0, buffer.effectCount)
    }
}
