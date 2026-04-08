package com.hemax.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val text: String,
    val mediaPath: String?,
    val mediaType: String?,
    val scheduledTime: Instant,
    val isSent: Boolean = false
)
