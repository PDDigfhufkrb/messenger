package com.hemax.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hemax.models.Message
import com.hemax.repositories.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatUiState(val messages: List<Message> = emptyList(), val isLoading: Boolean = true)

class ChatViewModel(private val messageRepo: MessageRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    fun loadMessages(chatId: Long) {
        viewModelScope.launch {
            messageRepo.getMessages(chatId).collect { messages ->
                _uiState.value = ChatUiState(messages = messages, isLoading = false)
            }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        viewModelScope.launch {
            messageRepo.sendMessage(chatId, text, null)
        }
    }
}
