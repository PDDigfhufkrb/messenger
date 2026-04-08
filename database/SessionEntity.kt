package com.hemax.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val userId: Long,
    val phoneNumber: String,
    val sessionId: String,
    val createdAt: Instant = Instant.fromEpochSeconds(System.currentTimeMillis() / 1000)
)
