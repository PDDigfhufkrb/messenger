package com.hemax.repositories

import com.hemax.models.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getCurrentUser(): Flow<User?>
    fun getUserById(userId: Long): Flow<User?>
    suspend fun updateProfile(firstName: String, lastName: String?): Result<Unit>
    suspend fun setTypingStatus(chatId: Long, isTyping: Boolean): Result<Unit>
    suspend fun setProfilePhoto(photoPath: String): Result<Unit>
    suspend fun searchUsers(query: String, limit: Int): Result<List<User>>
}
