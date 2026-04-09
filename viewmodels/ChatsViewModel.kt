package com.hemax.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hemax.models.Chat
import com.hemax.repositories.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ChatsUiState(val isLoading: Boolean = true, val chats: List<Chat> = emptyList(), val error: String? = null)

class ChatsViewModel(private val chatRepo: ChatRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState

    init {
        loadChats()
    }

    fun loadChats() {
        viewModelScope.launch {
            chatRepo.getChats()
                .catch { e -> _uiState.value = ChatsUiState(isLoading = false, error = e.message) }
                .collect { chats -> _uiState.value = ChatsUiState(isLoading = false, chats = chats) }
        }
    }

    fun selectChat(chatId: Long) {
        // Будет обработано в навигации
    }
}
