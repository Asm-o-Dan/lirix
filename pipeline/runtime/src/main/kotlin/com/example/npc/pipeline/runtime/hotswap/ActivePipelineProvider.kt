package com.example.npc.pipeline.runtime.hotswap

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

data class PipelineSnapshotInfo(
    val pipelineId: String,
    val revision: Long,
    val canonicalHash: String,
    val stagesCount: Int,
    val swappedAtTimestamp: Long
)

interface ActivePipelineProvider {
    fun current(): CompiledPipeline
    fun swap(next: CompiledPipeline): CompiledPipeline
    val activeRevisionFlow: StateFlow<PipelineSnapshotInfo>

    companion object {
        fun create(initial: CompiledPipeline): ActivePipelineProvider = ActivePipelineProviderImpl(initial)
    }
}

class ActivePipelineProviderImpl(
    initial: CompiledPipeline
) : ActivePipelineProvider {

    private val atomicRef = AtomicReference(requireNotNull(initial) { "Initial pipeline cannot be null" })

    private val _activeRevisionFlow = MutableStateFlow(
        PipelineSnapshotInfo(
            pipelineId = initial.pipelineId,
            revision = initial.revision,
            canonicalHash = initial.canonicalHash,
            stagesCount = initial.stagesCount,
            swappedAtTimestamp = System.currentTimeMillis()
        )
    )

    override val activeRevisionFlow: StateFlow<PipelineSnapshotInfo> = _activeRevisionFlow.asStateFlow()

    override fun current(): CompiledPipeline = atomicRef.get()

    override fun swap(next: CompiledPipeline): CompiledPipeline {
        while (true) {
            val current = atomicRef.get()
            require(next.revision > current.revision) {
                "New revision (${next.revision}) must be strictly greater than current revision (${current.revision})"
            }

            if (atomicRef.compareAndSet(current, next)) {
                _activeRevisionFlow.value = PipelineSnapshotInfo(
                    pipelineId = next.pipelineId,
                    revision = next.revision,
                    canonicalHash = next.canonicalHash,
                    stagesCount = next.stagesCount,
                    swappedAtTimestamp = System.currentTimeMillis()
                )
                return current
            }
        }
    }
}
