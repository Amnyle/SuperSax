package app.supersax.domain.common

import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.ExerciseType
import kotlinx.serialization.Serializable

/*
 * Plain, Room-independent records. They are what repositories hand to the UI and what the backup file
 * contains, so the backup round trip can be tested on the JVM.
 */

/** One logged block (or chunk) of a session. */
@Serializable
data class BlockLogRecord(
    val stepIndex: Int,
    val blockKind: BlockKind?,
    val exerciseType: ExerciseType?,
    val catalogId: String? = null,
    val plannedSec: Int,
    val actualSec: Int,
    val lipsTired: Boolean = false,
    val skipped: Boolean = false,
    val scaleKey: String? = null,
    val bpm: Int? = null,
    val pieceId: String? = null,
    val holdSec: Int? = null,
)

/** A scale run reported during a session. */
@Serializable
data class RunRecord(val scaleKey: String, val bpm: Int, val clean: Boolean)

/** A finished session (sax or phone). */
@Serializable
data class SessionRecord(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val programId: String?,
    val dayId: String?,
    /** ISO day of week the session was planned for, or null for an unplanned session. */
    val plannedDayOfWeek: Int?,
    val phone: Boolean = false,
    val plannedSec: Int,
    val playedSec: Int,
    val tiredness: Int? = null,
    val note: String = "",
    val reedId: String? = null,
    val blocks: List<BlockLogRecord> = emptyList(),
    val runs: List<RunRecord> = emptyList(),
)

@Serializable
enum class GoalType { TIME, TEMPO, SONG_MILESTONE, RECORDING }

/** A goal. Progress is always computed from the logs, never stored. */
@Serializable
data class GoalRecord(
    val id: String,
    val type: GoalType,
    val title: String,
    val createdAtMs: Long,
    /** Epoch day of the deadline, if any. */
    val deadlineEpochDay: Long? = null,
    val weeklyMinutes: Int? = null,
    val scaleKey: String? = null,
    val bpm: Int? = null,
    val pieceId: String? = null,
    val sectionId: String? = null,
    val stage: String? = null,
    /** For recording goals: the benchmark key (exercise or piece id). */
    val benchmarkKey: String? = null,
    val archived: Boolean = false,
)

/** A tuner hold, with its downsampled trace so thresholds can be recomputed later. */
@Serializable
data class TunerHoldRecord(
    val id: String,
    val atMs: Long,
    /** Concert MIDI of the reference note. */
    val refMidi: Int,
    val stableSec: Double,
    val inTuneShare: Double,
    val toleranceCents: Double,
    val meanCents: Double,
    val driftCents: Double,
    val slopeCentsPerSec: Double,
    val minCents: Double,
    val maxCents: Double,
    /** Cents offsets relative to the reference note, about 10 points per second. */
    val trace: List<Float>,
    val traceHz: Double = 10.0,
)

@Serializable
enum class RankEventKind {
    /** A clean pair (two clean runs in a row) of a scale at a BPM. */
    CLEAN_PAIR,
    /** A note claimed in the player's range (written MIDI). */
    RANGE_NOTE,
    /** Overtone milestone completed (tuner verified). */
    OVERTONE_MILESTONE,
    /** A long-tone block completed (self-reported; Sound Iron). */
    LONG_TONE_BLOCK,
}

/**
 * A rank-relevant fact. [verified] is true when it was measured by the tuner;
 * self-reported facts are flagged as such and can carry a recording as evidence.
 */
@Serializable
data class RankEventRecord(
    val id: String,
    val atMs: Long,
    val kind: RankEventKind,
    val scaleKey: String? = null,
    val bpm: Int? = null,
    val writtenMidi: Int? = null,
    val verified: Boolean = false,
    val evidenceRecordingId: String? = null,
)

@Serializable
data class RankSnapshotRecord(val atMs: Long, val overallTier: Int, val division: Int, val pillarTiers: Map<String, Int>)

@Serializable
enum class PieceStage { NOT_STARTED, LEARNING, PLAYABLE, CLEAN_AT_TEMPO }

/** Stage of a piece, or of one section when [sectionId] is set. */
@Serializable
data class PieceProgressRecord(
    val pieceId: String,
    val sectionId: String = "",
    val stage: PieceStage,
    val updatedAtMs: Long,
    val lastPlayedAtMs: Long = 0,
    val evidenceRecordingId: String? = null,
)

/** A user-imported piece (MusicXML parsed to the score format, or a PDF), or a file attached to a metadata-only entry. */
@Serializable
data class UserPieceRecord(
    val id: String,
    val title: String,
    val kind: String, // "musicxml" | "pdf"
    /** File name inside the app's `imports/` folder. */
    val fileName: String,
    /** Parsed piece JSON for MusicXML imports. */
    val pieceJson: String? = null,
    val attachedToPieceId: String? = null,
    val importedAtMs: Long,
)

@Serializable
data class RecordingRecord(
    val id: String,
    val atMs: Long,
    val fileName: String,
    val durationMs: Long,
    val exerciseKey: String? = null,
    val pieceId: String? = null,
    val reedId: String? = null,
    val mouthpieceId: String? = null,
    val benchmark: Boolean = false,
    val note: String = "",
)

@Serializable
data class ReedRecord(
    val id: String,
    val brand: String,
    val strength: String,
    val startedEpochDay: Long,
    val retired: Boolean = false,
    val note: String = "",
)

@Serializable
data class MouthpieceRecord(val id: String, val name: String, val note: String = "")

@Serializable
enum class DrillKind { READING, SCALE_RECOGNITION, FINGERING, MATCH_NOTE, THEORY_QUIZ, OVERTONE, ALTISSIMO, DAILY, READING_SPEED }

@Serializable
data class DrillResultRecord(
    val id: String,
    val atMs: Long,
    val kind: DrillKind,
    val level: Int,
    val questions: Int,
    val correct: Int,
    val meanResponseMs: Long,
    val detail: String = "",
)

@Serializable
data class LessonProgressRecord(
    val lessonId: String,
    val completedAtMs: Long,
    val quizScore: Double,
    val attempts: Int = 1,
)

/** A user audio file for the slow-down player: a content URI reference plus named loop points. */
@Serializable
data class AudioRefRecord(
    val id: String,
    val uri: String,
    val title: String,
    val addedAtMs: Long,
    val loops: List<LoopPoint> = emptyList(),
)

@Serializable
data class LoopPoint(val name: String, val startMs: Long, val endMs: Long)

/** A stored program template (JSON of [app.supersax.domain.program.Program]); one is active. */
@Serializable
data class StoredProgram(val id: String, val json: String, val active: Boolean, val updatedAtMs: Long)
