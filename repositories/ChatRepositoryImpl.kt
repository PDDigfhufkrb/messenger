package com.hemax.repositories

import com.hemax.database.dao.ChatDao
import com.hemax.models.Chat
import com.hemax.models.ChatFolder
import com.hemax.tdlib.TdLibClient
import com.hemax.tdlib.TdLibMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

class ChatRepositoryImpl(
    private val tdLibClient: TdLibClient,
    private val chatDao: ChatDao
) : ChatRepository {

    override fun getChats(): Flow<List<Chat>> = flow {
        val chats = tdLibClient.sendAsync<TdApi.Chats>(TdApi.GetChats(TdApi.ChatListMain(), 100, 0))
        val chatList = chats.chatIds.mapNotNull { chatId ->
            try {
                val tdChat = tdLibClient.getChat(chatId)
                val users = emptyMap<Long, com.hemax.models.User>() // упрощённо
                TdLibMapper.toDomainChat(tdChat, users)
            } catch (e: Exception) { null }
        }
        emit(chatList)
    }.flowOn(Dispatchers.IO)

    override fun getChatById(chatId: Long): Flow<Chat?> = flow {
        val tdChat = tdLibClient.getChat(chatId)
        val chat = TdLibMapper.toDomainChat(tdChat, emptyMap())
        emit(chat)
    }

    override suspend fun pinChat(chatId: Long, isPinned: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.PinChat(chatId, isPinned, TdApi.ChatListMain()))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun deleteChat(chatId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.DeleteChat(chatId))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun markChatAsRead(chatId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.ViewMessages(chatId, longArrayOf(), null, true))
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override fun getChatFolders(): Flow<List<ChatFolder>> = flow {
        val folders = tdLibClient.sendAsync<TdApi.ChatFolders>(TdApi.GetChatFolders())
        val list = folders.chatFolders.map {
            ChatFolder(it.id, it.title, it.includedChatIds.toList(), it.excludedChatIds.toList(), it.order)
        }
        emit(list)
    }
}
