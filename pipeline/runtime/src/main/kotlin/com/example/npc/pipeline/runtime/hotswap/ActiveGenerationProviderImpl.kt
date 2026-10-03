package com.example.npc.pipeline.runtime.hotswap

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

class ActiveGenerationProviderImpl(
    initial: RuntimeGeneration
) : ActiveGenerationProvider {

    private val atomicRef = AtomicReference(requireNotNull(initial) { "Initial generation cannot be null" })

    private val _activeGenerationFlow = MutableStateFlow(
        GenerationSnapshotInfo(
            generationId = initial.generationId,
            pipelineRevision = initial.pipeline.revision,
            bankVersion = initial.bank.bankVersion,
            activatedAtTimestamp = initial.activatedAtTimestamp
        )
    )

    override val activeGenerationFlow: StateFlow<GenerationSnapshotInfo> = _activeGenerationFlow.asStateFlow()

    override fun current(): RuntimeGeneration = atomicRef.get()

    override fun swap(next: RuntimeGeneration): RuntimeGeneration {
        while (true) {
            val current = atomicRef.get()
            require(next.generationId > current.generationId) {
                "New generationId (${next.generationId}) must be strictly greater than current generationId (${current.generationId})"
            }

            if (atomicRef.compareAndSet(current, next)) {
                _activeGenerationFlow.value = GenerationSnapshotInfo(
                    generationId = next.generationId,
                    pipelineRevision = next.pipeline.revision,
                    bankVersion = next.bank.bankVersion,
                    activatedAtTimestamp = next.activatedAtTimestamp
                )
                return current
            }
        }
    }

    override fun updateBank(newBank: CompiledTemplateBank): RuntimeGeneration {
        while (true) {
            val current = atomicRef.get()
            require(newBank.bankVersion >= current.bank.bankVersion) {
                "New bank version (${newBank.bankVersion}) cannot be less than current (${current.bank.bankVersion})"
            }
            val next = RuntimeGeneration(
                pipeline = current.pipeline,
                bank = newBank,
                generationId = current.generationId + 1,
                activatedAtTimestamp = System.currentTimeMillis()
            )
            if (atomicRef.compareAndSet(current, next)) {
                _activeGenerationFlow.value = GenerationSnapshotInfo(
                    generationId = next.generationId,
                    pipelineRevision = next.pipeline.revision,
                    bankVersion = next.bank.bankVersion,
                    activatedAtTimestamp = next.activatedAtTimestamp
                )
                return next
            }
        }
    }

    override fun updatePipeline(newPipeline: CompiledPipeline): RuntimeGeneration {
        while (true) {
            val current = atomicRef.get()
            require(newPipeline.revision >= current.pipeline.revision) {
                "New pipeline revision (${newPipeline.revision}) cannot be less than current (${current.pipeline.revision})"
            }
            val next = RuntimeGeneration(
                pipeline = newPipeline,
                bank = current.bank,
                generationId = current.generationId + 1,
                activatedAtTimestamp = System.currentTimeMillis()
            )
            if (atomicRef.compareAndSet(current, next)) {
                _activeGenerationFlow.value = GenerationSnapshotInfo(
                    generationId = next.generationId,
                    pipelineRevision = next.pipeline.revision,
                    bankVersion = next.bank.bankVersion,
                    activatedAtTimestamp = next.activatedAtTimestamp
                )
                return next
            }
        }
    }
}
