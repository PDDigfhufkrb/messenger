package com.hemax.di

import com.hemax.database.AppDatabase
import com.hemax.repositories.*
import com.hemax.tdlib.TdConfig
import com.hemax.tdlib.TdLibClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { TdConfig(apiId = 123456, apiHash = "YOUR_API_HASH") } // Замените на реальные
    single { TdLibClient(androidContext(), get()) }

    single { AppDatabase.getInstance(androidContext(), "strong_password") }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().chatDao() }
    single { get<AppDatabase>().messageDao() }
    single { get<AppDatabase>().sessionDao() }
    single { get<AppDatabase>().groupDao() }
    single { get<AppDatabase>().scheduledMessageDao() }

    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get()) }
    single<MessageRepository> { MessageRepositoryImpl(get()) }
    // Остальные репозитории пока заглушки, добавьте по мере необходимости
    single<UserRepository> { TODO() }
    single<SettingsRepository> { TODO() }
    single<GroupRepository> { TODO() }
    single<ScheduledMessageRepository> { TODO() }
    single<NotificationRepository> { TODO() }
}
