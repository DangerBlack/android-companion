package dev.danger.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val relayUrl: String = SettingsStore.DEFAULT_RELAY_URL,
    val topic: String = SettingsStore.DEFAULT_TOPIC,
    val token: String = "",
    val enabled: Boolean = false,
    val bgColor: String = SettingsStore.DEFAULT_BG_COLOR,
)

object SettingsStore {

    const val DEFAULT_RELAY_URL = "http://localhost:8080"
    const val DEFAULT_TOPIC = "companion-CHANGE-ME-to-a-long-random-string"
    const val DEFAULT_BG_COLOR = "#11151C"
    const val TRANSPARENT = "transparent"

    val RELAY_URL_KEY = stringPreferencesKey("relay_url")
    val TOPIC_KEY = stringPreferencesKey("topic")
    val TOKEN_KEY = stringPreferencesKey("token")
    val ENABLED_KEY = booleanPreferencesKey("enabled")
    val BG_COLOR_KEY = stringPreferencesKey("bg_color")

    fun flow(context: Context): Flow<Settings> =
        context.settingsDataStore.data.map { prefs -> prefs.toSettings() }

    suspend fun read(context: Context): Settings =
        context.settingsDataStore.data.first().toSettings()

    suspend fun write(context: Context, settings: Settings) {
        context.settingsDataStore.edit { prefs ->
            prefs[RELAY_URL_KEY] = settings.relayUrl.trim().ifBlank { DEFAULT_RELAY_URL }
            prefs[TOPIC_KEY] = settings.topic.trim().ifBlank { DEFAULT_TOPIC }
            prefs[TOKEN_KEY] = settings.token.trim()
            prefs[ENABLED_KEY] = settings.enabled
            prefs[BG_COLOR_KEY] = settings.bgColor
        }
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[ENABLED_KEY] = enabled
        }
    }

    suspend fun setBgColor(context: Context, value: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[BG_COLOR_KEY] = value
        }
    }

    private fun Preferences.toSettings(): Settings = Settings(
        relayUrl = this[RELAY_URL_KEY]?.takeIf { it.isNotBlank() } ?: DEFAULT_RELAY_URL,
        topic = this[TOPIC_KEY]?.takeIf { it.isNotBlank() } ?: DEFAULT_TOPIC,
        token = this[TOKEN_KEY] ?: "",
        enabled = this[ENABLED_KEY] ?: false,
        bgColor = this[BG_COLOR_KEY]?.takeIf { it.isNotBlank() } ?: DEFAULT_BG_COLOR,
    )
}
