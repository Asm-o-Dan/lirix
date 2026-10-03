package com.example.npc.feature.editor

import androidx.compose.ui.graphics.Color
import com.example.npc.feature.editor.model.EditorTokenUi
import com.example.npc.feature.editor.ui.TokenChipState
import com.example.npc.feature.editor.ui.toEditorTokenUi
import com.example.npc.feature.editor.ui.toTokenChipState
import com.example.npc.induction.SlotType
import com.example.npc.induction.TokenRole
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TokenChipStateTest {

    @Test
    fun `roleColor returns correct color for each slot role`() {
        TokenChipState(0, "100.00", SlotType.TX_AMOUNT).roleColor shouldBe Color(0xFF4CAF50)
        TokenChipState(1, "MDL", SlotType.CURRENCY).roleColor shouldBe Color(0xFF009688)
        TokenChipState(2, "*1234", SlotType.CARD_MASK).roleColor shouldBe Color(0xFF9C27B0)
        TokenChipState(3, "500.00", SlotType.BALANCE).roleColor shouldBe Color(0xFF2196F3)
        TokenChipState(4, "SUPERMARKET", SlotType.MERCHANT).roleColor shouldBe Color(0xFFE91E63)
        TokenChipState(5, "5.00", SlotType.FEE).roleColor shouldBe Color(0xFFFF9800)
        TokenChipState(6, "ref123", SlotType.OTHER).roleColor shouldBe Color(0xFF9E9E9E)
        TokenChipState(7, "Oplata", null).roleColor shouldBe Color(0xFFE0E0E0)
    }

    @Test
    fun `bidirectional mapping between EditorTokenUi and TokenChipState preserves properties`() {
        val tokenUi = EditorTokenUi(
            index = 3,
            text = "250.00",
            role = TokenRole.SLOT,
            assignedSlot = SlotType.TX_AMOUNT,
            isAnchorKeyword = false,
            isSelectable = true,
            isHighlighted = true
        )

        val chipState = tokenUi.toTokenChipState()
        chipState.tokenIndex shouldBe 3
        chipState.text shouldBe "250.00"
        chipState.role shouldBe SlotType.TX_AMOUNT
        chipState.isHighlighted shouldBe true

        val convertedBack = chipState.toEditorTokenUi()
        convertedBack.index shouldBe tokenUi.index
        convertedBack.text shouldBe tokenUi.text
        convertedBack.assignedSlot shouldBe tokenUi.assignedSlot
        convertedBack.isHighlighted shouldBe tokenUi.isHighlighted
    }
}
