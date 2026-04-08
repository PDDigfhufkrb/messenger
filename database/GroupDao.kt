package com.hemax.database.dao

import androidx.room.*
import com.hemax.database.entities.GroupEntity
import com.hemax.database.entities.GroupMemberEntity

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE chatId = :chatId")
    suspend fun getGroup(chatId: Long): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Query("SELECT * FROM group_members WHERE chatId = :chatId")
    suspend fun getMembers(chatId: Long): List<GroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)
}
