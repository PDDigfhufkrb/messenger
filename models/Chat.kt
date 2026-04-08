package com.hemax.models

import kotlinx.datetime.Instant

sealed class Chat {
    abstract val id: Long
    abstract val title: String
    abstract val photoUrl: String?
    abstract val lastMessage: Message?
    abstract val unreadCount: Int
    abstract val isPinned: Boolean
    abstract val order: Long
    abstract val draftMessage: String?

    data class Private(
        override val id: Long,
        override val title: String,
        override val photoUrl: String?,
        override val lastMessage: Message?,
        override val unreadCount: Int,
        override val isPinned: Boolean,
        override val order: Long,
        override val draftMessage: String?,
        val user: User,
        val isOnline: Boolean,
        val isTyping: Boolean
    ) : Chat()

    data class Group(
        override val id: Long,
        override val title: String,
        override val photoUrl: String?,
        override val lastMessage: Message?,
        override val unreadCount: Int,
        override val isPinned: Boolean,
        override val order: Long,
        override val draftMessage: String?,
        val memberCount: Int,
        val permissions: ChatPermissions,
        val isChannel: Boolean = false
    ) : Chat()
}

data class ChatPermissions(
    val canSendMessages: Boolean = true,
    val canSendMedia: Boolean = true,
    val canSendStickers: Boolean = true,
    val canSendPolls: Boolean = true
)
