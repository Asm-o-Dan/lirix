package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompiledPipelineTest {

    @Test
    fun execute_progressesThroughStagesUntilPass() {
        val layout = FrameLayout(longSlots = 2, doubleSlots = 2, refSlots = 2, textSlots = 2, textCapacity = 64)
        val frame = Frame(layout)

        val stage0 = object : CompiledStage {
            override val stageId: String = "s0"
            override val stageIndex: Int = 0
            override fun execute(frame: Frame): Int = 1 // jump to stage 1
        }
        val stage1 = object : CompiledStage {
            override val stageId: String = "s1"
            override val stageIndex: Int = 1
            override fun execute(frame: Frame): Int = Signal.PASS // finish pipeline
        }

        val pipeline = CompiledPipeline(
            pipelineId = "test-pipe",
            revision = 1L,
            canonicalHash = "hash123",
            compiledAtTimestamp = System.currentTimeMillis(),
            dslVersion = 1,
            compilerVersion = 1,
            packageWhitelistSet = setOf("com.apb.mobile"),
            requiredInputMask = 0L,
            layout = layout,
            stages = arrayOf(stage0, stage1),
            patterns = emptyArray(),
            debugInfo = DebugInfo(emptyMap(), emptyMap())
        )

        val signal = pipeline.execute(frame)
        assertEquals(Signal.PASS, signal)
    }

    @Test
    fun execute_terminatesEarlyOnDrop() {
        val layout = FrameLayout(longSlots = 2, doubleSlots = 2, refSlots = 2, textSlots = 2, textCapacity = 64)
        val frame = Frame(layout)

        val stage0 = object : CompiledStage {
            override val stageId: String = "s0"
            override val stageIndex: Int = 0
            override fun execute(frame: Frame): Int = Signal.DROP
        }
        val stage1 = object : CompiledStage {
            override val stageId: String = "s1"
            override val stageIndex: Int = 1
            override fun execute(frame: Frame): Int = Signal.PASS
        }

        val pipeline = CompiledPipeline(
            pipelineId = "test-pipe",
            revision = 1L,
            canonicalHash = "hash123",
            compiledAtTimestamp = System.currentTimeMillis(),
            dslVersion = 1,
            compilerVersion = 1,
            packageWhitelistSet = setOf("com.apb.mobile"),
            requiredInputMask = 0L,
            layout = layout,
            stages = arrayOf(stage0, stage1),
            patterns = emptyArray(),
            debugInfo = DebugInfo(emptyMap(), emptyMap())
        )

        val signal = pipeline.execute(frame)
        assertEquals(Signal.DROP, signal)
    }
}
