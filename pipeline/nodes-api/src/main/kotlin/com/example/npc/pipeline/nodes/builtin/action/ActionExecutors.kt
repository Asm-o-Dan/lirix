package com.example.npc.pipeline.nodes.builtin.action

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.executor.StepResult
import com.example.npc.pipeline.nodes.api.frame.Frame

class SetCategoryActionExecutor(
    val categoryOrdinal: Long,
    val confidenceBits: Long,
    val engineOrdinal: Long,
    val effectCode: Int,
    val nextPc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (buffer.begin(effectCode)) {
            buffer.putLong(categoryOrdinal)
            buffer.putLong(confidenceBits)
            buffer.putLong(engineOrdinal)
            buffer.end()
        } else {
            return StepResult.fail(-1)
        }
        return StepResult.jump(nextPc)
    }
}

class CreateTransactionActionExecutor(
    val inAmountMinorSlot: Int,
    val inCurrencyOrdinalSlot: Int,
    val inTxRefSlot: Int,
    val directionOrdinal: Long,
    val statusOrdinal: Long,
    val effectCode: Int,
    val nextPc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        val amount = if (inAmountMinorSlot in frame.longs.indices) frame.longs[inAmountMinorSlot] else 0L
        val currency = if (inCurrencyOrdinalSlot in frame.longs.indices) frame.longs[inCurrencyOrdinalSlot] else 0L
        val txRef = if (inTxRefSlot in frame.refs.indices) frame.refs[inTxRefSlot] else null

        if (buffer.begin(effectCode)) {
            buffer.putLong(amount)
            buffer.putLong(currency)
            buffer.putLong(directionOrdinal)
            buffer.putLong(statusOrdinal)
            buffer.putRef(txRef)
            buffer.end()
        } else {
            return StepResult.fail(-1)
        }
        return StepResult.jump(nextPc)
    }
}

class SaveToStorageActionExecutor(
    val completeProcessing: Boolean,
    val effectCode: Int,
    val nextPc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (buffer.begin(effectCode)) {
            buffer.putLong(if (completeProcessing) 1L else 0L)
            buffer.end()
        } else {
            return StepResult.fail(-1)
        }
        return StepResult.jump(nextPc)
    }
}

class DropEventActionExecutor(
    val reasonCode: Int,
    val effectCode: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (buffer.begin(effectCode)) {
            buffer.putLong(reasonCode.toLong())
            buffer.end()
        }
        return StepResult.halt(reasonCode)
    }
}

class StopProcessingActionExecutor(
    val reasonCode: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        return StepResult.halt(reasonCode)
    }
}
