package app.supersax.domain.generator

import app.supersax.domain.program.Block
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Exercise
import app.supersax.domain.program.ExerciseType
import app.supersax.domain.program.Focus
import app.supersax.domain.program.PhoneDrillSlot
import app.supersax.domain.program.Pillar
import app.supersax.domain.program.Program
import app.supersax.domain.program.ProgramDay
import app.supersax.domain.program.ProgramOrigin
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Rule-based, deterministic program generator (pure functions).
 *
 * A session is a priority-ordered set of blocks cut to the available minutes. Playing order
 * (the [BlockKind] declaration order) is separate from priority ([GeneratorConfig.dropOrder]).
 */
object Generator {

    data class Input(
        val programId: String,
        val name: String,
        val days: Set<Int>,
        val minutesPerSession: Int,
        val enduranceCapSec: Int,
        val focus: List<Focus> = emptyList(),
        val limiter: Pillar? = null,
        /** Phone drill types that exist in this build; only these are scheduled. */
        val availablePhoneDrills: List<ExerciseType> = emptyList(),
        val restSec: Int? = null,
        val createdAtEpochMs: Long = 0,
    )

    val PLAYING_KINDS = listOf(BlockKind.LONG_TONES, BlockKind.SCALES, BlockKind.NEW_MATERIAL, BlockKind.PLAY_FOR_FUN)

    /** Note keys added to [Program.notes]. */
    const val NOTE_CONSISTENCY_PREFIX = "consistency_shorter:"

    /**
     * The starting split for [minutes], interpolated linearly between table rows (scaled from the
     * nearest row outside the table) and rounded with the largest-remainder method so the total is exact.
     */
    fun baseSplit(minutes: Int, config: GeneratorConfig): Map<BlockKind, Int> {
        require(minutes > 0)
        val rows = config.splits.sortedBy { it.minutes }
        val raw: Map<BlockKind, Double> = when {
            minutes <= rows.first().minutes -> scaleRow(rows.first(), minutes)
            minutes >= rows.last().minutes -> scaleRow(rows.last(), minutes)
            else -> {
                val hi = rows.first { it.minutes >= minutes }
                val lo = rows.last { it.minutes <= minutes }
                if (hi.minutes == lo.minutes) scaleRow(lo, minutes) else {
                    val t = (minutes - lo.minutes).toDouble() / (hi.minutes - lo.minutes)
                    PLAYING_KINDS.associateWith { k -> lo.of(k) + t * (hi.of(k) - lo.of(k)) }
                }
            }
        }
        return apportion(raw, minutes)
    }

    private fun scaleRow(row: SplitRow, minutes: Int): Map<BlockKind, Double> =
        PLAYING_KINDS.associateWith { row.of(it) * minutes.toDouble() / row.minutes }

    /** Largest-remainder rounding: integer parts, then the biggest fractions get the leftover minutes. */
    fun apportion(raw: Map<BlockKind, Double>, total: Int): Map<BlockKind, Int> {
        val sum = raw.values.sum()
        val scaled = if (sum <= 0) raw else raw.mapValues { it.value * total / sum }
        val floors = scaled.mapValues { floor(it.value + 1e-9).toInt() }.toMutableMap()
        var left = total - floors.values.sum()
        val order = scaled.entries.sortedWith(
            compareByDescending<Map.Entry<BlockKind, Double>> { it.value - floor(it.value + 1e-9) }
                .thenBy { it.key.ordinal },
        )
        var i = 0
        while (left > 0 && order.isNotEmpty()) {
            val k = order[i % order.size].key
            floors[k] = floors.getValue(k) + 1
            left--; i++
        }
        return PLAYING_KINDS.associateWith { floors[it] ?: 0 }
    }

    /** Block that receives extra minutes for a focus, or null if the focus is served by phone drills. */
    fun receiverFor(focus: Focus, split: Map<BlockKind, Int>): BlockKind? = when (focus) {
        Focus.TONE -> BlockKind.LONG_TONES
        Focus.TECHNIQUE -> BlockKind.SCALES
        Focus.REPERTOIRE -> if ((split[BlockKind.NEW_MATERIAL] ?: 0) > 0) BlockKind.NEW_MATERIAL else BlockKind.PLAY_FOR_FUN
        Focus.READING, Focus.THEORY -> null
    }

