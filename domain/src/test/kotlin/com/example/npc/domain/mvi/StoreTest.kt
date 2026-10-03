package com.example.npc.domain.mvi

import app.cash.turbine.test
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {

    // --- Тестовые MVI сущности ---

    data class TestState(
        val count: Int = 0,
        val items: PersistentList<String> = persistentListOf(),
        val attributes: PersistentMap<String, String> = persistentMapOf()
    ) : UiState

    sealed interface TestIntent : UiIntent {
        data class Increment(val delta: Int = 1) : TestIntent
        data class AddItem(val item: String) : TestIntent
        data class PutAttribute(val key: String, val value: String) : TestIntent
        data object NoOp : TestIntent
        data class TriggerSideEffect(val message: String) : TestIntent
        data class DelayedWork(val message: String) : TestIntent
    }

    sealed interface TestEffect : UiEffect {
        data class Toast(val message: String) : TestEffect
        data class Navigate(val route: String) : TestEffect
    }

    // --- 1. Тесты чистого Reducer без моков и Android SDK ---

    @Test
    fun `reducer updates state deterministically with pure function`() {
        val reducer = Reducer<TestState, TestIntent> { state, intent ->
            when (intent) {
                is TestIntent.Increment -> state.copy(count = state.count + intent.delta)
                is TestIntent.AddItem -> state.copy(items = state.items.add(intent.item))
                is TestIntent.PutAttribute -> state.copy(attributes = state.attributes.put(intent.key, intent.value))
                is TestIntent.NoOp,
                is TestIntent.TriggerSideEffect,
                is TestIntent.DelayedWork -> state
            }
        }

        val initial = TestState()

        // 1. Инкремент
        val state1 = reducer(initial, TestIntent.Increment(5))
        assertEquals(5, state1.count)
        assertEquals(0, state1.items.size)

        // 2. Добавление в PersistentList
        val state2 = reducer(state1, TestIntent.AddItem("Alpha"))
        assertEquals(listOf("Alpha"), state2.items)

        // 3. Добавление в PersistentMap
        val state3 = reducer(state2, TestIntent.PutAttribute("env", "test"))
        assertEquals("test", state3.attributes["env"])

        // 4. Проверка инварианта: нулевые аллокации при NoOp
        val state4 = reducer(state3, TestIntent.NoOp)
        assertSame(state3, state4, "Reducer must return exact same instance on no-op")
    }

    @Test
    fun `kotlinx collections immutable maintain structural sharing`() {
        val initialList = persistentListOf("A", "B")
        val state = TestState(items = initialList)

        val updatedList = state.items.add("C")
        val updatedState = state.copy(items = updatedList)

        assertEquals(listOf("A", "B"), state.items)
        assertEquals(listOf("A", "B", "C"), updatedState.items)
        // Исходный список не изменился
        assertEquals(2, initialList.size)
    }

    // --- 2. Тесты DefaultStore с Turbine ---

    @Test
    fun `DefaultStore emits initial state and handles intents`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val reducer = Reducer<TestState, TestIntent> { state, intent ->
            when (intent) {
                is TestIntent.Increment -> state.copy(count = state.count + intent.delta)
                is TestIntent.AddItem -> state.copy(items = state.items.add(intent.item))
                else -> state
            }
        }

        val store = DefaultStore<TestState, TestIntent, TestEffect>(
            initialState = TestState(),
            scope = testScope,
            reducer = reducer,
            onIntent = { intent ->
                if (intent is TestIntent.TriggerSideEffect) {
                    emitEffect(TestEffect.Toast(intent.message))
                }
            }
        )

        store.state.test {
            // Начальное состояние
            assertEquals(0, awaitItem().count)

            // Диспетчеризация первого интента
            store.dispatch(TestIntent.Increment(10))
            assertEquals(10, awaitItem().count)

            // Диспетчеризация второго интента с PersistentList
            store.dispatch(TestIntent.AddItem("Item1"))
            val itemState = awaitItem()
            assertEquals(10, itemState.count)
            assertEquals(listOf("Item1"), itemState.items)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `DefaultStore avoids redundant allocations and duplicate emissions on no-op`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val reducer = Reducer<TestState, TestIntent> { state, intent ->
            when (intent) {
                is TestIntent.NoOp -> state // возврат идентичного инстанса
                is TestIntent.Increment -> state.copy(count = state.count + intent.delta)
                else -> state
            }
        }

        val store = DefaultStore<TestState, TestIntent, TestEffect>(
            initialState = TestState(count = 42),
            scope = testScope,
            reducer = reducer
        )

        store.state.test {
            assertEquals(42, awaitItem().count)

            // Отправка NoOp не должна вызывать новую эмиссию
            store.dispatch(TestIntent.NoOp)
            expectNoEvents()

            // Прямое обновление с тем же инстансом
            store.updateState { it }
            expectNoEvents()

            // Проверка, что реальное изменение эмитится
            store.dispatch(TestIntent.Increment(1))
            assertEquals(43, awaitItem().count)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `DefaultStore buffers and delivers side effects via Channel`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val store = DefaultStore<TestState, TestIntent, TestEffect>(
            initialState = TestState(),
            scope = testScope,
            onIntent = { intent ->
                if (intent is TestIntent.TriggerSideEffect) {
                    emitEffect(TestEffect.Toast(intent.message))
                    emitEffect(TestEffect.Navigate("route/${intent.message}"))
                }
            }
        )

        store.effects.test {
            store.dispatch(TestIntent.TriggerSideEffect("first"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(TestEffect.Toast("first"), awaitItem())
            assertEquals(TestEffect.Navigate("route/first"), awaitItem())

            store.dispatch(TestIntent.TriggerSideEffect("second"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(TestEffect.Toast("second"), awaitItem())
            assertEquals(TestEffect.Navigate("route/second"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- 3. Тесты BaseViewModel реализации ---

    class SampleViewModel(
        initialState: TestState = TestState(),
        scope: TestScope
    ) : BaseViewModel<TestState, TestIntent, TestEffect>(
        initialState = initialState,
        coroutineScope = scope
    ) {
        override fun handleIntent(intent: TestIntent) {
            when (intent) {
                is TestIntent.Increment -> {
                    updateState { it.copy(count = it.count + intent.delta) }
                }
                is TestIntent.AddItem -> {
                    updateState { it.copy(items = it.items.add(intent.item)) }
                }
                is TestIntent.PutAttribute -> {
                    updateState { it.copy(attributes = it.attributes.put(intent.key, intent.value)) }
                }
                is TestIntent.NoOp -> {
                    updateState { it } // no-op update
                }
                is TestIntent.TriggerSideEffect -> {
                    emitEffect(TestEffect.Toast(intent.message))
                }
                is TestIntent.DelayedWork -> {
                    viewModelScope.launch {
                        delay(100)
                        updateState { it.copy(count = it.count + 100) }
                        emitEffect(TestEffect.Toast("Completed: ${intent.message}"))
                    }
                }
            }
        }
    }

    @Test
    fun `BaseViewModel correctly transitions state and delivers effects`() = runTest {
        val vm = SampleViewModel(scope = this)

        vm.state.test {
            assertEquals(0, awaitItem().count)

            vm.dispatch(TestIntent.Increment(3))
            assertEquals(3, awaitItem().count)

            vm.dispatch(TestIntent.AddItem("PersistItem"))
            val updated = awaitItem()
            assertEquals(3, updated.count)
            assertEquals(listOf("PersistItem"), updated.items)

            // Проверка no-op
            vm.dispatch(TestIntent.NoOp)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `BaseViewModel handles asynchronous workflows with delay and effects`() = runTest {
        val vm = SampleViewModel(scope = this)

        vm.effects.test {
            vm.dispatch(TestIntent.DelayedWork("Task1"))

            // До задержки событий нет
            assertEquals(0, vm.currentState.count)

            advanceUntilIdle()

            assertEquals(100, vm.currentState.count)
            assertEquals(TestEffect.Toast("Completed: Task1"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }

        vm.close()
    }

    @Test
    fun `BaseViewModel currentState returns current synchronous snapshot`() = runTest {
        val vm = SampleViewModel(scope = this)
        assertEquals(0, vm.currentState.count)

        vm.dispatch(TestIntent.Increment(7))
        assertEquals(7, vm.currentState.count)

        vm.dispatch(TestIntent.PutAttribute("theme", "dark"))
        assertEquals("dark", vm.currentState.attributes["theme"])

        vm.close()
    }
}
