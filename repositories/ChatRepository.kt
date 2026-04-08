package com.hemax.repositories

import com.hemax.models.Chat
import com.hemax.models.ChatFolder
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getChats(): Flow<List<Chat>>
    fun getChatById(chatId: Long): Flow<Chat?>
    suspend fun pinChat(chatId: Long, isPinned: Boolean): Result<Unit>
    suspend fun deleteChat(chatId: Long): Result<Unit>
    suspend fun markChatAsRead(chatId: Long): Result<Unit>
    fun getChatFolders(): Flow<List<ChatFolder>>
}

data class ChatFolder(
    val id: Int,
    val title: String,
    val includedChatIds: List<Long>,
    val excludedChatIds: List<Long>,
    val order: Int
)
