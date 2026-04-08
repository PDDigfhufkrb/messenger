package com.hemax.viewmodels

import com.hemax.repositories.AuthRepository
import com.hemax.repositories.AuthState

sealed class AuthIntent {
    data class SendCode(val phone: String) : AuthIntent()
    data class VerifyCode(val code: String) : AuthIntent()
    object ResetError : AuthIntent()
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isCodeSent: Boolean = false,
    val phoneNumber: String = "",
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

sealed class AuthEffect {
    object NavigateToMain : AuthEffect()
    data class ShowError(val msg: String) : AuthEffect()
}

class AuthViewModel(
    private val authRepo: AuthRepository
) : MviViewModel<AuthIntent, AuthUiState, AuthEffect>(AuthUiState()) {

    init {
        viewModelScope.launch {
            authRepo.getAuthState().collect { authState ->
                when (authState) {
                    is AuthState.Verified -> {
                        setState { copy(isAuthenticated = true, isLoading = false) }
                        emitEffect(AuthEffect.NavigateToMain)
                    }
                    is AuthState.CodeSent -> setState { copy(isCodeSent = true, isLoading = false) }
                    is AuthState.LoggedOut -> setState { copy(isAuthenticated = false, isCodeSent = false) }
                    else -> {}
                }
            }
        }
    }

    override suspend fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.SendCode -> sendCode(intent.phone)
            is AuthIntent.VerifyCode -> verifyCode(intent.code)
            is AuthIntent.ResetError -> setState { copy(error = null) }
        }
    }

    private suspend fun sendCode(phone: String) {
        setState { copy(isLoading = true, phoneNumber = phone, error = null) }
        val result = authRepo.sendAuthCode(phone)
        if (result.isFailure) {
            setState { copy(isLoading = false, error = result.exceptionOrNull()?.message) }
            emitEffect(AuthEffect.ShowError(result.exceptionOrNull()?.message ?: "Ошибка"))
        }
    }

    private suspend fun verifyCode(code: String) {
        setState { copy(isLoading = true, error = null) }
        val result = authRepo.verifyAuthCode(code)
        if (result.isFailure) {
            setState { copy(isLoading = false, error = result.exceptionOrNull()?.message) }
            emitEffect(AuthEffect.ShowError(result.exceptionOrNull()?.message ?: "Неверный код"))
        }
    }
}
