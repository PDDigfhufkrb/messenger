package com.hemax.models

import kotlinx.datetime.Instant

data class GroupInfo(
    val chatId: Long,
    val title: String,
    val description: String?,
    val memberCount: Int,
    val members: List<GroupMember>,
    val permissions: GroupPermissions,
    val isChannel: Boolean,
    val username: String?,
    val inviteLink: String?
)

data class GroupMember(
    val userId: Long,
    val user: User,
    val role: GroupMemberRole,
    val joinedDate: Instant
)

enum class GroupMemberRole {
    CREATOR, ADMINISTRATOR, MEMBER, RESTRICTED
}

data class GroupPermissions(
    val canSendMessages: Boolean,
    val canSendMedia: Boolean,
    val canSendStickers: Boolean,
    val canSendPolls: Boolean,
    val canAddMembers: Boolean,
    val canPinMessages: Boolean,
    val canChangeInfo: Boolean
)
