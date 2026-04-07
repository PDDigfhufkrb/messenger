package com.hemax.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hemax.domain.repository.SettingsRepository
import com.hemax.domain.repository.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hemax_settings")

class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {
    
    companion object {
        private val THEME_KEY = stringPreferencesKey("theme")
        private val E2EE_KEY = stringPreferencesKey("e2ee_enabled")
        private val DARK_MODE_KEY = stringPreferencesKey("dark_mode")
        private val NOTIFICATION_ENABLED_KEY = stringPreferencesKey("notifications_enabled")
    }
    
    override fun getTheme(): Flow<Theme> {
        return context.dataStore.data
            .catch { exception -> 
                // Если DataStore ещё не готов, возвращаем значение по умолчанию
                emit(emptyPreferences())
            }
            .map { preferences ->
                val themeStr = preferences[THEME_KEY] ?: "DYNAMIC"
                when (themeStr) {
                    "LIGHT" -> Theme.LIGHT
                    "DARK" -> Theme.DARK
                    else -> Theme.DYNAMIC
                }
            }
            .flowOn(Dispatchers.IO)
    }
    
    override suspend fun setTheme(theme: Theme): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences[THEME_KEY] = theme.name
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun isDarkMode(): Flow<Boolean> {
        return context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                preferences[DARK_MODE_KEY]?.toBoolean() ?: false
            }
            .flowOn(Dispatchers.IO)
    }
    
    override suspend fun setDarkMode(isDark: Boolean): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences[DARK_MODE_KEY] = isDark.toString()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun isE2EEEnabled(): Flow<Boolean> {
        return context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                preferences[E2EE_KEY]?.toBoolean() ?: true  // По умолчанию включено
            }
            .flowOn(Dispatchers.IO)
    }
    
    override suspend fun setE2EEEnabled(enabled: Boolean): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences[E2EE_KEY] = enabled.toString()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun isNotificationsEnabled(): Flow<Boolean> {
        return context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                preferences[NOTIFICATION_ENABLED_KEY]?.toBoolean() ?: true
            }
            .flowOn(Dispatchers.IO)
    }
    
    override suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit> {
        return try {
            context.dataStore.edit { preferences ->
                preferences[NOTIFICATION_ENABLED_KEY] = enabled.toString()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
