package com.hemax.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val phoneNumber: String,
    val firstName: String,
    val lastName: String?,
    val username: String?,
    val photoUrl: String?,
    val bio: String?,
    val isVerified: Boolean,
    val isPremium: Boolean,
    val lastSeen: Instant?
)
