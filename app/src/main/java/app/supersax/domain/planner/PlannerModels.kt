package app.supersax.domain.planner

import app.supersax.domain.generator.Generator
import app.supersax.domain.generator.GeneratorConfig
import app.supersax.domain.program.Block
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Exercise
import app.supersax.domain.program.ExerciseType
import app.supersax.domain.program.PlayerState
import app.supersax.domain.program.ProgramDay
import app.supersax.domain.program.Target
import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable
enum class StepKind { PLAY, REST }

/* Planner models: serialized with the running session, so they stay Kotlin data classes. The planner itself is Java. */

/** A concrete, resolved target for one step of a session. */
@Serializable
data class ResolvedTarget @JvmOverloads constructor(
    val bpm: Int? = null,
    val holdSec: Int? = null,
    val scaleKey: String? = null,
    val pieceId: String? = null,
    val sectionId: String? = null,
    val targetMidi: Int? = null,
    val lowMidi: Int? = null,
    val highMidi: Int? = null,
    val pinned: Boolean = false,
)

/** One step of a concrete session (an exercise chunk or a rest). */
@Serializable
data class PlannedStep @JvmOverloads constructor(
    val index: Int,
    val kind: StepKind,
    val durationSec: Int,
    val blockId: String? = null,
    val blockKind: BlockKind? = null,
    val exerciseId: String? = null,
    val exerciseType: ExerciseType? = null,
    val catalogId: String? = null,
    val target: ResolvedTarget = ResolvedTarget(),
    val chunk: Int = 1,
    val chunks: Int = 1,
    /** A rest forced by "lips tired": it cannot be skipped. */
    val enforced: Boolean = false,
)

/** A program day resolved into steps for today. */
@Serializable
data class PlannedSession(
    val programId: String?,
    val dayId: String?,
    val steps: List<PlannedStep>,
) {
    val playingSec: Int get() = steps.filter { it.kind == StepKind.PLAY }.sumOf { it.durationSec }
    val totalSec: Int get() = steps.sumOf { it.durationSec }
}

/** Pieces chosen by the library for today, if any. */
data class PieceChoice @JvmOverloads constructor(val newPieceId: String? = null, val newSectionId: String? = null, val funPieceId: String? = null)
