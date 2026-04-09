package com.hemax.repositories

import com.hemax.models.Message
import com.hemax.models.Poll
import com.hemax.tdlib.TdLibClient
import com.hemax.tdlib.TdLibMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

class MessageRepositoryImpl(private val tdLibClient: TdLibClient) : MessageRepository {

    override fun getMessages(chatId: Long): Flow<List<Message>> = flow {
        val tdMessages = tdLibClient.sendAsync<TdApi.Messages>(TdApi.GetChatHistory(chatId, 0, 0, 50, false))
        val messages = tdMessages.messages.mapNotNull { msg ->
            if (msg is TdApi.Message) TdLibMapper.toDomainMessage(msg) else null
        }
        emit(messages)
    }.flowOn(Dispatchers.IO)

    override suspend fun sendMessage(chatId: Long, text: String, replyToId: Long?): Result<Message> = withContext(Dispatchers.IO) {
        try {
            val content = TdLibMapper.toTdInputMessageText(text)
            val tdMessage = tdLibClient.sendAsync<TdApi.Message>(TdApi.SendMessage(chatId, replyToId ?: 0, null, null, null, content))
            Result.success(TdLibMapper.toDomainMessage(tdMessage))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun sendMedia(chatId: Long, media: MediaData, caption: String?): Result<Message> = withContext(Dispatchers.IO) {
        try {
            val inputFile = TdApi.InputFileLocal(media.uri)
            val content = when (media.type) {
                MediaType.IMAGE -> TdApi.InputMessagePhoto(inputFile, null, emptyArray(), 0, 0, caption?.let { TdApi.FormattedText(it, emptyArray()) })
                MediaType.VIDEO -> TdApi.InputMessageVideo(inputFile, null, emptyArray(), 0, 0, 0, false, false, caption?.let { TdApi.FormattedText(it, emptyArray()) })
                else -> TdApi.InputMessageDocument(inputFile, null, false, caption?.let { TdApi.FormattedText(it, emptyArray()) })
            }
            val tdMessage = tdLibClient.sendAsync<TdApi.Message>(TdApi.SendMessage(chatId, 0, null, null, null, content))
            Result.success(TdLibMapper.toDomainMessage(tdMessage))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun editMessage(chatId: Long, messageId: Long, newText: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.EditMessageText(chatId, messageId, null, TdLibMapper.toTdInputMessageText(newText)))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun deleteMessage(chatId: Long, messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.DeleteMessages(chatId, longArrayOf(messageId), true))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun addReaction(chatId: Long, messageId: Long, emoji: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.AddMessageReaction(chatId, messageId, TdApi.ReactionTypeEmoji(emoji), false, false))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun forwardMessage(chatId: Long, fromChatId: Long, messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.ForwardMessages(chatId, fromChatId, longArrayOf(messageId), null, false, false, null))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun downloadFile(fileId: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val path = tdLibClient.downloadFile(fileId)
            Result.success(path ?: "")
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun searchMessages(chatId: Long, query: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val result = tdLibClient.sendAsync<TdApi.Messages>(TdApi.SearchChatMessages(chatId, query, null, 0, 0, 50, emptyList(), 0))
            val messages = result.messages.mapNotNull { TdLibMapper.toDomainMessage(it as TdApi.Message) }
            Result.success(messages)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun sendPoll(chatId: Long, question: String, options: List<String>, isAnonymous: Boolean, allowsMultipleAnswers: Boolean, isQuiz: Boolean, correctOptionId: Int?, explanation: String?): Result<Poll> = withContext(Dispatchers.IO) {
        try {
            val tdOptions = options.map { TdApi.PollOption(it, TdApi.InputMessageText(TdApi.FormattedText(it, emptyArray()), false, false), 0) }.toTypedArray()
            val content = TdApi.InputMessagePoll(question, tdOptions, 0, isAnonymous, allowsMultipleAnswers, isQuiz, correctOptionId ?: 0, explanation ?: "", 0, emptyArray())
            val tdMessage = tdLibClient.sendAsync<TdApi.Message>(TdApi.SendMessage(chatId, 0, null, null, null, content))
            val poll = Poll((tdMessage.content as TdApi.MessagePoll).poll.id, question, options, 0, false, isAnonymous, allowsMultipleAnswers, isQuiz, correctOptionId, explanation)
            Result.success(poll)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun voteInPoll(chatId: Long, messageId: Long, optionIds: List<Int>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.SetPollAnswer(chatId, messageId, optionIds.toIntArray()))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
