package com.hemax.tdlib

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

class TdLibClient(
    private val context: Context,
    private val config: TdConfig
) : Client.ResultHandler {

    private val client = Client.create()
    private var isClosed = false
    private val nextRequestId = AtomicLong(0)
    private val handlers = ConcurrentHashMap<Long, (TdApi.Object) -> Unit>()

    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 100)
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()

    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState: StateFlow<TdApi.AuthorizationState?> = _authorizationState

    private val _newMessages = MutableSharedFlow<TdApi.UpdateNewMessage>(extraBufferCapacity = 50)
    val newMessages: SharedFlow<TdApi.UpdateNewMessage> = _newMessages.asSharedFlow()

    private val _fileUpdates = MutableSharedFlow<TdApi.UpdateFile>(extraBufferCapacity = 50)
    val fileUpdates: SharedFlow<TdApi.UpdateFile> = _fileUpdates.asSharedFlow()

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
            useSecretChats = true
            apiId = config.apiId
            apiHash = config.apiHash
            systemLanguageCode = "ru"
            deviceModel = android.os.Build.MODEL
            systemVersion = android.os.Build.VERSION.RELEASE
            applicationVersion = "1.0.0"
            enableStorageOptimizer = true
            ignoreBackgroundDownloads = false
        }
        client.send(TdApi.SetTdlibParameters(parameters), defaultHandler)
    }

    private val defaultHandler = Client.ResultHandler { obj ->
        scope.launch {
            _updates.emit(obj)
            when (obj) {
                is TdApi.UpdateAuthorizationState -> _authorizationState.emit(obj.authorizationState)
                is TdApi.UpdateNewMessage -> _newMessages.emit(obj)
                is TdApi.UpdateFile -> {
                    if (obj.file.local.isDownloadingCompleted) {
                        filePaths[obj.file.id] = obj.file.local.path
                    }
                    _fileUpdates.emit(obj)
                }
                else -> {}
            }
        }
    }

    private fun startPolling() {
        scope.launch {
            while (!isClosed) {
                client.receive(1.0, defaultHandler)
            }
        }
    }

    fun send(function: TdApi.Function, handler: Client.ResultHandler? = null): Long {
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

    suspend fun <T : TdApi.Object> sendAsync(function: TdApi.Function): T =
        suspendCancellableCoroutine { continuation ->
            send(function, object : Client.ResultHandler {
                @Suppress("UNCHECKED_CAST")
                override fun onResult(result: TdApi.Object) {
                    when (result) {
                        is TdApi.Error -> continuation.resumeWithException(TdLibException(result.message, result.code))
                        else -> continuation.resume(result as T)
                    }
                }
            })
        }

    suspend fun downloadFile(fileId: Int, priority: Int = 1): String? {
        filePaths[fileId]?.let { return it }
        val file = sendAsync<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        if (file.local.isDownloadingCompleted) {
            filePaths[fileId] = file.local.path
            return file.local.path
        }
        return suspendCancellableCoroutine { continuation ->
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
            scope.launch {
                delay(30000)
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(TdLibException("Download timeout", -1))
                    job?.cancel()
                }
            }
        }
    }

    suspend fun getChat(chatId: Long): TdApi.Chat = sendAsync(TdApi.GetChat(chatId))
    suspend fun getUser(userId: Long): TdApi.User = sendAsync(TdApi.GetUser(userId))

    fun close() {
        isClosed = true
        client.close()
        scope.cancel()
    }
}

class TdLibException(message: String, val code: Int) : Exception(message)
