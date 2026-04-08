package com.hemax.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val photoUrl: String?,
    val unreadCount: Int,
    val isPinned: Boolean,
    val order: Long,
    val draftMessage: String?,
    val type: String,
    val memberCount: Int?,
    val isChannel: Boolean
)
