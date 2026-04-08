package com.hemax.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val chatId: Long,
    val title: String,
    val description: String?,
    val memberCount: Int,
    val isChannel: Boolean,
    val username: String?,
    val inviteLink: String?
)

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    val chatId: Long,
    val userId: Long,
    val role: String,
    val joinedDate: Long
)
