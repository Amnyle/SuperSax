package app.supersax.domain.song

import kotlinx.serialization.Serializable

/** A named point in a song (e.g. "Chorus"), in the original recording's time. */
@Serializable
data class Checkpoint(val id: String, val name: String, val ms: Long)

/**
 * What the song player knows about one song: the detected tempo, first beat and key, the player's
 * corrections ([bpm], [firstBeatMs], [beatsPerBar] start as detected), and the checkpoints.
 */
@Serializable
data class SongSetup(
    val analyzed: Boolean = false,
    val detectedBpm: Double = 0.0,
    val bpm: Double = 0.0,
    val firstBeatMs: Long = 0,
    val beatsPerBar: Int = 4,
    /** Concert tonic pitch class (0 = C), or −1 when unknown. */
    val keyTonic: Int = -1,
    val keyMinor: Boolean = false,
    val keyConfidence: Double = 0.0,
    val checkpoints: List<Checkpoint> = emptyList(),
    /** Where the audio came from, e.g. a YouTube page address; null for an imported file. */
    val source: String? = null,
)