    fun receiverFor(limiter: Pillar, split: Map<BlockKind, Int>): BlockKind? = when (limiter) {
        Pillar.SOUND, Pillar.RANGE -> BlockKind.LONG_TONES
        Pillar.TECHNIQUE -> BlockKind.SCALES
        Pillar.REPERTOIRE -> if ((split[BlockKind.NEW_MATERIAL] ?: 0) > 0) BlockKind.NEW_MATERIAL else BlockKind.PLAY_FOR_FUN
        Pillar.MUSICIANSHIP, Pillar.CONSISTENCY -> null
    }

    /**
     * Moves `round(minutes * share)` (at least one) minutes to [receiver], taken from the other blocks
     * in drop order. Donors keep at least [GeneratorConfig.minBlockMinutes]; protected blocks and
     * blocks in [exclude] (earlier receivers) never donate.
     */
    fun shift(
        split: Map<BlockKind, Int>,
        receiver: BlockKind,
        share: Double,
        config: GeneratorConfig,
        exclude: Set<BlockKind> = emptySet(),
    ): Map<BlockKind, Int> {
        val total = split.values.sum()
        var wanted = (total * share).roundToInt().coerceAtLeast(1)
        val out = split.toMutableMap()
        for (donor in config.dropOrder) {
            if (donor == receiver || donor in exclude || wanted == 0) continue
            val have = out[donor] ?: 0
            if (have <= 0) continue
            val give = minOf(wanted, have - config.minBlockMinutes).coerceAtLeast(0)
            out[donor] = have - give
            out[receiver] = (out[receiver] ?: 0) + give
            wanted -= give
        }
        return out
    }

    /** Applies focus (up to two) and the limiter to the base split. */
    fun adjustedSplit(minutes: Int, focus: List<Focus>, limiter: Pillar?, config: GeneratorConfig): Map<BlockKind, Int> {
        var split = baseSplit(minutes, config)
        val received = mutableSetOf<BlockKind>()
        focus.take(2).forEach { f ->
            receiverFor(f, split)?.let { split = shift(split, it, config.focusShare, config, received); received += it }
        }
        limiter?.let { l ->
            receiverFor(l, split)?.let { split = shift(split, it, config.limiterShare, config, received); received += it }
        }
        return split
    }

    /**
     * Cuts a day to [available] minutes. If over budget, the first kind in the drop order is removed
     * entirely; the rest is then shrunk proportionally (each block keeps at least one minute); if it
     * still does not fit, the next droppable kind goes. Protected kinds are never removed.
     */
    fun fitToMinutes(blocks: List<Block>, available: Int, config: GeneratorConfig): List<Block> {
        var current = blocks.filter { it.minutes > 0 }
        if (current.sumOf { it.minutes } <= available) return current
        val droppable = config.dropOrder.toMutableList()
        // Step 1: drop the lowest-priority kind.
        if (droppable.isNotEmpty()) {
            val first = droppable.removeAt(0)
            current = current.filter { it.kind != first }
        }
        while (true) {
            val total = current.sumOf { it.minutes }
            if (total <= available) return current
            if (current.size * config.minBlockMinutes <= available) {
                return shrink(current, available, config.minBlockMinutes)
            }
            if (droppable.isEmpty()) return shrink(current, available.coerceAtLeast(current.size), config.minBlockMinutes)
            val next = droppable.removeAt(0)
            current = current.filter { it.kind != next }
        }
    }

    private fun shrink(blocks: List<Block>, target: Int, min: Int): List<Block> {
        val total = blocks.sumOf { it.minutes }.toDouble()
        val raw = blocks.map { it.minutes * target / total }
        val floors = raw.map { floor(it + 1e-9).toInt().coerceAtLeast(min) }.toMutableList()
        var left = target - floors.sum()
        val order = raw.indices.sortedByDescending { raw[it] - floor(raw[it] + 1e-9) }
        var i = 0
        while (left > 0) { floors[order[i % order.size]]++; left--; i++ }
        while (left < 0) {
            val idx = floors.indices.filter { floors[it] > min }.maxByOrNull { floors[it] } ?: break
            floors[idx]--; left++
        }
        return blocks.mapIndexed { i2, b -> b.copy(minutes = floors[i2]) }
    }

