package com.hemax.data.repository

import com.hemax.data.database.dao.UserDao
import com.hemax.data.database.entity.UserEntity
import com.hemax.data.tdlib.TdLibClient
import com.hemax.data.tdlib.TdLibMapper
import com.hemax.domain.model.User
import com.hemax.domain.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import kotlinx.datetime.Clock

class UserRepositoryImpl(
    private val tdLibClient: TdLibClient,
    private val userDao: UserDao
) : UserRepository {
    
    private val _currentUser = MutableStateFlow<User?>(null)
    
    init {
        // Подписываемся на обновления пользователя
        CoroutineScope(Dispatchers.IO).launch {
            tdLibClient.updates.collect { update ->
                when (update) {
                    is TdApi.UpdateUser -> {
                        val user = TdLibMapper.toDomainUser(update.user)
                        saveUserToDb(user)
                        if (_currentUser.value?.id == user.id) {
                            _currentUser.emit(user)
                        }
                    }
                    is TdApi.UpdateUserStatus -> {
                        // Обновляем статус пользователя в кэше
                        val current = _currentUser.value
                        if (current?.id == update.userId) {
                            _currentUser.emit(current.copy(lastSeen = Clock.System.now()))
                        }
                    }
                }
            }
        }
        
        // Загружаем текущего пользователя при старте
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tdUser = tdLibClient.sendAsync<TdApi.User>(TdApi.GetMe())
                val user = TdLibMapper.toDomainUser(tdUser)
                saveUserToDb(user)
                _currentUser.emit(user)
            } catch (e: Exception) {
                // Пользователь не авторизован
            }
        }
    }
    
    private suspend fun saveUserToDb(user: User) {
        userDao.insertUser(
            UserEntity(
                id = user.id,
                phoneNumber = user.phoneNumber,
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
                photoUrl = user.photoUrl,
                bio = user.bio,
                isVerified = user.isVerified,
                isPremium = user.isPremium,
                lastSeen = user.lastSeen
            )
        )
    }
    
    override fun getCurrentUser(): Flow<User?> = _currentUser.asStateFlow()
    
    override fun getUserById(userId: Long): Flow<User?> = flow {
        // Сначала из кэша TDLib
        val tdUser = try {
            tdLibClient.getUser(userId)
        } catch (e: Exception) {
            null
        }
        val user = tdUser?.let { TdLibMapper.toDomainUser(it) }
        if (user != null) {
            emit(user)
            saveUserToDb(user)
        } else {
            // Потом из базы
            val dbUser = userDao.getUserById(userId)
            dbUser?.let {
                emit(
                    User(
                        id = it.id,
                        phoneNumber = it.phoneNumber,
                        firstName = it.firstName,
                        lastName = it.lastName,
                        username = it.username,
                        photoUrl = it.photoUrl,
                        bio = it.bio,
                        isVerified = it.isVerified,
                        isPremium = it.isPremium,
                        lastSeen = it.lastSeen
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun updateProfile(firstName: String, lastName: String?): Result<Unit> {
        return try {
            tdLibClient.sendAsync<TdApi.Ok>(
                TdApi.SetName(firstName, lastName ?: "")
            )
            // Обновляем текущего пользователя
            val updatedUser = tdLibClient.sendAsync<TdApi.User>(TdApi.GetMe())
            _currentUser.emit(TdLibMapper.toDomainUser(updatedUser))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun setTypingStatus(chatId: Long, isTyping: Boolean): Result<Unit> {
        return try {
            val action = if (isTyping) TdApi.ChatActionTyping() else TdApi.ChatActionCancel()
            tdLibClient.sendChatAction(chatId, action)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun setProfilePhoto(photoPath: String): Result<Unit> {
        return try {
            val inputFile = TdApi.InputFileLocal(photoPath)
            tdLibClient.sendAsync<TdApi.Ok>(TdApi.SetProfilePhoto(inputFile))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun searchUsers(query: String, limit: Int): Result<List<User>> {
        return try {
            val result = tdLibClient.sendAsync<TdApi.Users>(
                TdApi.SearchUsers(query, limit)
            )
            val users = result.userIds.mapNotNull { userId ->
                try {
                    val tdUser = tdLibClient.getUser(userId)
                    TdLibMapper.toDomainUser(tdUser)
                } catch (e: Exception) { null }
            }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
