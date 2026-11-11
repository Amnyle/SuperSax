package app.supersax.data.repo

import app.supersax.data.db.KeyValueEntity
import app.supersax.data.db.SaxDatabase
import app.supersax.domain.song.SongSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Song player data, one JSON document per song in the key-value table (no schema change needed). */
class SongRepository(private val db: SaxDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun key(songId: String) = "song:$songId"

    fun observe(songId: String): Flow<SongSetup> = db.kv().observe(key(songId)).map { e ->
        e?.let { runCatching { json.decodeFromString(SongSetup.serializer(), it.json) }.getOrNull() } ?: SongSetup()
    }

    suspend fun get(songId: String): SongSetup =
        db.kv().get(key(songId))?.let { runCatching { json.decodeFromString(SongSetup.serializer(), it.json) }.getOrNull() } ?: SongSetup()

    suspend fun save(songId: String, s: SongSetup) = db.kv().put(KeyValueEntity(key(songId), json.encodeToString(SongSetup.serializer(), s)))

    suspend fun delete(songId: String) = db.kv().delete(key(songId))
}