    /** Builds the blocks (with default exercises) for one day of [minutes]. */
    fun blocksFor(dayId: String, minutes: Int, focus: List<Focus>, limiter: Pillar?, config: GeneratorConfig): List<Block> {
        val split = adjustedSplit(minutes, focus, limiter, config)
        return PLAYING_KINDS.filter { (split[it] ?: 0) > 0 }.mapIndexed { i, kind ->
            val bid = "$dayId-b$i"
            val m = split.getValue(kind)
            val exercises = when (kind) {
                BlockKind.LONG_TONES -> buildList {
                    add(Exercise("$bid-e0", "long-tones-basic", ExerciseType.LONG_TONE))
                    if (limiter == Pillar.RANGE) add(Exercise("$bid-e1", "range-extension", ExerciseType.RANGE_EXTENSION))
                }
                BlockKind.SCALES -> buildList {
                    add(Exercise("$bid-e0", "scale-runs", ExerciseType.SCALE))
                    if (m >= 6) add(Exercise("$bid-e1", "scale-review", ExerciseType.SCALE))
                }
                BlockKind.NEW_MATERIAL -> listOf(Exercise("$bid-e0", "new-material", ExerciseType.NEW_MATERIAL))
                BlockKind.PLAY_FOR_FUN -> listOf(Exercise("$bid-e0", "play-for-fun", ExerciseType.PLAY_FOR_FUN))
                BlockKind.BREATHING -> listOf(Exercise("$bid-e0", "breathing-pacer", ExerciseType.BREATHING_PACER))
            }
            Block(bid, kind, m, exercises)
        }
    }

    /**
     * Schedules phone-drill sessions: the configured number per week (+1 if the limiter is
     * Musicianship, +1 if reading or theory is a focus), capped so the total never exceeds
     * [PhoneDrillConfig.maxShareOfSaxMinutes] of the weekly sax minutes. Only [available] drill types
     * are used, focus-matching ones first, rotating.
     */
    fun phoneDrills(
        weeklySaxMinutes: Int,
        focus: List<Focus>,
        limiter: Pillar?,
        available: List<ExerciseType>,
        config: GeneratorConfig,
    ): List<PhoneDrillSlot> {
        if (available.isEmpty()) return emptyList()
        val pc = config.phoneDrills
        var wanted = pc.sessionsPerWeek
        if (limiter == Pillar.MUSICIANSHIP) wanted++
        if (focus.any { it == Focus.READING || it == Focus.THEORY }) wanted++
        val cap = floor(weeklySaxMinutes * pc.maxShareOfSaxMinutes + 1e-9).toInt()
        val lengths = mutableListOf<Int>()
        var budget = cap
        while (lengths.size < wanted && budget >= MIN_PHONE_MINUTES) {
            val m = minOf(pc.minutes, budget)
            lengths += m
            budget -= m
        }
        val ordered = buildList {
            if (Focus.READING in focus && ExerciseType.READING in available) add(ExerciseType.READING)
            if (Focus.THEORY in focus && ExerciseType.THEORY_LESSON in available) add(ExerciseType.THEORY_LESSON)
            addAll(available.filter { it !in this })
        }
        return lengths.mapIndexed { i, m ->
            val day = 1 + (i * 7 / lengths.size + 1) % 7
            PhoneDrillSlot(day, m, ordered[i % ordered.size])
        }
    }

    private const val MIN_PHONE_MINUTES = 3

    /** Generates a whole program. Same input, same output. */
    fun generate(input: Input, config: GeneratorConfig): Program {
        val sortedDays = input.days.sorted()
        val days = sortedDays.map { dow ->
            val dayId = "${input.programId}-d$dow"
            ProgramDay(dayId, dow, blocksFor(dayId, input.minutesPerSession, input.focus, input.limiter, config))
        }
        val weekly = days.sumOf { it.minutes }
        val notes = buildList {
            if (input.limiter == Pillar.CONSISTENCY) {
                val suggested = (input.minutesPerSession * config.consistencySuggestedShare / 5.0).roundToInt() * 5
                add(NOTE_CONSISTENCY_PREFIX + suggested.coerceAtLeast(5))
            }
        }
        return Program(
            id = input.programId,
            name = input.name,
            origin = ProgramOrigin.GENERATED,
            days = days,
            phoneDrills = phoneDrills(weekly, input.focus, input.limiter, input.availablePhoneDrills, config),
            enduranceCapSec = input.enduranceCapSec,
            restSec = input.restSec ?: config.restSec,
            notes = notes,
            createdAtEpochMs = input.createdAtEpochMs,
        )
    }
}
