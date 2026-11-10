package app.saxpractice.domain.program

import kotlinx.serialization.Serializable

@Serializable
enum class Focus { TONE, TECHNIQUE, REPERTOIRE, READING, THEORY }

/** The six rank pillars. Declaration order is the tie-break order for the limiter (spec §9). */
@Serializable
enum class Pillar { SOUND, TECHNIQUE, RANGE, REPERTOIRE, CONSISTENCY, MUSICIANSHIP }

/** How long before the lips tire (onboarding question 2). */
@Serializable
enum class Endurance { UNDER_3, FROM_3_TO_5, FROM_5_TO_10, FROM_10_TO_20, OVER_20 }

/** Five-step self-assessed level (reading or theory). 0 = lowest. */
@Serializable
enum class Registers { LOW, MIDDLE, HIGH }

/**
 * Progress on one scale: current tempo, clean-run streak and whether it is solid.
 * Tempo is in eighth notes per minute, as in the rank table.
 */
@Serializable
data class ScaleProgress(
    val scaleKey: String,
    val bpm: Int,
    val consecutiveClean: Int = 0,
    val bestCleanBpm: Int = 0,
    val unlockedAtEpochMs: Long = 0,
    val lastPracticedEpochMs: Long = 0,
)

/**
 * Everything the generator and the session planner need to know about the player.
 * Seeded by onboarding, then updated by the adaptation rules after each session.
 */
@Serializable
data class PlayerState(
    val scales: List<ScaleProgress> = emptyList(),
    val holdTargetSec: Int = 6,
    val enduranceCapSec: Int = 300,
    val sessionsWithoutFatigue: Int = 0,
    val registers: Set<Registers> = setOf(Registers.MIDDLE),
    val readingLevel: Int = 1,
    val theoryLevel: Int = 1,
    val focus: List<Focus> = emptyList(),
    /** Written MIDI range the player can play (used by library matching). */
    val writtenLowMidi: Int = 62,
    val writtenHighMidi: Int = 84,
) {
    fun scale(key: String): ScaleProgress? = scales.firstOrNull { it.scaleKey == key }
    val unlockedKeys: List<String> get() = scales.map { it.scaleKey }
}

/** Answers collected by onboarding (one question per page). */
@Serializable
data class OnboardingAnswers(
    val days: Set<Int> = setOf(1, 3, 5),
    val minutesPerSession: Int = 20,
    val endurance: Endurance = Endurance.FROM_5_TO_10,
    val longestHoldSec: Int = 8,
    val registers: Set<Registers> = setOf(Registers.MIDDLE),
    /** Concert scale keys the player plays cleanly. */
    val cleanScales: Set<String> = emptySet(),
    val comfortableBpm: Int = 60,
    val readingLevel: Int = 1,
    val theoryLevel: Int = 1,
    val songsNow: List<String> = emptyList(),
    val songsNext: List<String> = emptyList(),
    val focus: List<Focus> = emptyList(),
)
