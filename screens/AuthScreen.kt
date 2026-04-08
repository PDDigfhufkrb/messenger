package com.hemax.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemax.viewmodels.AuthIntent
import com.hemax.viewmodels.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = koinViewModel(),
    onAuthenticated: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AuthEffect.NavigateToMain -> onAuthenticated()
                is AuthEffect.ShowError -> { /* показать Snackbar */ }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!state.isCodeSent) {
            PhoneInput(
                isLoading = state.isLoading,
                onSend = { viewModel.processIntent(AuthIntent.SendCode(it)) }
            )
        } else {
            CodeInput(
                isLoading = state.isLoading,
                onVerify = { viewModel.processIntent(AuthIntent.VerifyCode(it)) }
            )
        }
        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun PhoneInput(isLoading: Boolean, onSend: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    OutlinedTextField(
        value = phone, onValueChange = { phone = it },
        label = { Text("Номер телефона") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = { onSend(phone) }, enabled = phone.isNotBlank() && !isLoading, modifier = Modifier.fillMaxWidth()) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Отправить код")
    }
}

@Composable
private fun CodeInput(isLoading: Boolean, onVerify: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    OutlinedTextField(
        value = code, onValueChange = { code = it },
        label = { Text("Код подтверждения") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = { onVerify(code) }, enabled = code.length >= 5 && !isLoading, modifier = Modifier.fillMaxWidth()) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Войти")
    }
}
