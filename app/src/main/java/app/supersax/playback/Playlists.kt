package app.supersax.playback

import app.supersax.AppContainer
import app.supersax.R
import app.supersax.data.db.KeyValueEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A playlist of songs (song ids in order). */
@Serializable
data class Playlist(val id: String, val name: String, val songIds: List<String> = emptyList(), val createdAtMs: Long = 0)

/** Playlists, kept in the key-value table. "To learn" and "Learnt" are made the first time. */
class Playlists(private val c: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(Playlist.serializer())
    private val lock = Mutex()

    val all: Flow<List<Playlist>> = c.db.kv().observe(KEY).map { e ->
        e?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() } ?: defaults()
    }

    private fun defaults() = listOf(
        Playlist("to-learn", c.context.getString(R.string.pl_to_learn)),
        Playlist("learnt", c.context.getString(R.string.pl_learnt)),
    )

    private suspend fun load(): List<Playlist> =
        c.db.kv().get(KEY)?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() } ?: defaults()

    private suspend fun edit(f: (List<Playlist>) -> List<Playlist>) = lock.withLock {
        c.db.kv().put(KeyValueEntity(KEY, json.encodeToString(ser, f(load()))))
    }

    suspend fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        edit { it + Playlist(id, name.trim(), createdAtMs = c.clock.nowMs()) }
        return id
    }

    suspend fun rename(id: String, name: String) = edit { l -> l.map { if (it.id == id) it.copy(name = name.trim()) else it } }
    suspend fun delete(id: String) = edit { l -> l.filterNot { it.id == id } }

    /** Puts [songId] in or out of playlist [id]. */
    suspend fun setMember(id: String, songId: String, member: Boolean) = edit { l ->
        l.map { p -> if (p.id != id) p else if (member) (if (songId in p.songIds) p else p.copy(songIds = p.songIds + songId)) else p.copy(songIds = p.songIds - songId) }
    }

    /** A deleted song leaves every playlist. */
    suspend fun forget(songId: String) = edit { l -> l.map { it.copy(songIds = it.songIds - songId) } }

    companion object { const val KEY = "playlists" }
}
