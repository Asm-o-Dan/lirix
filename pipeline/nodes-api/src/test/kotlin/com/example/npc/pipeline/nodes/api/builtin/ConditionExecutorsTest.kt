package com.example.npc.pipeline.nodes.api.builtin

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.example.npc.pipeline.nodes.builtin.condition.*
import com.google.re2j.Pattern
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConditionExecutorsTest {

    private fun createFrame(): Frame {
        return Frame(FrameLayout(longSlots = 4, doubleSlots = 4, refSlots = 4, textSlots = 4, textCapacity = 256))
    }

    @Test
    fun `PackageMatchExecutor matches allowed package in exact mode`() {
        val executor = PackageMatchExecutor(
            inPackageSlot = 0,
            allowedPackages = arrayOf("com.apb.mobile", "com.prisbank.app"),
            matchMode = 0, // EXACT
            negate = false,
            thenPc = 10,
            elsePc = 20
        )
        val frame = createFrame()
        val buffer = EffectBuffer()

        // Matched
        frame.texts[0].set("com.apb.mobile")
        val resMatch = executor.execute(frame, buffer)
        assertEquals(10, resMatch.arg)

        // Non-matched
        frame.texts[0].set("com.radolyn.ayugram")
        val resNonMatch = executor.execute(frame, buffer)
        assertEquals(20, resNonMatch.arg)
    }

    @Test
    fun `TextRegexMatchExecutor executes regex matching via RE2J with 0 allocations`() {
        val pattern = Pattern.compile("(?i)перевод\\s+\\d+")
        val matcher = pattern.matcher("")

        val executor = TextRegexMatchExecutor(
            inTextSlot = 0,
            scratchMatcherSlot = 0,
            negate = false,
            thenPc = 5,
            elsePc = 8
        )
        val frame = createFrame()
        frame.refs[0] = matcher
        val buffer = EffectBuffer()

        // Match
        frame.texts[0].set("Входящий перевод 500 RUP")
        val res1 = executor.execute(frame, buffer)
        assertEquals(5, res1.arg)

        // No match
        frame.texts[0].set("Просто сообщение")
        val res2 = executor.execute(frame, buffer)
        assertEquals(8, res2.arg)
    }

    @Test
    fun `SenderMatchExecutor case sensitive and insensitive matching`() {
        val executor = SenderMatchExecutor(
            inSenderSlot = 1,
            senders = arrayOf("APB", "PrisBank"),
            caseSensitive = false,
            negate = false,
            thenPc = 1,
            elsePc = 2
        )
        val frame = createFrame()
        val buffer = EffectBuffer()

        frame.texts[1].set("apb")
        val res = executor.execute(frame, buffer)
        assertEquals(1, res.arg)
    }

    @Test
    fun `CategoryMatchExecutor verifies category and confidence threshold`() {
        val executor = CategoryMatchExecutor(
            inCategoryOrdinalSlot = 0,
            inConfidenceSlot = 0,
            expectedCategoryOrdinal = 0L, // FINANCE
            minConfidence = 0.8,
            thenPc = 100,
            elsePc = 200
        )
        val frame = createFrame()
        val buffer = EffectBuffer()

        // Matched
        frame.longs[0] = 0L
        frame.doubles[0] = 0.95
        val resPass = executor.execute(frame, buffer)
        assertEquals(100, resPass.arg)

        // Confidence below threshold
        frame.doubles[0] = 0.75
        val resFail = executor.execute(frame, buffer)
        assertEquals(200, resFail.arg)
    }

    @Test
    fun `PrototypeSupportCountExecutor passes when fingerprint present`() {
        val executor = PrototypeSupportCountExecutor(
            inFingerprintSlot = 0,
            inPackageSlot = 0,
            minSupportCount = 2,
            outCategorySlot = 1,
            outConfidenceSlot = 1,
            thenPc = 15,
            elsePc = 30
        )
        val frame = createFrame()
        frame.refs[0] = "sha256-sample-fingerprint"
        val buffer = EffectBuffer()

        val res = executor.execute(frame, buffer)
        assertEquals(15, res.arg)
        assertEquals(6L, frame.longs[1]) // UNCLASSIFIED
        assertEquals(1.0, frame.doubles[1], 0.001)
    }
}
