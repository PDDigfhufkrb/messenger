package com.hemax.repositories

import com.hemax.database.dao.SessionDao
import com.hemax.database.entities.SessionEntity
import com.hemax.tdlib.TdLibClient
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.drinkless.tdlib.TdApi

class AuthRepositoryImpl(
    private val tdLib: TdLibClient,
    private val sessionDao: SessionDao
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            tdLib.authorizationState.collect { state ->
                when (state) {
                    is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = AuthState.Idle
                    is TdApi.AuthorizationStateWaitCode -> _authState.value = AuthState.CodeSent
                    is TdApi.AuthorizationStateReady -> {
                        val me = tdLib.sendAsync<TdApi.User>(TdApi.GetMe())
                        sessionDao.insertSession(SessionEntity(me.id, me.phoneNumber, "active", Clock.System.now()))
                        _authState.value = AuthState.Verified(me.id.toString())
                    }
                    is TdApi.AuthorizationStateClosed -> _authState.value = AuthState.LoggedOut
                    else -> {}
                }
            }
        }
    }

    override suspend fun sendAuthCode(phoneNumber: String): Result<Unit> = runCatching {
        tdLib.sendAsync<TdApi.Ok>(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
    }

    override suspend fun verifyAuthCode(code: String): Result<String> = runCatching {
        tdLib.sendAsync<TdApi.Ok>(TdApi.CheckAuthenticationCode(code))
        ""
    }

    override fun getAuthState(): Flow<AuthState> = _authState.asStateFlow()

    override suspend fun logout(): Result<Unit> = runCatching {
        tdLib.sendAsync<TdApi.Ok>(TdApi.LogOut())
        sessionDao.clearSession()
    }
}
