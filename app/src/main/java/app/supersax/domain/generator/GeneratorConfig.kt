package app.supersax.domain.generator

import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Endurance
import kotlinx.serialization.Serializable

/** One row of the starting time split table (minutes per block kind for a session length). */
@Serializable
data class SplitRow(
    val minutes: Int,
    val longTones: Int,
    val scales: Int,
    val newMaterial: Int,
    val playForFun: Int,
) {
    fun of(kind: BlockKind): Int = when (kind) {
        BlockKind.LONG_TONES -> longTones
        BlockKind.SCALES -> scales
        BlockKind.NEW_MATERIAL -> newMaterial
        BlockKind.PLAY_FOR_FUN -> playForFun
        BlockKind.BREATHING -> 0
    }
}

@Serializable
data class PhoneDrillConfig(
    val sessionsPerWeek: Int = 3,
    val minutes: Int = 5,
    /** Phone drill minutes never exceed this share of the weekly planned sax minutes. */
    val maxShareOfSaxMinutes: Double = 0.20,
)

@Serializable
data class AdaptationConfig(
    val bpmStep: Int = 5,
    val cleanRunsForBump: Int = 2,
    val minBpm: Int = 40,
    val maxBpm: Int = 240,
    /** A scale counts as solid once a clean pair was played at or above this tempo. */
    val solidMinBpm: Int = 60,
    val holdStepSec: Int = 1,
    val holdMaxSec: Int = 40,
    val holdMinSec: Int = 3,
    /** Long tones do not grow if the average absolute drift of recent holds exceeds this. */
    val maxDriftForGrowthCents: Double = 20.0,
    /** A long-tone block ended before this share of its time counts as tiring early. */
    val tiredEarlyShare: Double = 0.75,
    val staminaStepSec: Int = 30,
    val staminaSessionsForStep: Int = 3,
    val staminaMaxSec: Int = 1200,
    /** Written-pitch unlock order of new scales, as concert keys ("MAJOR:Eb" is written C major). */
    val unlockOrder: List<String> = emptyList(),
)

/**
 * Generator configuration, loaded from `assets/config/generator.json`. All numbers are placeholders
 * meant to be tuned without code changes.
 */
@Serializable
data class GeneratorConfig(
    val splits: List<SplitRow>,
    /** Kinds dropped first when time is short. Kinds not listed are protected. */
    val dropOrder: List<BlockKind> = listOf(BlockKind.NEW_MATERIAL, BlockKind.SCALES, BlockKind.LONG_TONES),
    val restSec: Int = 60,
    val enduranceCapSec: Map<Endurance, Int> = mapOf(
        Endurance.UNDER_3 to 120, Endurance.FROM_3_TO_5 to 180, Endurance.FROM_5_TO_10 to 300,
        Endurance.FROM_10_TO_20 to 600, Endurance.OVER_20 to 1200,
    ),
    /** Share of the session moved to the block matching each chosen focus. */
    val focusShare: Double = 0.10,
    /** Share of the session moved to the block matching the rank limiter. */
    val limiterShare: Double = 0.10,
    /** Consistency limiter: suggested session length as a share of the current one. */
    val consistencySuggestedShare: Double = 0.67,
    val minBlockMinutes: Int = 1,
    val phoneDrills: PhoneDrillConfig = PhoneDrillConfig(),
    val adaptation: AdaptationConfig = AdaptationConfig(),
)
