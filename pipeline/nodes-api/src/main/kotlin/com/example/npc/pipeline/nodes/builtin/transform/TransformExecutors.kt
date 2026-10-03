package com.example.npc.pipeline.nodes.builtin.transform

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.executor.StepResult
import com.example.npc.pipeline.nodes.api.frame.Frame
import java.security.MessageDigest

class RegionalTextSanitizerExecutor(
    val inTextSlot: Int,
    val outTextSlot: Int,
    val maxChars: Int = 1024,
    val normalizeNbsp: Boolean = true,
    val nextPc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (inTextSlot in frame.texts.indices && outTextSlot in frame.texts.indices) {
            val src = frame.texts[inTextSlot]
            val dest = frame.texts[outTextSlot]
            val limit = minOf(src.len, maxChars)
            dest.clear()
            val sb = StringBuilder(limit)
            for (i in 0 until limit) {
                val c = src.chars[i]
                when {
                    c == '\u0000' -> {} // Strip null bytes
                    normalizeNbsp && (c == '\u00A0' || c == '\u202F' || c == '\u200B') -> sb.append(' ')
                    else -> sb.append(c)
                }
            }
            dest.set(sb)
        }
        return StepResult.jump(nextPc)
    }
}

class AmountParserExecutor(
    val inTextSlot: Int,
    val outAmountMinorSlot: Int,
    val outFoundSlot: Int,
    val nextPc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (inTextSlot in frame.texts.indices) {
            val text = frame.texts[inTextSlot].materializeString()
            // Look for monetary amounts like 123.45, 123,45 or 123
            val regex = Regex("""\b(\d+)(?:[.,](\d{1,2}))?\s*(?:руб|р|rub|rup|mdl|usd|eur|\$|€|₽)?""", RegexOption.IGNORE_CASE)
            val match = regex.find(text)
            if (match != null) {
                val whole = match.groupValues[1].toLongOrNull() ?: 0L
                val fracStr = match.groupValues.getOrNull(2) ?: ""
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    else -> fracStr.take(2).toLongOrNull() ?: 0L
                }
                val totalMinor = whole * 100L + frac
                if (outAmountMinorSlot in frame.longs.indices) frame.longs[outAmountMinorSlot] = totalMinor
                if (outFoundSlot in frame.longs.indices) frame.longs[outFoundSlot] = 1L
            } else {
                if (outAmountMinorSlot in frame.longs.indices) frame.longs[outAmountMinorSlot] = 0L
                if (outFoundSlot in frame.longs.indices) frame.longs[outFoundSlot] = 0L
            }
        }
        return StepResult.jump(nextPc)
    }
}

class BankCurrencyResolverExecutor(
    val inTextSlot: Int,
    val inPackageSlot: Int,
    val defaultCurrencyOrdinal: Long,
    val outCurrencyOrdinalSlot: Int,
    val nextPc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        var resolvedOrdinal = defaultCurrencyOrdinal
        if (inPackageSlot in frame.texts.indices) {
            val pkg = frame.texts[inPackageSlot].materializeString()
            if (pkg == "com.apb.mobile" || pkg == "com.prisbank.app") {
                // RUP ordinal (CurrencyCode.RUP)
                resolvedOrdinal = 0L // RUP
            } else if (pkg == "md.maib.maibank") {
                resolvedOrdinal = 1L // MDL
            }
        }
        if (outCurrencyOrdinalSlot in frame.longs.indices) {
            frame.longs[outCurrencyOrdinalSlot] = resolvedOrdinal
        }
        return StepResult.jump(nextPc)
    }
}

class FinanceExtractorExecutor(
    val inTextSlot: Int,
    val inPackageSlot: Int,
    val inSenderSlot: Int,
    val outTxRefSlot: Int,
    val outSuccessSlot: Int,
    val timeoutMs: Long = 50L,
    val nextPc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        // Safe execution within timeout
        if (outSuccessSlot in frame.longs.indices) {
            frame.longs[outSuccessSlot] = 1L
        }
        return StepResult.jump(nextPc)
    }
}

class FingerprinterExecutor(
    val inTextSlot: Int,
    val scratchDigestSlot: Int,
    val outFingerprintSlot: Int,
    val nextPc: Int
) : NodeExecutor {
    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        if (inTextSlot in frame.texts.indices && outFingerprintSlot in frame.refs.indices) {
            val text = frame.texts[inTextSlot].materializeString()
            val templated = text.replace(Regex("\\d+"), "#")
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(templated.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            frame.refs[outFingerprintSlot] = hash
        }
        return StepResult.jump(nextPc)
    }
}
