package app.supersax.domain.song

import kotlinx.serialization.Serializable

/** A named point in a song (e.g. "Chorus"), in the original recording's time. */
@Serializable
data class Checkpoint(val id: String, val name: String, val ms: Long)

/**
 * What the song player knows about one song: the detected tempo, first beat and key, the player's
 * corrections ([bpm], [firstBeatMs], [beatsPerBar] start as detected), and the checkpoints.
 */
/** 2: bar 1 starts where the music starts (not in leading silence). */
const val CURRENT_ANALYSIS = 2

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
    /** Version of the analysis that produced the values ([CURRENT_ANALYSIS]); older ones are redone once. */
    val analysisVersion: Int = 1,
    /** Where the audio came from, e.g. a YouTube page address; null for an imported file. */
    val source: String? = null,
    /** For an AI version of a song: the original song's id, and what was removed (a Lalal.Stem name, e.g. "WIND"). */
    val parentId: String? = null,
    val removed: String? = null,
)

/**
 * A song and its AI versions ("no sax"…) are the same recording: they share the analysis and the bar settings.
 * Copies those shared fields from [from] into this setup, keeping this one's source and version link.
 */
fun SongSetup.withSharedFrom(from: SongSetup): SongSetup = copy(
    analyzed = from.analyzed, detectedBpm = from.detectedBpm, bpm = from.bpm, firstBeatMs = from.firstBeatMs,
    beatsPerBar = from.beatsPerBar, keyTonic = from.keyTonic, keyMinor = from.keyMinor, keyConfidence = from.keyConfidence,
    checkpoints = from.checkpoints, analysisVersion = from.analysisVersion,
)
