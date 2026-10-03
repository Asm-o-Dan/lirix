package com.example.npc.extract.universal

import com.example.npc.core.text.Lexer
import com.example.npc.core.text.TextNormalizer
import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.model.KeywordKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FeatureExtractorTest {

    private val normalizer = TextNormalizer.create()
    private val lexer = Lexer.create()
    private val candidateGenerator = AmountCandidateGenerator.create()
    private val featureExtractor = FeatureExtractor.create()

    private fun tokenize(text: String): TokenStream {
        val norm = normalizer.normalize(text)
        return lexer.tokenize(norm)
    }

    @Test
    @DisplayName("MAIB Incident: computes balance anchor for 'Sold: 12 345,67 MDL' and card anchor")
    fun testMaibIncidentFeatures() {
        val text = "Restituire 245,90 MDL\nTEMU.COM\nCard *1234\nSold: 12 345,67 MDL"
        val stream = tokenize(text)
        val candidates = candidateGenerator.generate(stream)

        val features = featureExtractor.extract(candidates, stream)

        assertThat(features).hasSize(2)

        // 1. Transaction Amount (245,90 MDL)
        val f1 = features[0]
        assertThat(f1.hasBalanceAnchorLeft).isFalse()
        assertThat(f1.nearestKeywordKind).isEqualTo(KeywordKind.REFUND)
        assertThat(f1.distanceToNearestKeyword).isEqualTo(1)
        assertThat(f1.isFirstInLine).isFalse() // "Restituire" is before it
        assertThat(f1.isLastInLine).isTrue()
        assertThat(f1.lineIndex).isEqualTo(0)

        // 2. Balance (12 345,67 MDL)
        val f2 = features[1]
        assertThat(f2.hasBalanceAnchorLeft).isTrue() // "Sold:" precedes it
        assertThat(f2.hasCardAnchor).isTrue() // Card *1234 is within window
        assertThat(f2.lineIndex).isEqualTo(3)
        assertThat(f2.isLastInLine).isTrue()

        // Also test FeatureVector
        val vector = featureExtractor.computeVector(candidates, stream)
        assertThat(vector.candidateCount).isEqualTo(2)
        assertThat(vector.hasBalanceAnchorLeft[0]).isFalse()
        assertThat(vector.hasBalanceAnchorLeft[1]).isTrue()
        assertThat(vector.hasCardMask).isTrue()
        assertThat(vector.hasPromoKeyword).isFalse()
        assertThat(vector.hasOtpKeyword).isFalse()
    }

    @Test
    @DisplayName("Explicit sign detection: '- 245,90 MDL'")
    fun testExplicitSignDetection() {
        val text = "- 245,90 MDL"
        val stream = tokenize(text)
        val candidates = candidateGenerator.generate(stream)

        val features = featureExtractor.extract(candidates, stream)

        assertThat(features).hasSize(1)
        assertThat(features[0].hasExplicitSign).isTrue()
        assertThat(features[0].isExplicitMinus).isTrue()
        assertThat(features[0].isExplicitPlus).isFalse()
    }
}
