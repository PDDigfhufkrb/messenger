package com.hemax.models

import kotlinx.datetime.Instant

data class Message(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val text: String?,
    val media: Media?,
    val date: Instant,
    val isOutgoing: Boolean,
    val isRead: Boolean,
    val replyToMessageId: Long?,
    val reactions: List<Reaction>,
    val editDate: Instant?,
    val isDeleted: Boolean = false
)

data class Reaction(val emoji: String, val count: Int, val isSelected: Boolean = false)
