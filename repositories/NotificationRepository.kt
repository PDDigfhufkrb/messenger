package com.hemax.repositories

import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    suspend fun registerDevice(token: String, isSandbox: Boolean): Result<Unit>
    suspend fun unregisterDevice(): Result<Unit>
    fun observeIncomingNotifications(): Flow<NotificationData>
}

data class NotificationData(
    val chatId: Long,
    val chatTitle: String,
    val senderName: String,
    val messageText: String,
    val messageId: Long,
    val isCall: Boolean = false
)
