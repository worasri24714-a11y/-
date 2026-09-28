package app.copyruler.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    /** Volume down = next, volume up = previous, only while the copy screen is open. */
    val volumeKeys: Boolean = true,
    val haptics: Boolean = true,
    val fontSizeSp: Int = DEFAULT_FONT_SIZE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** In photo mode, move the band from one detected text line to the next. */
    val snapToLines: Boolean = true,
) {
    companion object {
        const val MIN_FONT_SIZE = 16
        const val MAX_FONT_SIZE = 34
        const val DEFAULT_FONT_SIZE = 22
    }
}

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                volumeKeys = prefs[VOLUME_KEYS] ?: true,
                haptics = prefs[HAPTICS] ?: true,
                fontSizeSp = (prefs[FONT_SIZE] ?: AppSettings.DEFAULT_FONT_SIZE)
                    .coerceIn(AppSettings.MIN_FONT_SIZE, AppSettings.MAX_FONT_SIZE),
                themeMode = prefs[THEME]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.SYSTEM,
                snapToLines = prefs[SNAP_TO_LINES] ?: true,
            )
        }

    suspend fun setVolumeKeys(enabled: Boolean) {
        dataStore.edit { it[VOLUME_KEYS] = enabled }
    }

    suspend fun setHaptics(enabled: Boolean) {
        dataStore.edit { it[HAPTICS] = enabled }
    }

    suspend fun setFontSize(sizeSp: Int) {
        dataStore.edit {
            it[FONT_SIZE] = sizeSp.coerceIn(AppSettings.MIN_FONT_SIZE, AppSettings.MAX_FONT_SIZE)
        }
    }

    suspend fun setSnapToLines(enabled: Boolean) {
        dataStore.edit { it[SNAP_TO_LINES] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    private companion object {
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys")
        val HAPTICS = booleanPreferencesKey("haptics")
        val FONT_SIZE = intPreferencesKey("font_size_sp")
        val THEME = stringPreferencesKey("theme_mode")
        val SNAP_TO_LINES = booleanPreferencesKey("snap_to_lines")
    }
}
