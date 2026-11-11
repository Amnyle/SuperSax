package app.supersax.domain.program

import kotlinx.serialization.Serializable

/** What a block is for. Order of this enum is the playing order. */
@Serializable
enum class BlockKind { BREATHING, LONG_TONES, SCALES, NEW_MATERIAL, PLAY_FOR_FUN }

/** Exercise types. Phone drills never appear in a sax session; they run as separate short sessions. */
@Serializable
enum class ExerciseType(val isPhoneDrill: Boolean = false, val isPlaying: Boolean = true) {
    LONG_TONE,
    RANGE_EXTENSION,
    SCALE,
    NEW_MATERIAL,
    PLAY_FOR_FUN,
    BREATHING_PACER(isPlaying = false),
    READING(isPhoneDrill = true, isPlaying = false),
    SCALE_RECOGNITION(isPhoneDrill = true, isPlaying = false),
    THEORY_LESSON(isPhoneDrill = true, isPlaying = false),
    FINGERING(isPhoneDrill = true, isPlaying = false),
}

/**
 * Targets of an exercise. All fields optional: unset fields are resolved by the session planner from
 * the player's state. When [pinned] is true the explicit values override automatic progression.
 *
 * Note fields are concert MIDI numbers (the storage standard).
 */
@Serializable
data class Target @JvmOverloads constructor(
    val bpm: Int? = null,
    val holdSec: Int? = null,
    val scaleKey: String? = null,
    val lowMidi: Int? = null,
    val highMidi: Int? = null,
    val pieceId: String? = null,
    val sectionId: String? = null,
    val cleanRuns: Int? = null,
    val targetMidi: Int? = null,
    val pinned: Boolean = false,
)

/** One exercise in a block. [catalogId] points to a description in `assets/exercises/exercises.json`. */
@Serializable
data class Exercise @JvmOverloads constructor(
    val id: String,
    val catalogId: String,
    val type: ExerciseType,
    val target: Target = Target(),
)

/** A block of the session. [minutes] is its planned playing time. */
@Serializable
data class Block(
    val id: String,
    val kind: BlockKind,
    val minutes: Int,
    val exercises: List<Exercise>,
)

/** A practice day of the week. [dayOfWeek] is ISO (1 = Monday .. 7 = Sunday). */
@Serializable
data class ProgramDay(
    val id: String,
    val dayOfWeek: Int,
    val blocks: List<Block>,
) {
    val minutes: Int get() = blocks.sumOf { it.minutes }
}

/** A short phone-only session (no sax). */
@Serializable
data class PhoneDrillSlot(val dayOfWeek: Int, val minutes: Int, val type: ExerciseType)

@Serializable
enum class ProgramOrigin { GENERATED, MANUAL, BUNDLED }

/**
 * A practice program template: program > days > blocks > exercises > targets.
 * Generated and manual programs are the same object, edited in the same editor.
 */
@Serializable
data class Program @JvmOverloads constructor(
    val id: String,
    val name: String,
    val origin: ProgramOrigin,
    val days: List<ProgramDay>,
    val phoneDrills: List<PhoneDrillSlot> = emptyList(),
    /** Longest continuous playing time in seconds before a rest is forced. */
    val enduranceCapSec: Int = 300,
    val restSec: Int = 60,
    /** Free-text explanation produced by the generator (e.g. a Consistency suggestion key). */
    val notes: List<String> = emptyList(),
    val createdAtEpochMs: Long = 0,
) {
    val weeklySaxMinutes: Int get() = days.sumOf { it.minutes }
    val weeklyPhoneMinutes: Int get() = phoneDrills.sumOf { it.minutes }
    fun dayFor(dayOfWeek: Int): ProgramDay? = days.firstOrNull { it.dayOfWeek == dayOfWeek }
}
