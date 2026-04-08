package com.hemax.repositories

import com.hemax.models.GroupInfo
import com.hemax.models.GroupMemberRole
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getGroupInfo(chatId: Long): Flow<GroupInfo?>
    suspend fun createGroup(title: String, userIds: List<Long>, description: String?): Result<GroupInfo>
    suspend fun createChannel(title: String, description: String?, isPublic: Boolean): Result<GroupInfo>
    suspend fun addMembers(chatId: Long, userIds: List<Long>): Result<Unit>
    suspend fun removeMember(chatId: Long, userId: Long): Result<Unit>
    suspend fun changeMemberRole(chatId: Long, userId: Long, role: GroupMemberRole): Result<Unit>
    suspend fun leaveGroup(chatId: Long): Result<Unit>
    suspend fun deleteGroup(chatId: Long): Result<Unit>
    suspend fun generateInviteLink(chatId: Long): Result<String>
    suspend fun joinByInviteLink(link: String): Result<GroupInfo>
}
