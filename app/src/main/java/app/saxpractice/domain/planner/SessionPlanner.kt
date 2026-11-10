package app.saxpractice.domain.planner

import app.saxpractice.domain.generator.Generator
import app.saxpractice.domain.generator.GeneratorConfig
import app.saxpractice.domain.program.Block
import app.saxpractice.domain.program.BlockKind
import app.saxpractice.domain.program.Exercise
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.domain.program.PlayerState
import app.saxpractice.domain.program.ProgramDay
import app.saxpractice.domain.program.Target
import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable
enum class StepKind { PLAY, REST }

/** A concrete, resolved target for one step of a session. */
@Serializable
data class ResolvedTarget(
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
data class PlannedStep(
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
data class PieceChoice(val newPieceId: String? = null, val newSectionId: String? = null, val funPieceId: String? = null)

/**
 * Resolves a program template into a concrete session using the player's current state:
 * scale rotation (least recently practiced unlocked scales first), tempo per scale, long-tone hold
 * target, library pieces, the embouchure endurance cap (chunks + rests) and rests between blocks.
 */
object SessionPlanner {

    fun plan(
        programId: String?,
        day: ProgramDay,
        state: PlayerState,
        config: GeneratorConfig,
        enduranceCapSec: Int = state.enduranceCapSec,
        restSec: Int = config.restSec,
        pieces: PieceChoice = PieceChoice(),
        availableMinutes: Int? = null,
    ): PlannedSession {
        val blocks = availableMinutes?.let { Generator.fitToMinutes(day.blocks, it, config) } ?: day.blocks
        val scaleQueue = scaleRotation(state, config).toMutableList()
        val raw = mutableListOf<List<PlannedStep>>()
        for (block in blocks.sortedBy { it.kind.ordinal }.filter { it.minutes > 0 }) {
            raw += blockSteps(block, state, scaleQueue, pieces, config, enduranceCapSec)
        }
        val steps = mutableListOf<PlannedStep>()
        raw.forEachIndexed { bi, blockSteps ->
            val prevPlays = steps.lastOrNull()?.let { it.kind == StepKind.PLAY && it.exerciseType?.isPlaying == true } == true
            val firstPlays = blockSteps.firstOrNull()?.exerciseType?.isPlaying == true
            if (bi > 0 && prevPlays && firstPlays && restSec > 0) steps += rest(restSec)
            steps += blockSteps
        }
        return PlannedSession(programId, day.id, steps.mapIndexed { i, s -> s.copy(index = i) })
    }

    private fun rest(sec: Int) = PlannedStep(0, StepKind.REST, sec)

    /** Unlocked scales, least recently practiced first, then by key for determinism. */
    fun scaleRotation(state: PlayerState, config: GeneratorConfig): List<String> {
        val unlocked = state.scales.sortedWith(compareBy({ it.lastPracticedEpochMs }, { it.scaleKey })).map { it.scaleKey }
        return unlocked.ifEmpty { config.adaptation.unlockOrder.take(1) }
    }

    private fun blockSteps(
        block: Block,
        state: PlayerState,
        scaleQueue: MutableList<String>,
        pieces: PieceChoice,
        config: GeneratorConfig,
        capSec: Int,
    ): List<PlannedStep> {
        val ex = block.exercises.ifEmpty { return emptyList() }
        val totalSec = block.minutes * 60
        val per = totalSec / ex.size
        val extra = totalSec - per * ex.size
        val steps = mutableListOf<PlannedStep>()
        var continuous = 0
        ex.forEachIndexed { i, e ->
            val dur = per + if (i == 0) extra else 0
            val target = resolve(e, state, scaleQueue, pieces, config)
            val playing = e.type.isPlaying
            val chunks = if (playing && dur > capSec) ceil(dur.toDouble() / capSec).toInt() else 1
            val chunkLen = dur / chunks
            for (c in 1..chunks) {
                val d = if (c == chunks) dur - chunkLen * (chunks - 1) else chunkLen
                if (playing && continuous > 0 && continuous + d > capSec) {
                    steps += rest(config.restSec)
                    continuous = 0
                }
                steps += PlannedStep(
                    index = 0, kind = StepKind.PLAY, durationSec = d, blockId = block.id, blockKind = block.kind,
                    exerciseId = e.id, exerciseType = e.type, catalogId = e.catalogId, target = target,
                    chunk = c, chunks = chunks,
                )
                continuous = if (playing) continuous + d else 0
            }
        }
        return steps
    }

    /** Fills unset target fields from the player state. Pinned values are kept as they are. */
    fun resolve(e: Exercise, state: PlayerState, scaleQueue: MutableList<String>, pieces: PieceChoice, config: GeneratorConfig): ResolvedTarget {
        val t: Target = e.target
        return when (e.type) {
            ExerciseType.LONG_TONE, ExerciseType.RANGE_EXTENSION -> ResolvedTarget(
                holdSec = if (t.pinned && t.holdSec != null) t.holdSec else state.holdTargetSec,
                targetMidi = t.targetMidi, lowMidi = t.lowMidi, highMidi = t.highMidi, pinned = t.pinned,
            )
            ExerciseType.SCALE -> {
                val key = t.scaleKey ?: scaleQueue.removeFirstOrNull()?.also { scaleQueue.add(it) }
                val stateBpm = key?.let { state.scale(it)?.bpm } ?: config.adaptation.minBpm
                ResolvedTarget(
                    bpm = if (t.pinned && t.bpm != null) t.bpm else stateBpm,
                    scaleKey = key, lowMidi = t.lowMidi, highMidi = t.highMidi, pinned = t.pinned,
                )
            }
            ExerciseType.NEW_MATERIAL -> ResolvedTarget(
                pieceId = t.pieceId ?: pieces.newPieceId, sectionId = t.sectionId ?: pieces.newSectionId,
                bpm = t.bpm, pinned = t.pinned,
            )
            ExerciseType.PLAY_FOR_FUN -> ResolvedTarget(pieceId = t.pieceId ?: pieces.funPieceId, pinned = t.pinned)
            else -> ResolvedTarget(bpm = t.bpm, pinned = t.pinned)
        }
    }
}
