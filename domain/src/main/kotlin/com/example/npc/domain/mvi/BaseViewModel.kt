package com.example.npc.domain.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Базовый абстрактный класс ViewModel / Presenter в MVI архитектуре модуля :domain.
 *
 * Спроектирован без привязки к Android SDK, что обеспечивает 100% покрытие JVM-тестами
 * без моков и сторонних фреймворков.
 *
 * Инварианты:
 * 1. Неизменяемость [state] ([UiState]).
 * 2. Нулевые аллокации и отсутствие повторных эмиссий при неизменном состоянии (`===` или `==`).
 * 3. Буферизованный канал одноразовых сайд-эффектов [effects] ([Channel.BUFFERED]).
 * 4. Контроль жизненного цикла через [Closeable].
 */
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
    coroutineScope: CoroutineScope? = null
) : Store<S, I, E>, Closeable {

    private val job = SupervisorJob()

    /**
     * Скоуп корутин для выполнения фоновых задач ViewModel.
     * Если не передан в конструктор, используется собственный скоуп с [SupervisorJob].
     */
    protected val viewModelScope: CoroutineScope =
        coroutineScope ?: CoroutineScope(job + Dispatchers.Default)

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()

    /**
     * Синхронный геттер текущего снимка состояния.
     */
    val currentState: S get() = _state.value

    private val _effects = Channel<E>(capacity = Channel.BUFFERED)
    override val effects: Flow<E> = _effects.receiveAsFlow()

    /**
     * Алиас [effects] для обратной совместимости.
     */
    val effect: Flow<E> get() = effects

    /**
     * Диспетчеризация внешнего намерения.
     */
    override fun dispatch(intent: I) {
        handleIntent(intent)
    }

    /**
     * Обработка намерения в наследнике.
     */
    protected abstract fun handleIntent(intent: I)

    /**
     * Обновление состояния с использованием функции редьюсера.
     * Проверка `next === current` предотвращает ненужные аллокации и холостые триггеры Flow.
     */
    protected fun updateState(reducer: (S) -> S) {
        _state.update { current ->
            val next = reducer(current)
            if (next === current) current else next
        }
    }

    /**
     * Прямая установка нового состояния с проверкой на ссылочную идентичность.
     */
    protected fun setState(newState: S) {
        _state.update { current ->
            if (newState === current) current else newState
        }
    }

    /**
     * Неблокирующая отправка сайд-эффекта в буферизованный канал.
     * При заполнении буфера отправка переходит в корутину [viewModelScope].
     */
    protected fun emitEffect(effect: E) {
        val result = _effects.trySend(effect)
        if (result.isFailure) {
            viewModelScope.launch {
                _effects.send(effect)
            }
        }
    }

    /**
     * Приостанавливаемая отправка сайд-эффекта.
     */
    protected suspend fun sendEffect(effect: E) {
        _effects.send(effect)
    }

    /**
     * Закрытие каналов и отмена активных корутин при утилизации ViewModel.
     */
    override fun close() {
        _effects.close()
        job.cancel()
    }
}
