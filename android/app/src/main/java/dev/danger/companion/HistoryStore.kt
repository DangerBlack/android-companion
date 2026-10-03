package dev.danger.companion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "history")

object HistoryStore {

    val EVENTS_KEY = stringPreferencesKey("events")

    const val MAX_ENTRIES = 100

    suspend fun append(context: Context, event: CompanionEvent) {
        context.historyDataStore.edit { prefs ->
            val existing = decode(prefs[EVENTS_KEY])
            val updated = (listOf(event) + existing).take(MAX_ENTRIES)
            prefs[EVENTS_KEY] = encode(updated)
        }
    }

    fun flow(context: Context): Flow<List<CompanionEvent>> =
        context.historyDataStore.data.map { prefs -> decode(prefs[EVENTS_KEY]) }

    suspend fun clear(context: Context) {
        context.historyDataStore.edit { prefs -> prefs.remove(EVENTS_KEY) }
    }

    private fun encode(events: List<CompanionEvent>): String {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    private fun decode(raw: String?): List<CompanionEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    CompanionEvent.fromJson(array.optString(i, null))?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
