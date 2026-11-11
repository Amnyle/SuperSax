package app.supersax.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.supersax.domain.backup.SettingsRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User settings in DataStore, stored as one JSON document (the same shape as in the backup), plus
 * two pieces of runtime state: the running session (so it survives the app being killed) and the
 * Do Not Disturb filter to restore.
 */
class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settingsKey = stringPreferencesKey("settings_json")
    private val activeSessionKey = stringPreferencesKey("active_session")
    private val dndPreviousKey = intPreferencesKey("dnd_previous_filter")

    val settings: Flow<SettingsRecord> = context.settingsStore.data.map { decode(it[settingsKey]) }

    private fun decode(s: String?): SettingsRecord =
        s?.let { runCatching { json.decodeFromString(SettingsRecord.serializer(), it) }.getOrNull() } ?: SettingsRecord()

    suspend fun current(): SettingsRecord = settings.first()

    /** Blocking read, only for `attachBaseContext` (the locale must be known before the first frame). */
    fun currentBlocking(): SettingsRecord = runBlocking { current() }

    suspend fun update(transform: (SettingsRecord) -> SettingsRecord) {
        context.settingsStore.edit { prefs ->
            prefs[settingsKey] = json.encodeToString(SettingsRecord.serializer(), transform(decode(prefs[settingsKey])))
        }
    }

    suspend fun replace(record: SettingsRecord) = update { record }

    val activeSession: Flow<String?> = context.settingsStore.data.map { it[activeSessionKey] }

    suspend fun saveActiveSession(value: String?) {
        context.settingsStore.edit { if (value == null) it.remove(activeSessionKey) else it[activeSessionKey] = value }
    }

    suspend fun dndPrevious(): Int? = context.settingsStore.data.first()[dndPreviousKey]

    suspend fun setDndPrevious(filter: Int?) {
        context.settingsStore.edit { if (filter == null) it.remove(dndPreviousKey) else it[dndPreviousKey] = filter }
    }
}
