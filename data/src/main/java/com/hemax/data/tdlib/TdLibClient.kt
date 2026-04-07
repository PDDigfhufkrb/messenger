package com.hemax.data.tdlib

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class TdLibClient(
    private val context: Context,
    private val config: TdConfig
) : Client.ResultHandler {

    private val client: Client = Client.create()
    private var isClosed = false
    private val nextRequestId = AtomicLong(0)
    private val handlers = ConcurrentHashMap<Long, (TdApi.Object) -> Unit>()
    
    // Потоки обновлений (разные типы для удобства)
    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 100)
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()
    
    // Специализированные потоки для часто используемых обновлений
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState: StateFlow<TdApi.AuthorizationState?> = _authorizationState
    
    private val _newMessages = MutableSharedFlow<TdApi.UpdateNewMessage>(extraBufferCapacity = 50)
    val newMessages: SharedFlow<TdApi.UpdateNewMessage> = _newMessages.asSharedFlow()
    
    private val _messageSendSucceeded = MutableSharedFlow<TdApi.UpdateMessageSendSucceeded>(extraBufferCapacity = 50)
    val messageSendSucceeded: SharedFlow<TdApi.UpdateMessageSendSucceeded> = _messageSendSucceeded.asSharedFlow()
    
    private val _chatUpdates = MutableSharedFlow<TdApi.UpdateChat>(extraBufferCapacity = 50)
    val chatUpdates: SharedFlow<TdApi.UpdateChat> = _chatUpdates.asSharedFlow()
    
    private val _fileUpdates = MutableSharedFlow<TdApi.UpdateFile>(extraBufferCapacity = 50)
    val fileUpdates: SharedFlow<TdApi.UpdateFile> = _fileUpdates.asSharedFlow()
    
    // Кэш загруженных файлов
    private val filePaths = ConcurrentHashMap<Int, String>()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        setupClient()
        startPolling()
    }
    
    private fun setupClient() {
        val parameters = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = File(context.filesDir, "tdlib").absolutePath
            filesDirectory = File(context.cacheDir, "tdlib_files").absolutePath
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = true  // Включаем E2EE
            apiId = config.apiId
            apiHash = config.apiHash
            systemLanguageCode = "ru"
            deviceModel = android.os.Build.MODEL
            systemVersion = android.os.Build.VERSION.RELEASE
            applicationVersion = "1.0.0"
            enableStorageOptimizer = true
            ignoreBackgroundDownloads = false
        }
        
        client.send(TdApi.SetTdlibParameters(parameters), object : Client.ResultHandler {
            override fun onResult(result: TdApi.Object) {
                if (result is TdApi.Ok) {
                    client.send(TdApi.CheckDatabaseEncryption(), defaultHandler)
                } else {
                    // Ошибка инициализации
                    scope.launch {
                        _updates.emit(TdApi.Error(400, "Failed to set TDLib parameters"))
                    }
                }
            }
        })
    }
    
    private val defaultHandler = Client.ResultHandler { obj ->
        scope.launch {
            _updates.emit(obj)
            when (obj) {
                is TdApi.UpdateAuthorizationState -> {
                    _authorizationState.emit(obj.authorizationState)
                }
                is TdApi.UpdateNewMessage -> {
                    _newMessages.emit(obj)
                }
                is TdApi.UpdateMessageSendSucceeded -> {
                    _messageSendSucceeded.emit(obj)
                }
                is TdApi.UpdateChat -> {
                    _chatUpdates.emit(obj)
                }
                is TdApi.UpdateFile -> {
                    handleFileUpdate(obj)
                    _fileUpdates.emit(obj)
                }
                else -> {}
            }
        }
    }
    
    private fun handleFileUpdate(update: TdApi.UpdateFile) {
        val file = update.file
        if (file.local.isDownloadingCompleted) {
            filePaths[file.id] = file.local.path
        }
    }
    
    private fun startPolling() {
        scope.launch {
            while (!isClosed) {
                client.receive(1.0, defaultHandler)
            }
        }
    }
    
    fun send(
        function: TdApi.Function,
        handler: Client.ResultHandler? = null
    ): Long {
        val requestId = nextRequestId.incrementAndGet()
        val wrappedHandler = handler?.let {
            Client.ResultHandler { result ->
                it.onResult(result)
                handlers.remove(requestId)
            }
        }
        handlers[requestId] = wrappedHandler ?: {}
        client.send(function, wrappedHandler ?: defaultHandler)
        return requestId
    }
    
    suspend fun <T : TdApi.Object> sendAsync(
        function: TdApi.Function
    ): T = suspendCancellableCoroutine { continuation ->
        send(function, object : Client.ResultHandler {
            @Suppress("UNCHECKED_CAST")
            override fun onResult(result: TdApi.Object) {
                when (result) {
                    is TdApi.Error -> {
                        continuation.resumeWithException(TdLibException(result.message, result.code))
                    }
                    else -> continuation.resume(result as T)
                }
            }
        })
    }
    
    /**
     * Загружает файл по его ID. Возвращает локальный путь после загрузки.
     */
    suspend fun downloadFile(fileId: Int, priority: Int = 1): String? {
        // Проверяем, не загружен ли уже
        filePaths[fileId]?.let { return it }
        
        // Запрашиваем файл
        val file = sendAsync<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        if (file.local.isDownloadingCompleted) {
            filePaths[fileId] = file.local.path
            return file.local.path
        }
        
        // Ожидаем завершения через обновления
        return suspendCoroutine { continuation ->
            var job: Job? = null
            job = scope.launch {
                fileUpdates.collect { update ->
                    if (update.file.id == fileId && update.file.local.isDownloadingCompleted) {
                        continuation.resume(update.file.local.path)
                        job?.cancel()
                    } else if (update.file.id == fileId && update.file.local.isDownloadingFailed) {
                        continuation.resumeWithException(TdLibException("Download failed", -1))
                        job?.cancel()
                    }
                }
            }
            // Таймаут 30 секунд
            scope.launch {
                delay(30000)
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(TdLibException("Download timeout", -1))
                    job?.cancel()
                }
            }
        }
    }
    
    /**
     * Получение информации о чате с кэшированием
     */
    private val chatCache = ConcurrentHashMap<Long, TdApi.Chat>()
    
    suspend fun getChat(chatId: Long): TdApi.Chat {
        return chatCache[chatId] ?: run {
            val chat = sendAsync<TdApi.Chat>(TdApi.GetChat(chatId))
            chatCache[chatId] = chat
            chat
        }
    }
    
    /**
     * Получение пользователя с кэшированием
     */
    private val userCache = ConcurrentHashMap<Long, TdApi.User>()
    
    suspend fun getUser(userId: Long): TdApi.User {
        return userCache[userId] ?: run {
            val user = sendAsync<TdApi.User>(TdApi.GetUser(userId))
            userCache[userId] = user
            user
        }
    }
    
    /**
     * Отправка действия (печатает, смотрит видео и т.д.)
     */
    suspend fun sendChatAction(chatId: Long, action: TdApi.ChatAction) {
        sendAsync<TdApi.Ok>(TdApi.SendChatAction(chatId, action))
    }
    
    /**
     * Поиск чатов
     */
    suspend fun searchChats(query: String, limit: Int = 20): List<TdApi.Chat> {
        val result = sendAsync<TdApi.Chats>(TdApi.SearchChats(query, limit))
        return result.chatIds.mapNotNull { chatId ->
            try { getChat(chatId) } catch (e: Exception) { null }
        }
    }
    
    /**
     * Поиск сообщений в чате
     */
    suspend fun searchMessages(chatId: Long, query: String, limit: Int = 50): List<TdApi.Message> {
        val result = sendAsync<TdApi.Messages>(
            TdApi.SearchChatMessages(chatId, query, null, 0, 0, limit, emptyList(), 0)
        )
        return result.messages.filterIsInstance<TdApi.Message>()
    }
    
    fun close() {
        isClosed = true
        client.close()
        scope.cancel()
    }
}

class TdLibException(message: String, val code: Int) : Exception(message)
