package com.hemax.repositories

import com.hemax.models.ScheduledMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface ScheduledMessageRepository {
    suspend fun scheduleMessage(chatId: Long, text: String, scheduledTime: Instant, mediaData: MediaData?): Result<Long>
    suspend fun cancelScheduledMessage(messageId: Long): Result<Unit>
    fun getScheduledMessages(): Flow<List<ScheduledMessage>>
    suspend fun processPendingMessages()
}
