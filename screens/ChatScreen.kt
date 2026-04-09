package com.hemax.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemax.viewmodels.ChatViewModel

@Composable
fun ChatScreen(chatId: Long, viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
    }

    Column {
        LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
            items(uiState.messages) { msg ->
                Text(text = msg.text ?: "", modifier = Modifier.padding(8.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.sendMessage(chatId, text); text = "" }) { Text("Отправить") }
        }
    }
}
