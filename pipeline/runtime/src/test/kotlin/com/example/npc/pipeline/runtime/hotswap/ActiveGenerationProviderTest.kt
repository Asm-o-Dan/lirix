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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveGenerationProviderTest {

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

    private fun createGeneration(genId: Long, bankVer: Long, pipeRev: Long): RuntimeGeneration {
        return RuntimeGeneration(
            pipeline = createPipeline(pipeRev),
            bank = CompiledTemplateBank(bankVer),
            generationId = genId
        )
    }

    @Test
    fun returnsInitialGenerationAtStart() {
        val initial = createGeneration(1L, 0L, 1L)
        val provider = ActiveGenerationProvider.create(initial)

        assertEquals(initial, provider.current())
        assertEquals(1L, provider.activeGenerationFlow.value.generationId)
        assertEquals(0L, provider.activeGenerationFlow.value.bankVersion)
        assertEquals(1L, provider.activeGenerationFlow.value.pipelineRevision)
    }

    @Test
    fun swap_successfulAndReturnsPrevious() {
        val g1 = createGeneration(1L, 0L, 1L)
        val g2 = createGeneration(2L, 1L, 1L)
        val provider = ActiveGenerationProvider.create(g1)

        val prev = provider.swap(g2)
        assertEquals(g1, prev)
        assertEquals(g2, provider.current())
        assertEquals(2L, provider.activeGenerationFlow.value.generationId)
    }

    @Test
    fun updateBank_incrementsGenerationAndKeepsPipeline() {
        val g1 = createGeneration(1L, 0L, 1L)
        val provider = ActiveGenerationProvider.create(g1)

        val next = provider.updateBank(CompiledTemplateBank(1L))
        assertEquals(2L, next.generationId)
        assertEquals(1L, next.bank.bankVersion)
        assertEquals(g1.pipeline, next.pipeline)
    }

    @Test
    fun swap_throwsOnLesserOrEqualGenerationId() {
        val g2 = createGeneration(2L, 1L, 1L)
        val provider = ActiveGenerationProvider.create(g2)

        assertThrows(IllegalArgumentException::class.java) {
            provider.swap(createGeneration(2L, 2L, 1L))
        }

        assertThrows(IllegalArgumentException::class.java) {
            provider.swap(createGeneration(1L, 2L, 1L))
        }
    }

    @Test
    fun concurrentReadsAndUpdates_remainConsistent() = runBlocking {
        val g1 = createGeneration(1L, 0L, 1L)
        val provider = ActiveGenerationProvider.create(g1)

        val readerJobs = (1..50).map {
            launch(Dispatchers.Default) {
                for (i in 1..2000) {
                    val curr = provider.current()
                    assertTrue(curr.generationId >= 1L)
                    assertTrue(curr.bank.bankVersion >= 0L)
                }
            }
        }

        val writerJob = launch(Dispatchers.Default) {
            for (v in 1L..20L) {
                provider.updateBank(CompiledTemplateBank(v))
            }
        }

        (readerJobs + writerJob).joinAll()
        assertTrue(provider.current().generationId >= 21L)
        assertEquals(20L, provider.current().bank.bankVersion)
    }
}
