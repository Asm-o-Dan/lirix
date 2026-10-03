package com.example.npc.pipeline.runtime.hotswap

import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.compiler.CompiledStage
import com.example.npc.pipeline.compiler.DebugInfo
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ActivePipelineProviderTest {

    private fun createPipeline(rev: Long, id: String = "test-pipe"): CompiledPipeline {
        return CompiledPipeline(
            pipelineId = id,
            revision = rev,
            canonicalHash = "hash-$rev",
            compiledAtTimestamp = 1000L * rev,
            dslVersion = 1,
            compilerVersion = 1,
            packageWhitelistSet = setOf("com.apb.mobile"),
            requiredInputMask = 0L,
            layout = FrameLayout(2, 2, 2, 2, 64),
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
    fun returnsInitialAtStart() {
        val p1 = createPipeline(1L)
        val provider = ActivePipelineProvider.create(p1)

        assertEquals(p1, provider.current())
        assertEquals(1L, provider.activeRevisionFlow.value.revision)
    }

    @Test
    fun swap_successfulAndReturnsPrevious() {
        val p1 = createPipeline(1L)
        val p2 = createPipeline(2L)
        val provider = ActivePipelineProvider.create(p1)

        val prev = provider.swap(p2)
        assertEquals(p1, prev)
        assertEquals(p2, provider.current())
        assertEquals(2L, provider.activeRevisionFlow.value.revision)
    }

    @Test
    fun swap_throwsOnLesserOrEqualRevision() {
        val p2 = createPipeline(2L)
        val provider = ActivePipelineProvider.create(p2)

        // Equal revision
        assertThrows(IllegalArgumentException::class.java) {
            provider.swap(createPipeline(2L))
        }

        // Lesser revision
        assertThrows(IllegalArgumentException::class.java) {
            provider.swap(createPipeline(1L))
        }
    }

    @Test
    fun concurrentReadsDuringSwap_remainStable() = runBlocking {
        val p1 = createPipeline(1L)
        val provider = ActivePipelineProvider.create(p1)

        val jobs = (1..50).map { threadIdx ->
            launch(Dispatchers.Default) {
                for (i in 1..1000) {
                    val curr = provider.current()
                    org.junit.jupiter.api.Assertions.assertTrue(curr.revision >= 1L)
                }
            }
        }

        val swapJob = launch(Dispatchers.Default) {
            for (rev in 2L..10L) {
                provider.swap(createPipeline(rev))
            }
        }

        (jobs + swapJob).joinAll()
        assertEquals(10L, provider.current().revision)
    }
}
