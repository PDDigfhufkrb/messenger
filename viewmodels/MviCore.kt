package com.hemax.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

abstract class MviViewModel<Intent, State, Effect>(
    initialState: State
) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effect = Channel<Effect>(Channel.BUFFERED)
    val effect: Flow<Effect> = _effect.receiveAsFlow()

    private val intentChannel = Channel<Intent>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            intentChannel.consumeAsFlow().collect { handleIntent(it) }
        }
    }

    protected abstract suspend fun handleIntent(intent: Intent)

    fun processIntent(intent: Intent) {
        viewModelScope.launch { intentChannel.send(intent) }
    }

    protected fun setState(reducer: State.() -> State) {
        _state.update { it.reducer() }
    }

    protected fun emitEffect(effect: Effect) {
        viewModelScope.launch { _effect.send(effect) }
    }
}
