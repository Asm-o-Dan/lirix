package com.example.npc.pipeline.nodes.api.builtin

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.example.npc.pipeline.nodes.builtin.transform.AmountParserExecutor
import com.example.npc.pipeline.nodes.builtin.transform.BankCurrencyResolverExecutor
import com.example.npc.pipeline.nodes.builtin.transform.FinanceExtractorExecutor
import com.example.npc.pipeline.nodes.builtin.transform.FingerprinterExecutor
import com.example.npc.pipeline.nodes.builtin.transform.RegionalTextSanitizerExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TransformExecutorsTest {

    private lateinit var frame: Frame
    private lateinit var buffer: EffectBuffer

    @BeforeEach
    fun setUp() {
        val layout = FrameLayout(
            longSlots = 8,
            doubleSlots = 4,
            refSlots = 8,
            textSlots = 8,
            textCapacity = 1024
        )
        frame = Frame(layout)
        buffer = EffectBuffer()
    }

    @Test
    fun regionalTextSanitizer_normalizesNbspAndStripsNull() {
        val inSlot = 0
        val outSlot = 1
        frame.texts[inSlot].set("Hello\u00A0World\u0000!\u202FTest\u200B")

        val executor = RegionalTextSanitizerExecutor(
            inTextSlot = inSlot,
            outTextSlot = outSlot,
            maxChars = 100,
            normalizeNbsp = true,
            nextPc = 5
        )

        val result = executor.execute(frame, buffer)
        assertEquals(5, result.arg)
        assertEquals("Hello World! Test ", frame.texts[outSlot].materializeString())
    }

    @Test
    fun regionalTextSanitizer_truncatesAtMaxChars() {
        val inSlot = 0
        val outSlot = 1
        frame.texts[inSlot].set("1234567890")

        val executor = RegionalTextSanitizerExecutor(
            inTextSlot = inSlot,
            outTextSlot = outSlot,
            maxChars = 5,
            normalizeNbsp = false,
            nextPc = 2
        )

        val result = executor.execute(frame, buffer)
        assertEquals(2, result.arg)
        assertEquals("12345", frame.texts[outSlot].materializeString())
    }

    @Test
    fun amountParser_parsesDecimalAndFraction() {
        val inSlot = 0
        val amountSlot = 0
        val foundSlot = 1

        frame.texts[inSlot].set("Perevod 123.45 руб uspeshno")
        val executor = AmountParserExecutor(inSlot, amountSlot, foundSlot, nextPc = 10)
        val res = executor.execute(frame, buffer)

        assertEquals(10, res.arg)
        assertEquals(12345L, frame.longs[amountSlot])
        assertEquals(1L, frame.longs[foundSlot])
    }

    @Test
    fun amountParser_parsesCommaFraction() {
        val inSlot = 0
        val amountSlot = 0
        val foundSlot = 1

        frame.texts[inSlot].set("Oplata 50,5 mdl")
        val executor = AmountParserExecutor(inSlot, amountSlot, foundSlot, nextPc = 10)
        executor.execute(frame, buffer)

        assertEquals(5050L, frame.longs[amountSlot])
        assertEquals(1L, frame.longs[foundSlot])
    }

    @Test
    fun amountParser_handlesNoMatch() {
        val inSlot = 0
        val amountSlot = 0
        val foundSlot = 1

        frame.texts[inSlot].set("No numbers here")
        val executor = AmountParserExecutor(inSlot, amountSlot, foundSlot, nextPc = 10)
        executor.execute(frame, buffer)

        assertEquals(0L, frame.longs[amountSlot])
        assertEquals(0L, frame.longs[foundSlot])
    }

    @Test
    fun bankCurrencyResolver_resolvesKnownPackages() {
        val textSlot = 0
        val pkgSlot = 1
        val curSlot = 0

        frame.texts[pkgSlot].set("com.apb.mobile")
        val execApb = BankCurrencyResolverExecutor(textSlot, pkgSlot, defaultCurrencyOrdinal = 2L, curSlot, nextPc = 3)
        execApb.execute(frame, buffer)
        assertEquals(0L, frame.longs[curSlot]) // RUP

        frame.texts[pkgSlot].set("md.maib.maibank")
        val execMaib = BankCurrencyResolverExecutor(textSlot, pkgSlot, defaultCurrencyOrdinal = 2L, curSlot, nextPc = 3)
        execMaib.execute(frame, buffer)
        assertEquals(1L, frame.longs[curSlot]) // MDL

        frame.texts[pkgSlot].set("unknown.bank")
        val execOther = BankCurrencyResolverExecutor(textSlot, pkgSlot, defaultCurrencyOrdinal = 2L, curSlot, nextPc = 3)
        execOther.execute(frame, buffer)
        assertEquals(2L, frame.longs[curSlot]) // Default
    }

    @Test
    fun financeExtractor_setsSuccessAndJumps() {
        val inText = 0
        val inPkg = 1
        val inSender = 2
        val txRef = 0
        val success = 0

        val executor = FinanceExtractorExecutor(inText, inPkg, inSender, txRef, success, timeoutMs = 50L, nextPc = 7)
        val res = executor.execute(frame, buffer)

        assertEquals(7, res.arg)
        assertEquals(1L, frame.longs[success])
    }

    @Test
    fun fingerprinter_computesDeterministicSha256() {
        val inText = 0
        val fpSlot = 0
        frame.texts[inText].set("Karta *1234 Spisanie 500 rub")

        val executor = FingerprinterExecutor(inText, scratchDigestSlot = 0, outFingerprintSlot = fpSlot, nextPc = 8)
        val res = executor.execute(frame, buffer)

        assertEquals(8, res.arg)
        val fp1 = frame.refs[fpSlot] as String
        assertTrue(fp1.isNotEmpty())
        assertEquals(64, fp1.length) // SHA-256 hex string

        // Same template with different digits should produce identical fingerprint
        frame.texts[inText].set("Karta *9876 Spisanie 999 rub")
        executor.execute(frame, buffer)
        val fp2 = frame.refs[fpSlot] as String
        assertEquals(fp1, fp2)
    }
}
