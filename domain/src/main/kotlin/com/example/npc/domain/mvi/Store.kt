package com.example.npc.domain.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Маркерный интерфейс для UI состояния экрана.
 * Реализации должны быть иммутабельными (data class) со стабильными полями
 * и неизменяемыми коллекциями kotlinx.collections.immutable (PersistentList, PersistentMap).
 */
interface UiState

/**
 * Маркерный интерфейс для намерений (интентов) пользователя или системы.
 */
interface UiIntent

/**
 * Маркерный интерфейс для одноразовых сайд-эффектов (навигация, снекбары, тосты).
 */
interface UiEffect

/**
 * Строгий MVI/UDF контракт хранилища состояния.
 *
 * @param S Тип неизменяемого UI состояния.
 * @param I Тип входящих намерений / действий.
 * @param E Тип одноразовых эффектов.
 */
interface Store<S : UiState, in I : UiIntent, out E : UiEffect> {
    /**
     * Поток текущего состояния UI.
     */
    val state: StateFlow<S>

    /**
     * Поток одноразовых побочных эффектов на базе буферизованного Channel.
     */
    val effects: Flow<E>

    /**
     * Отправка намерения в хранилище.
     */
    fun dispatch(intent: I)
}

/**
 * Чистый детерминированный редьюсер `(S, R) -> S`.
 *
 * Позволяет тестировать всю логику переходов состояний в быстрых юнит-тестах
 * со 100% покрытием без моков, корутин и Android SDK.
 */
fun interface Reducer<S : UiState, in R> {
    fun reduce(state: S, result: R): S

    operator fun invoke(state: S, result: R): S = reduce(state, result)
}

/**
 * Легковесная базовая реализация [Store] для использования в чистом Kotlin JVM коде.
 */
open class DefaultStore<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
    private val scope: CoroutineScope,
    private val reducer: Reducer<S, I>? = null,
    private val onIntent: (suspend DefaultStore<S, I, E>.(I) -> Unit)? = null
) : Store<S, I, E> {

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(capacity = Channel.BUFFERED)
    override val effects: Flow<E> = _effects.receiveAsFlow()

    val currentState: S get() = _state.value

    override fun dispatch(intent: I) {
        if (reducer != null) {
            updateState { reducer.reduce(it, intent) }
        }
        if (onIntent != null) {
            scope.launch {
                onIntent.invoke(this@DefaultStore, intent)
            }
        }
    }

    /**
     * Обновление состояния с проверкой на ссылочную идентичность:
     * нулевые аллокации и отсутствие ложных эмиссий при неизменном состоянии.
     */
    fun updateState(transform: (S) -> S) {
        _state.update { current ->
            val next = transform(current)
            if (next === current) current else next
        }
    }

    /**
     * Прямая установка состояния.
     */
    fun setState(newState: S) {
        _state.update { current ->
            if (newState === current) current else newState
        }
    }

    /**
     * Отправка эффекта в буферизованный канал.
     */
    fun emitEffect(effect: E) {
        val result = _effects.trySend(effect)
        if (result.isFailure) {
            scope.launch {
                _effects.send(effect)
            }
        }
    }

    suspend fun sendEffect(effect: E) {
        _effects.send(effect)
    }

    fun close() {
        _effects.close()
    }
}
