package com.hemax.repositories

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getTheme(): Flow<Theme>
    suspend fun setTheme(theme: Theme): Result<Unit>
    fun isDarkMode(): Flow<Boolean>
    suspend fun setDarkMode(isDark: Boolean): Result<Unit>
    fun isE2EEEnabled(): Flow<Boolean>
    suspend fun setE2EEEnabled(enabled: Boolean): Result<Unit>
    fun isNotificationsEnabled(): Flow<Boolean>
    suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit>
}

enum class Theme { LIGHT, DARK, DYNAMIC }
