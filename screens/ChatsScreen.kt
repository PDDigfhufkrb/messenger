package com.hemax.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hemax.viewmodels.ChatsViewModel

@Composable
fun ChatsScreen(viewModel: ChatsViewModel, onChatClick: (Long) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("HEmax") }) }) { padding ->
        when {
            uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.chats.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Нет чатов") }
            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                items(uiState.chats) { chat ->
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { onChatClick(chat.id) }) {
                        Text(chat.title, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
