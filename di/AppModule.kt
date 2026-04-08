package com.hemax.di

import com.hemax.database.AppDatabase
import com.hemax.repositories.*
import com.hemax.tdlib.TdConfig
import com.hemax.tdlib.TdLibClient
import com.hemax.viewmodels.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { TdConfig(apiId = YOUR_API_ID, apiHash = "YOUR_API_HASH") }
    single { TdLibClient(androidContext(), get()) }

    single { AppDatabase.getInstance(androidContext(), "your_strong_password") }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().chatDao() }
    single { get<AppDatabase>().messageDao() }
    single { get<AppDatabase>().sessionDao() }
    single { get<AppDatabase>().groupDao() }
    single { get<AppDatabase>().scheduledMessageDao() }

    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    // single<ChatRepository> { ChatRepositoryImpl(get(), get()) }
    // single<MessageRepository> { MessageRepositoryImpl(get(), get()) }
    // ... остальные репозитории

    viewModelOf(::AuthViewModel)
    // viewModelOf(::ChatsViewModel)
    // viewModelOf(::ChatViewModel)
}
