package com.hemax.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Long,
    val chatId: Long,
    val senderId: Long,
    val text: String?,
    val mediaUrl: String?,
    val mediaType: String?,
    val date: Instant,
    val isOutgoing: Boolean,
    val isRead: Boolean,
    val replyToMessageId: Long?,
    val reactions: String?,
    val editDate: Instant?
)
