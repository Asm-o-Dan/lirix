package com.example.npc.pipeline.nodes.builtin.condition

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.executor.StepResult
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.google.re2j.Matcher

class PackageMatchExecutor(
    val inPackageSlot: Int,
    val allowedPackages: Array<String>,
    val matchMode: Int,
    val negate: Boolean,
    val thenPc: Int,
    val elsePc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (inPackageSlot < 0 || inPackageSlot >= frame.texts.size) {
            return StepResult.jump(elsePc)
        }
        val textReg = frame.texts[inPackageSlot]
        val pkgStr = textReg.materializeString()

        var matched = false
        for (allowed in allowedPackages) {
            val isMatch = when (matchMode) {
                0 -> pkgStr == allowed // EXACT
                1 -> pkgStr.startsWith(allowed) // PREFIX
                2 -> pkgStr.contains(allowed) // CONTAINS
                else -> pkgStr == allowed
            }
            if (isMatch) {
                matched = true
                break
            }
        }

        val outcome = if (negate) !matched else matched
        return StepResult.jump(if (outcome) thenPc else elsePc)
    }
}

class TextRegexMatchExecutor(
    val inTextSlot: Int,
    val scratchMatcherSlot: Int,
    val negate: Boolean,
    val thenPc: Int,
    val elsePc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (inTextSlot < 0 || inTextSlot >= frame.texts.size ||
            scratchMatcherSlot < 0 || scratchMatcherSlot >= frame.refs.size) {
            return StepResult.jump(elsePc)
        }
        val matcher = frame.refs[scratchMatcherSlot] as? Matcher ?: return StepResult.jump(elsePc)
        val textReg = frame.texts[inTextSlot]
        matcher.reset(textReg)
        val found = matcher.find()
        val outcome = if (negate) !found else found
        return StepResult.jump(if (outcome) thenPc else elsePc)
    }
}

class SenderMatchExecutor(
    val inSenderSlot: Int,
    val senders: Array<String>,
    val caseSensitive: Boolean,
    val negate: Boolean,
    val thenPc: Int,
    val elsePc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (inSenderSlot < 0 || inSenderSlot >= frame.texts.size) {
            return StepResult.jump(elsePc)
        }
        val senderStr = frame.texts[inSenderSlot].materializeString()
        var matched = false
        for (s in senders) {
            if (senderStr.equals(s, ignoreCase = !caseSensitive)) {
                matched = true
                break
            }
        }
        val outcome = if (negate) !matched else matched
        return StepResult.jump(if (outcome) thenPc else elsePc)
    }
}

class CategoryMatchExecutor(
    val inCategoryOrdinalSlot: Int,
    val inConfidenceSlot: Int,
    val expectedCategoryOrdinal: Long,
    val minConfidence: Double,
    val thenPc: Int,
    val elsePc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (inCategoryOrdinalSlot < 0 || inCategoryOrdinalSlot >= frame.longs.size ||
            inConfidenceSlot < 0 || inConfidenceSlot >= frame.doubles.size) {
            return StepResult.jump(elsePc)
        }
        val actualCat = frame.longs[inCategoryOrdinalSlot]
        val actualConf = frame.doubles[inConfidenceSlot]
        val matched = (actualCat == expectedCategoryOrdinal) && (actualConf >= minConfidence)
        return StepResult.jump(if (matched) thenPc else elsePc)
    }
}

class PrototypeSupportCountExecutor(
    val inFingerprintSlot: Int,
    val inPackageSlot: Int,
    val minSupportCount: Long,
    val outCategorySlot: Int,
    val outConfidenceSlot: Int,
    val thenPc: Int,
    val elsePc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (inFingerprintSlot < 0 || inFingerprintSlot >= frame.refs.size) {
            return StepResult.jump(elsePc)
        }
        val fp = frame.refs[inFingerprintSlot] as? String
        if (fp.isNullOrBlank()) {
            return StepResult.jump(elsePc)
        }
        // In prototype simulation, if support count threshold is satisfied:
        // Set category to UNCLASSIFIED (ordinal 6) or target, confidence = 1.0
        if (outCategorySlot in frame.longs.indices) {
            frame.longs[outCategorySlot] = 6L
        }
        if (outConfidenceSlot in frame.doubles.indices) {
            frame.doubles[outConfidenceSlot] = 1.0
        }
        return StepResult.jump(thenPc)
    }
}
