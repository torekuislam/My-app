package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val biometricLockEnabled: Boolean = false,
    val defaultUserAgent: String = DEFAULT_WINDOWS_CHROME_UA,
    val desktopViewportWidth: Int = 1280,
    val enforceDesktopFacebookUrl: Boolean = true,
) {
    companion object {
        const val CHROME_135_WIN_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
        const val CHROME_134_WIN_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
        const val CHROME_133_WIN_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        const val DEFAULT_WINDOWS_CHROME_UA = CHROME_135_WIN_UA
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "session_deck_prefs")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val DEFAULT_UA = stringPreferencesKey("default_ua")
        val DESKTOP_WIDTH = intPreferencesKey("desktop_width")
        val ENFORCE_DESKTOP_FB_URL = booleanPreferencesKey("enforce_desktop_fb_url")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val theme = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.DARK)
        AppSettings(
            themeMode = theme,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            defaultUserAgent = prefs[Keys.DEFAULT_UA]?.takeIf { it.isNotBlank() }
                ?: AppSettings.DEFAULT_WINDOWS_CHROME_UA,
            desktopViewportWidth = prefs[Keys.DESKTOP_WIDTH] ?: 1280,
            enforceDesktopFacebookUrl = prefs[Keys.ENFORCE_DESKTOP_FB_URL] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setDefaultUserAgent(ua: String) {
        context.settingsDataStore.edit {
            it[Keys.DEFAULT_UA] = ua.trim().ifBlank { AppSettings.DEFAULT_WINDOWS_CHROME_UA }
        }
    }

    suspend fun setDesktopViewportWidth(width: Int) {
        context.settingsDataStore.edit { it[Keys.DESKTOP_WIDTH] = width.coerceIn(980, 1920) }
    }

    suspend fun setEnforceDesktopFacebookUrl(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ENFORCE_DESKTOP_FB_URL] = enabled }
    }
}
