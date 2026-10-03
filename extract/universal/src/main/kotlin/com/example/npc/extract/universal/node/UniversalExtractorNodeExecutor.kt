package com.example.npc.extract.universal.node

import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.extract.universal.ExtractionVerdict
import com.example.npc.extract.universal.UniversalExtractor
import com.example.npc.extract.universal.profile.InMemorySourceProfileRegistry
import com.example.npc.extract.universal.profile.SourceProfileRegistry
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.executor.StepResult
import com.example.npc.pipeline.nodes.api.frame.Frame

/**
 * Исполнитель узла extract.universal для конвейера рантайма (SPI NodeExecutor).
 * Выполняется как фоллбэк при отсутствии транзакции в регистре outTxRefSlot.
 */
class UniversalExtractorNodeExecutor(
    val inTextSlot: Int,
    val inPackageNameSlot: Int,
    val outTxRefSlot: Int,
    val extractor: UniversalExtractor = UniversalExtractor.create(),
    val profileRegistry: SourceProfileRegistry = InMemorySourceProfileRegistry(),
    val nextPc: Int
) : NodeExecutor {

    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        // Если транзакция уже извлечена предыдущими узлами конвейера, пропускаем
        if (outTxRefSlot in frame.refs.indices && frame.refs[outTxRefSlot] != null) {
            return StepResult.jump(nextPc)
        }

        val textReg = if (inTextSlot in frame.texts.indices) frame.texts[inTextSlot] else return StepResult.jump(nextPc)
        if (textReg.length == 0) return StepResult.jump(nextPc)

        val packageName = if (inPackageNameSlot in frame.refs.indices) frame.refs[inPackageNameSlot] as? String ?: "" else ""

        val rawText = textReg.materializeString()
        val normalized = TextNormalizer.normalize(rawText)
        val tokens = Lexer.tokenize(normalized)

        val profile = profileRegistry.getProfile(packageName)
        val result = extractor.extract(normalized, tokens, profile)

        if (result.verdict == ExtractionVerdict.ACCEPT || result.verdict == ExtractionVerdict.SUGGEST) {
            if (result.transaction != null && outTxRefSlot in frame.refs.indices) {
                frame.refs[outTxRefSlot] = result.transaction
            }
        }

        return StepResult.jump(nextPc)
    }
}
