package com.example.npc.feature.editor

import app.cash.turbine.test
import com.example.npc.feature.editor.model.EditorUiEffect
import com.example.npc.feature.editor.model.EditorUiIntent
import com.example.npc.induction.SlotType
import com.example.npc.induction.TokenRole
import com.example.npc.induction.model.BuiltTemplate
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `initialization correctly tokenizes raw text into tokens`() = testScope.runTest {
        val sampleText = "Oplata 150.00 MDL v Linella"
        val viewModel = EditorViewModel(
            eventId = 101L,
            packageName = "md.maib.app",
            rawText = sampleText,
            coroutineScope = this,
            defaultDispatcher = testDispatcher
        )

        try {
            val tokens = viewModel.currentState.tokens
            tokens.isNotEmpty() shouldBe true
            tokens.any { it.text == "150.00" } shouldBe true
            viewModel.currentState.canUndo shouldBe false
            viewModel.currentState.isSaving shouldBe false
            viewModel.currentState.detectedOpType shouldBe "AUTO"
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `assigning slot role updates token and enforces single TX_AMOUNT`() = testScope.runTest {
        val sampleText = "Oplata 150.00 MDL i 5.00 MDL fee"
        val viewModel = EditorViewModel(
            eventId = 101L,
            packageName = "md.maib.app",
            rawText = sampleText,
            coroutineScope = this,
            defaultDispatcher = testDispatcher
        )

        try {
            val amountIndex1 = viewModel.currentState.tokens.indexOfFirst { it.text == "150.00" }
            val amountIndex2 = viewModel.currentState.tokens.indexOfFirst { it.text == "5.00" }

            // Назначаем первый токен как TX_AMOUNT
            viewModel.dispatch(
                EditorUiIntent.AssignTokenRole(
                    tokenIndex = amountIndex1,
                    role = TokenRole.SLOT,
                    slot = SlotType.TX_AMOUNT
                )
            )

            viewModel.currentState.tokens[amountIndex1].assignedSlot shouldBe SlotType.TX_AMOUNT
            viewModel.currentState.canUndo shouldBe true

            // Назначаем второй токен как TX_AMOUNT -> первый должен сброситься в LITERAL / null
            viewModel.dispatch(
                EditorUiIntent.AssignTokenRole(
                    tokenIndex = amountIndex2,
                    role = TokenRole.SLOT,
                    slot = SlotType.TX_AMOUNT
                )
            )

            viewModel.currentState.tokens[amountIndex2].assignedSlot shouldBe SlotType.TX_AMOUNT
            viewModel.currentState.tokens[amountIndex1].assignedSlot shouldBe null
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `undo restores previous state`() = testScope.runTest {
        val sampleText = "Oplata 150.00 MDL"
        val viewModel = EditorViewModel(
            eventId = 101L,
            packageName = "md.maib.app",
            rawText = sampleText,
            coroutineScope = this,
            defaultDispatcher = testDispatcher
        )

        try {
            val tokenIdx = viewModel.currentState.tokens.indexOfFirst { it.text == "150.00" }
            viewModel.currentState.tokens[tokenIdx].assignedSlot shouldBe SlotType.TX_AMOUNT
            viewModel.currentState.canUndo shouldBe false

            viewModel.dispatch(
                EditorUiIntent.AssignTokenRole(
                    tokenIndex = tokenIdx,
                    role = TokenRole.SLOT,
                    slot = SlotType.FEE
                )
            )

            viewModel.currentState.tokens[tokenIdx].assignedSlot shouldBe SlotType.FEE
            viewModel.currentState.canUndo shouldBe true

            // Выполняем отмену
            viewModel.dispatch(EditorUiIntent.Undo)

            viewModel.currentState.tokens[tokenIdx].assignedSlot shouldBe SlotType.TX_AMOUNT
            viewModel.currentState.canUndo shouldBe false
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `save and activate emits SavedSuccessfully and CloseSheet when canSave is true`() = testScope.runTest {
        var savedTemplate: BuiltTemplate? = null
        val sampleText = "Pokupka 245.50 MDL Magazin"

        val viewModel = EditorViewModel(
            eventId = 42L,
            packageName = "com.bank.app",
            rawText = sampleText,
            onSaveTemplate = { tmpl, _ ->
                savedTemplate = tmpl
            },
            coroutineScope = this,
            defaultDispatcher = testDispatcher
        )

        try {
            val amountIdx = viewModel.currentState.tokens.indexOfFirst { it.text == "245.50" }
            viewModel.dispatch(
                EditorUiIntent.AssignTokenRole(
                    tokenIndex = amountIdx,
                    role = TokenRole.SLOT,
                    slot = SlotType.TX_AMOUNT
                )
            )

            // Ждем debounce 150ms
            testScheduler.advanceTimeBy(200)
            testScheduler.advanceUntilIdle()

            viewModel.currentState.validationError shouldBe null
            viewModel.currentState.canSave shouldBe true

            viewModel.effects.test {
                viewModel.dispatch(EditorUiIntent.SaveAndActivate)
                testScheduler.advanceUntilIdle()

                val effect1 = awaitItem()
                (effect1 is EditorUiEffect.SavedSuccessfully) shouldBe true

                val effect2 = awaitItem()
                (effect2 is EditorUiEffect.ShowToast) shouldBe true

                val effect3 = awaitItem()
                (effect3 is EditorUiEffect.CloseSheet) shouldBe true
            }

            savedTemplate.shouldNotBeNull()
            savedTemplate!!.constants.containsKey("transactionType") shouldBe false
            savedTemplate!!.decomposedSpec!!.constants.containsKey("transactionType") shouldBe false
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `auto induction correctly assigns TX_AMOUNT, CURRENCY, CARD_MASK, FEE, BALANCE and BALANCE_CURRENCY`() = testScope.runTest {
        val samplePush = "Возврат суммы 17 MDL от TEMU.COM успешно обработан на карту ***6159. Примененная комиссия: 0.01 USD. Доступный остаток: 16.25 USD."
        val tokens = EditorViewModel.tokenizeRawText(samplePush)

        val txAmountToken = tokens.find { it.text == "17" }
        txAmountToken.shouldNotBeNull()
        txAmountToken.assignedSlot shouldBe SlotType.TX_AMOUNT

        val currencyToken = tokens.find { it.text == "MDL" }
        currencyToken.shouldNotBeNull()
        currencyToken.assignedSlot shouldBe SlotType.CURRENCY

        val cardToken = tokens.find { it.text.contains("6159") }
        cardToken.shouldNotBeNull()
        cardToken.assignedSlot shouldBe SlotType.CARD_MASK

        val feeToken = tokens.find { it.text == "0.01" }
        feeToken.shouldNotBeNull()
        feeToken.assignedSlot shouldBe SlotType.FEE

        val balanceToken = tokens.find { it.text == "16.25" }
        balanceToken.shouldNotBeNull()
        balanceToken.assignedSlot shouldBe SlotType.BALANCE

        val balanceCurrencyToken = tokens.find { it.assignedSlot == SlotType.BALANCE_CURRENCY }
        balanceCurrencyToken.shouldNotBeNull()
        balanceCurrencyToken.text shouldBe "USD"

        val viewModel = EditorViewModel(
            eventId = 43L,
            packageName = "md.maib.app",
            rawText = samplePush,
            coroutineScope = this,
            defaultDispatcher = testDispatcher
        )

        try {
            testScheduler.advanceTimeBy(200)
            testScheduler.advanceUntilIdle()

            viewModel.currentState.validationError shouldBe null
            viewModel.currentState.canSave shouldBe true
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `changing direction blocks save until the new direction is validated`() = testScope.runTest {
        var savedTemplate: BuiltTemplate? = null
        val viewModel = EditorViewModel(
            eventId = 501L, packageName = "test.bank", rawText = "Операция 100 MDL Магазин",
            onSaveTemplate = { template, _ -> savedTemplate = template },
            coroutineScope = this, defaultDispatcher = testDispatcher
        )
        try {
            testScheduler.advanceTimeBy(200)
            testScheduler.advanceUntilIdle()
            viewModel.currentState.canSave shouldBe true
            viewModel.dispatch(EditorUiIntent.ChangeOpType("CREDIT"))
            viewModel.currentState.canSave shouldBe false
            viewModel.dispatch(EditorUiIntent.SaveAndActivate)
            savedTemplate shouldBe null
            testScheduler.advanceTimeBy(200)
            testScheduler.advanceUntilIdle()
            viewModel.currentState.canSave shouldBe true
            viewModel.dispatch(EditorUiIntent.SaveAndActivate)
            testScheduler.advanceUntilIdle()
            savedTemplate!!.constants["transactionType"] shouldBe "CREDIT"
        } finally {
            viewModel.close()
        }
    }
}
