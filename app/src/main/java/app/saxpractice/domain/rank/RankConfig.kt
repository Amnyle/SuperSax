package app.saxpractice.domain.rank

import app.saxpractice.domain.scale.ScaleType
import kotlinx.serialization.Serializable

/** Scale families counted by the Technique pillar. */
@Serializable
enum class ScaleFamily { MAJOR, MINOR, MODE, ARPEGGIO;
    companion object {
        fun of(t: ScaleType): ScaleFamily = when (t.family) {
            ScaleType.Family.MAJOR -> MAJOR
            ScaleType.Family.MINOR -> MINOR
            ScaleType.Family.MODE -> MODE
            ScaleType.Family.ARPEGGIO -> ARPEGGIO
        }
    }
}

/** "[count] scales of [family] with a clean pair at [minBpm] or more." */
@Serializable
data class TechniqueReq(val family: ScaleFamily, val count: Int, val minBpm: Int)

@Serializable
data class TechniqueTier(val tier: Int, val requirements: List<TechniqueReq>)

/**
 * Range tier: the lowest claimed written note must be at or below [lowAtMost] and the highest at or
 * above [highAtLeast] (written MIDI). Above [verifiedFrom], only tuner-verified notes count and the
 * overtone milestone is required.
 */
@Serializable
data class RangeTier(val tier: Int, val lowAtMost: Int, val highAtLeast: Int)

/** Sound tier: holds of at least [seconds], at least [inTuneShare] of the time within ±[toleranceCents]. */
@Serializable
data class SoundTier(
    val tier: Int,
    val seconds: Double,
    val toleranceCents: Double,
    val distinctNotes: Int = 3,
    val inTuneShare: Double = 0.8,
    /** Holds must cover the low, middle and high registers. */
    val allRegisters: Boolean = false,
    /** Iron: completed long-tone blocks (self-reported) instead of tuner holds. */
    val longToneBlocks: Int = 0,
)

@Serializable
data class ConsistencyConfig(
    val windowDays: Int = 56,
    /** Adherence thresholds in percent for tiers 1..8. */
    val thresholds: List<Int>,
    /** From this tier up, the average sessions per week must also reach [minSessionsPerWeek]. */
    val sessionsFloorFromTier: Int = 4,
    val minSessionsPerWeek: Double = 3.0,
)

/** Repertoire tier: the hardest piece "clean at tempo" has at least [minDifficulty]. */
@Serializable
data class RepertoireTier(val tier: Int, val minDifficulty: Int)

/**
 * Musicianship tier: a reading round at [readingLevel] or above with at least [readingAccuracy]
 * correct and a mean answer time at most [readingMeanMs]; a scale-recognition round at
 * [scaleLevel] or above with at least [scaleAccuracy]; [lessons] theory lessons completed with a quiz
 * score of at least [lessonMinScore]. Zero values mean "not required".
 */
@Serializable
data class MusicianshipTier(
    val tier: Int,
    val readingLevel: Int = 0,
    val readingAccuracy: Double = 0.0,
    val readingMeanMs: Long = Long.MAX_VALUE,
    val scaleLevel: Int = 0,
    val scaleAccuracy: Double = 0.0,
    val lessons: Int = 0,
    val lessonMinScore: Double = 0.0,
    /** Iron: any one finished drill round or lesson. */
    val anyActivity: Boolean = false,
)

/** All rank thresholds, from `assets/rank/rank_config.json`. Never hard-coded. */
@Serializable
data class RankConfig(
    val technique: List<TechniqueTier>,
    val range: List<RangeTier>,
    /** Range tiers from this one up need tuner-verified notes and the overtone milestone. */
    val rangeVerifiedFromTier: Int = 5,
    val sound: List<SoundTier>,
    val consistency: ConsistencyConfig,
    val repertoire: List<RepertoireTier>,
    val musicianship: List<MusicianshipTier>,
)
