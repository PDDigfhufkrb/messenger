package com.hemax.repositories

import com.hemax.models.Message
import com.hemax.models.Poll
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessages(chatId: Long): Flow<List<Message>>
    suspend fun sendMessage(chatId: Long, text: String, replyToId: Long? = null): Result<Message>
    suspend fun sendMedia(chatId: Long, media: MediaData, caption: String? = null): Result<Message>
    suspend fun editMessage(chatId: Long, messageId: Long, newText: String): Result<Unit>
    suspend fun deleteMessage(chatId: Long, messageId: Long): Result<Unit>
    suspend fun addReaction(chatId: Long, messageId: Long, emoji: String): Result<Unit>
    suspend fun forwardMessage(chatId: Long, fromChatId: Long, messageId: Long): Result<Unit>
    suspend fun downloadFile(fileId: Int): Result<String>
    suspend fun searchMessages(chatId: Long, query: String): Result<List<Message>>
    suspend fun sendPoll(
        chatId: Long,
        question: String,
        options: List<String>,
        isAnonymous: Boolean,
        allowsMultipleAnswers: Boolean,
        isQuiz: Boolean,
        correctOptionId: Int?,
        explanation: String?
    ): Result<Poll>
    suspend fun voteInPoll(chatId: Long, messageId: Long, optionIds: List<Int>): Result<Unit>
}

data class MediaData(
    val uri: String,
    val type: MediaType,
    val mimeType: String
)

enum class MediaType { IMAGE, VIDEO, AUDIO, DOCUMENT }
