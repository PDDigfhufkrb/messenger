package com.hemax.models

import kotlinx.datetime.Instant

data class ScheduledMessage(
    val id: Long,
    val chatId: Long,
    val text: String,
    val mediaPath: String?,
    val mediaType: String?,
    val scheduledTime: Instant,
    val isSent: Boolean = false
)
