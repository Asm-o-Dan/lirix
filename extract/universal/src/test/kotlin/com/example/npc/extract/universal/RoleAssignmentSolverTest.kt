package com.example.npc.extract.universal

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.TokenStream
import com.example.npc.extract.universal.model.SlotRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RoleAssignmentSolverTest {

    private val normalizer = TextNormalizer.create()
    private val lexer = Lexer.create()
    private val candidateGenerator = AmountCandidateGenerator.create()
    private val featureExtractor = FeatureExtractor.create()
    private val solver = RoleAssignmentSolver.create()

    private fun tokenize(text: String): TokenStream {
        val norm = normalizer.normalize(text)
        return lexer.tokenize(norm)
    }

    @Test
    @DisplayName("MAIB TEMU Incident: assigns TX_AMOUNT to 245.90 MDL and BALANCE to 12 345,67 MDL")
    fun testMaibIncidentPush() {
        val text = "Restituire 245,90 MDL\nTEMU.COM\nCard *1234\nSold: 12 345,67 MDL"
        val stream = tokenize(text)
        val candidates = candidateGenerator.generate(stream)
        val features = featureExtractor.extract(candidates, stream)

        val solution = solver.solve(features)

        assertThat(solution).isNotNull
        val res = solution!!

        // 1. Transaction Amount must be 245.90 MDL
        assertThat(res.txAmount.minorUnits).isEqualTo(24590L)
        assertThat(res.txAmount.currencyCode).isEqualTo(CurrencyCode.MDL)

        // 2. Balance must be 12 345.67 MDL
        assertThat(res.balance).isNotNull
        assertThat(res.balance!!.minorUnits).isEqualTo(1234567L)
        assertThat(res.balance!!.currencyCode).isEqualTo(CurrencyCode.MDL)

        // 3. Check assignments mapping
        val roles = res.assignments.associate { it.candidate.id to it.role }
        assertThat(roles[candidates[0].id]).isEqualTo(SlotRole.TX_AMOUNT)
        assertThat(roles[candidates[1].id]).isEqualTo(SlotRole.BALANCE)

        // 4. Confidence
        assertThat(res.confidence).isGreaterThanOrEqualTo(0.90f)
    }

    @Test
    @DisplayName("Single candidate push: automatically assigned TX_AMOUNT")
    fun testSingleCandidate() {
        val text = "Plata 50.00 MDL Magazin"
        val stream = tokenize(text)
        val candidates = candidateGenerator.generate(stream)
        val features = featureExtractor.extract(candidates, stream)

        val solution = solver.solve(features)

        assertThat(solution).isNotNull
        assertThat(solution!!.txAmount.minorUnits).isEqualTo(5000L)
        assertThat(solution.balance).isNull()
        assertThat(solution.fee).isNull()
        assertThat(solution.assignments).hasSize(1)
        assertThat(solution.assignments[0].role).isEqualTo(SlotRole.TX_AMOUNT)
    }

    @Test
    @DisplayName("Two candidates without balance anchor: first is TX_AMOUNT, second is OTHER")
    fun testTwoCandidatesNoBalanceAnchor() {
        val text = "Summa 100 MDL i 200 MDL"
        val stream = tokenize(text)
        val candidates = candidateGenerator.generate(stream)
        val features = featureExtractor.extract(candidates, stream)

        val solution = solver.solve(features)

        assertThat(solution).isNotNull
        assertThat(solution!!.txAmount.minorUnits).isEqualTo(10000L)
        assertThat(solution.balance).isNull()
        assertThat(solution.otherAmounts).hasSize(1)
        assertThat(solution.otherAmounts[0].minorUnits).isEqualTo(20000L)
    }

    @Test
    @DisplayName("Candidate with fee anchor is correctly assigned FEE")
    fun testFeeAnchorAssignment() {
        val text = "Perevod 1000 RUP, komissiya: 15 RUP"
        val stream = tokenize(text)
        val candidates = candidateGenerator.generate(stream, sourcePackage = "com.apb.mobile")
        val features = featureExtractor.extract(candidates, stream)

        val solution = solver.solve(features)

        assertThat(solution).isNotNull
        assertThat(solution!!.txAmount.minorUnits).isEqualTo(100000L)
        assertThat(solution.fee).isNotNull
        assertThat(solution.fee!!.minorUnits).isEqualTo(1500L)
        assertThat(solution.balance).isNull()
    }

    @Test
    @DisplayName("Performance benchmark: combinatorial search takes <= 0.05 ms per message")
    fun testPerformanceBenchmark() {
        val text = "Restituire 245,90 MDL\nTEMU.COM\nCard *1234\nSold: 12 345,67 MDL"
        val stream = tokenize(text)
        val candidates = candidateGenerator.generate(stream)
        val features = featureExtractor.extract(candidates, stream)

        // Warm up
        repeat(500) {
            solver.solve(features)
        }

        // Measure 1000 iterations
        val iterations = 1000
        val startNano = System.nanoTime()
        repeat(iterations) {
            solver.solve(features)
        }
        val elapsedNano = System.nanoTime() - startNano
        val avgMs = (elapsedNano / iterations.toDouble()) / 1_000_000.0

        // Strict SLA: <= 0.05 ms (50 microseconds)
        assertThat(avgMs).isLessThanOrEqualTo(0.05)
    }

    @Test
    @DisplayName("Empty features returns null")
    fun testEmptyFeatures() {
        val solution = solver.solve(emptyList())
        assertThat(solution).isNull()
    }
}
