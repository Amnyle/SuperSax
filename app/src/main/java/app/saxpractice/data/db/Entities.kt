package app.saxpractice.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.saxpractice.domain.common.DrillKind
import app.saxpractice.domain.common.GoalType
import app.saxpractice.domain.common.PieceStage
import app.saxpractice.domain.common.RankEventKind
import app.saxpractice.domain.program.BlockKind
import app.saxpractice.domain.program.ExerciseType

/** A program template, stored as its JSON document (it is always edited and resolved as a whole). */
@Entity(tableName = "program")
data class ProgramEntity(@PrimaryKey val id: String, val json: String, val active: Boolean, val updatedAtMs: Long)

@Entity(tableName = "session", indices = [Index("startedAtMs")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val programId: String?,
    val dayId: String?,
    val plannedDayOfWeek: Int?,
    val phone: Boolean,
    val plannedSec: Int,
    val playedSec: Int,
    val tiredness: Int?,
    val note: String,
    val reedId: String?,
    /** Scale runs reported during the session, JSON list of RunRecord. */
    val runsJson: String,
)

@Entity(
    tableName = "session_block_log",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class SessionBlockLogEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sessionId: String,
    val stepIndex: Int,
    val blockKind: BlockKind?,
    val exerciseType: ExerciseType?,
    val catalogId: String?,
    val plannedSec: Int,
    val actualSec: Int,
    val lipsTired: Boolean,
    val skipped: Boolean,
    val scaleKey: String?,
    val bpm: Int?,
    val pieceId: String?,
    val holdSec: Int?,
)

@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey val id: String,
    val type: GoalType,
    val title: String,
    val createdAtMs: Long,
    val deadlineEpochDay: Long?,
    val weeklyMinutes: Int?,
    val scaleKey: String?,
    val bpm: Int?,
    val pieceId: String?,
    val sectionId: String?,
    val stage: String?,
    val benchmarkKey: String?,
    val archived: Boolean,
)

@Entity(tableName = "tuner_hold", indices = [Index("refMidi"), Index("atMs")])
data class TunerHoldEntity(
    @PrimaryKey val id: String,
    val atMs: Long,
    val refMidi: Int,
    val stableSec: Double,
    val inTuneShare: Double,
    val toleranceCents: Double,
    val meanCents: Double,
    val driftCents: Double,
    val slopeCentsPerSec: Double,
    val minCents: Double,
    val maxCents: Double,
    /** Downsampled cents trace, JSON list of floats. */
    val traceJson: String,
    val traceHz: Double,
)

@Entity(tableName = "rank_event", indices = [Index("kind")])
data class RankEventEntity(
    @PrimaryKey val id: String,
    val atMs: Long,
    val kind: RankEventKind,
    val scaleKey: String?,
    val bpm: Int?,
    val writtenMidi: Int?,
    val verified: Boolean,
    val evidenceRecordingId: String?,
)

@Entity(tableName = "rank_snapshot")
data class RankSnapshotEntity(@PrimaryKey val atMs: Long, val overallTier: Int, val division: Int, val pillarTiersJson: String)

@Entity(tableName = "piece_progress", primaryKeys = ["pieceId", "sectionId"])
data class PieceProgressEntity(
    val pieceId: String,
    val sectionId: String,
    val stage: PieceStage,
    val updatedAtMs: Long,
    val lastPlayedAtMs: Long,
    val evidenceRecordingId: String?,
)

@Entity(tableName = "user_piece")
data class UserPieceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val fileName: String,
    val pieceJson: String?,
    val attachedToPieceId: String?,
    val importedAtMs: Long,
)

@Entity(tableName = "recording", indices = [Index("atMs")])
data class RecordingEntity(
    @PrimaryKey val id: String,
    val atMs: Long,
    val fileName: String,
    val durationMs: Long,
    val exerciseKey: String?,
    val pieceId: String?,
    val reedId: String?,
    val mouthpieceId: String?,
    val benchmark: Boolean,
    val note: String,
)

@Entity(tableName = "reed")
data class ReedEntity(
    @PrimaryKey val id: String,
    val brand: String,
    val strength: String,
    val startedEpochDay: Long,
    val retired: Boolean,
    val note: String,
)

@Entity(tableName = "mouthpiece")
data class MouthpieceEntity(@PrimaryKey val id: String, val name: String, val note: String)

@Entity(tableName = "drill_result", indices = [Index("kind")])
data class DrillResultEntity(
    @PrimaryKey val id: String,
    val atMs: Long,
    val kind: DrillKind,
    val level: Int,
    val questions: Int,
    val correct: Int,
    val meanResponseMs: Long,
    val detail: String,
)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(@PrimaryKey val lessonId: String, val completedAtMs: Long, val quizScore: Double, val attempts: Int)

@Entity(tableName = "audio_ref")
data class AudioRefEntity(@PrimaryKey val id: String, val uri: String, val title: String, val addedAtMs: Long, val loopsJson: String)

/** Single-row documents (player state, onboarding answers), keyed by name. */
@Entity(tableName = "kv")
data class KeyValueEntity(@PrimaryKey val key: String, val json: String)
