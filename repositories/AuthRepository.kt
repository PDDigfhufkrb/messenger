package com.hemax.repositories

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun sendAuthCode(phoneNumber: String): Result<Unit>
    suspend fun verifyAuthCode(code: String): Result<String>
    fun getAuthState(): Flow<AuthState>
    suspend fun logout(): Result<Unit>
}

sealed class AuthState {
    object Idle : AuthState()
    object CodeSent : AuthState()
    data class Verified(val sessionId: String) : AuthState()
    object LoggedOut : AuthState()
}
