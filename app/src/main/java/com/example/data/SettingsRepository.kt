package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "signal_yard_settings")

data class AppSettings(
    val soundEnabled: Boolean = true,
    val masterVolume: Float = 0.75f,
    val hapticsEnabled: Boolean = true,
    val colorblindSymbols: Boolean = true,
    val showRouteGuidance: Boolean = true,
    val defaultSpeedMultiplier: Float = 1f
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val COLORBLIND_SYMBOLS = booleanPreferencesKey("colorblind_symbols")
        val SHOW_ROUTE_GUIDANCE = booleanPreferencesKey("show_route_guidance")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            masterVolume = prefs[Keys.MASTER_VOLUME] ?: 0.75f,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            colorblindSymbols = prefs[Keys.COLORBLIND_SYMBOLS] ?: true,
            showRouteGuidance = prefs[Keys.SHOW_ROUTE_GUIDANCE] ?: true,
            defaultSpeedMultiplier = prefs[Keys.DEFAULT_SPEED] ?: 1f
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = AppSettings(
                soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
                masterVolume = prefs[Keys.MASTER_VOLUME] ?: 0.75f,
                hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
                colorblindSymbols = prefs[Keys.COLORBLIND_SYMBOLS] ?: true,
                showRouteGuidance = prefs[Keys.SHOW_ROUTE_GUIDANCE] ?: true,
                defaultSpeedMultiplier = prefs[Keys.DEFAULT_SPEED] ?: 1f
            )
            val updated = transform(current)
            prefs[Keys.SOUND_ENABLED] = updated.soundEnabled
            prefs[Keys.MASTER_VOLUME] = updated.masterVolume.coerceIn(0f, 1f)
            prefs[Keys.HAPTICS_ENABLED] = updated.hapticsEnabled
            prefs[Keys.COLORBLIND_SYMBOLS] = updated.colorblindSymbols
            prefs[Keys.SHOW_ROUTE_GUIDANCE] = updated.showRouteGuidance
            prefs[Keys.DEFAULT_SPEED] = updated.defaultSpeedMultiplier
        }
    }
}
