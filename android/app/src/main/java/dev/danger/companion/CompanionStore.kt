package dev.danger.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.companionDataStore: DataStore<Preferences> by preferencesDataStore(name = "companion")

object CompanionStore {

    val STATE_KEY = stringPreferencesKey("state")

    suspend fun save(context: Context, state: CompanionState) {
        context.companionDataStore.edit { prefs ->
            prefs[STATE_KEY] = state.toJson()
        }
    }

    fun flow(context: Context): Flow<CompanionState> =
        context.companionDataStore.data.map { prefs ->
            val parsed = CompanionState.fromJson(prefs[STATE_KEY])
            effectiveState(parsed, System.currentTimeMillis())
        }

    suspend fun current(context: Context): CompanionState {
        val prefs = context.companionDataStore.data.first()
        val parsed = CompanionState.fromJson(prefs[STATE_KEY])
        return effectiveState(parsed, System.currentTimeMillis())
    }

    fun effectiveState(state: CompanionState?, now: Long): CompanionState {
        if (state == null) return CompanionState.defaultSleepy()
        val ttl = state.ttlMs
        if (ttl != null && ttl > 0 && state.updatedAt > 0 &&
            now - state.updatedAt > ttl &&
            state.emotion != Emotion.NEUTRAL &&
            state.emotion != Emotion.SLEEPY
        ) {
            return CompanionState(
                emotion = Emotion.NEUTRAL,
                text = null,
                instanceId = state.instanceId,
                instanceLabel = state.instanceLabel,
                colorArgb = state.colorArgb,
                updatedAt = now,
                ttlMs = null,
            )
        }
        return state
    }
}
