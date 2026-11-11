package app.supersax.domain.generator

import app.supersax.domain.program.PlayerState
import app.supersax.domain.program.ScaleProgress

/**
 * The owner's own training rules, as pure functions over [PlayerState].
 *
 * - Two clean runs in a row add 5 BPM for that scale.
 * - A new scale unlocks when the last two unlocked scales are solid.
 * - Long tones grow only if the player did not tire early (and drift is under control).
 */
object Adaptation {

    data class RunResult(val state: PlayerState, val bumped: Boolean, val newBpm: Int, val unlocked: String?)

    /**
     * Records one run of [scaleKey] at [bpm]. A clean run extends the streak; a mistake resets it.
     * When the streak reaches [AdaptationConfig.cleanRunsForBump], the tempo rises by
     * [AdaptationConfig.bpmStep] for next time and the streak restarts. Then the unlock rule is applied.
     */
    fun recordRun(state: PlayerState, scaleKey: String, bpm: Int, clean: Boolean, nowMs: Long, config: AdaptationConfig): RunResult {
        val current = state.scale(scaleKey) ?: ScaleProgress(scaleKey, bpm, unlockedAtEpochMs = nowMs)
        var progress = current.copy(lastPracticedEpochMs = nowMs)
        var bumped = false
        if (clean) {
            val streak = progress.consecutiveClean + 1
            if (streak >= config.cleanRunsForBump) {
                val newBpm = (maxOf(progress.bpm, bpm) + config.bpmStep).coerceAtMost(config.maxBpm)
                progress = progress.copy(bpm = newBpm, consecutiveClean = 0, bestCleanBpm = maxOf(progress.bestCleanBpm, bpm))
                bumped = true
            } else {
                progress = progress.copy(consecutiveClean = streak)
            }
        } else {
            progress = progress.copy(consecutiveClean = 0)
        }
        val scales = if (state.scale(scaleKey) == null) state.scales + progress
        else state.scales.map { if (it.scaleKey == scaleKey) progress else it }
        var newState = state.copy(scales = scales)
        val unlocked = nextUnlock(newState, config)
        if (unlocked != null) {
            newState = newState.copy(
                scales = newState.scales + ScaleProgress(unlocked, startBpm(newState, config), unlockedAtEpochMs = nowMs),
            )
        }
        return RunResult(newState, bumped, progress.bpm, unlocked)
    }

    /** A scale is solid once a clean pair has been played at or above [AdaptationConfig.solidMinBpm]. */
    fun isSolid(p: ScaleProgress, config: AdaptationConfig): Boolean = p.bestCleanBpm >= config.solidMinBpm

    /**
     * The next scale in [AdaptationConfig.unlockOrder] if the two most recently unlocked scales are both
     * solid (or the only one is, when there is one), else null.
     */
    fun nextUnlock(state: PlayerState, config: AdaptationConfig): String? {
        val unlocked = state.scales
        val candidate = config.unlockOrder.firstOrNull { key -> unlocked.none { it.scaleKey == key } } ?: return null
        if (unlocked.isEmpty()) return candidate
        val lastTwo = unlocked.sortedWith(compareBy<ScaleProgress> { it.unlockedAtEpochMs }).takeLast(2)
        return if (lastTwo.all { isSolid(it, config) }) candidate else null
    }

    private fun startBpm(state: PlayerState, config: AdaptationConfig): Int =
        (state.scales.minOfOrNull { it.bpm } ?: config.minBpm).coerceAtLeast(config.minBpm)

    /**
     * Long-tone hold target after a session (stamina ramp). It grows by one step only if the long-tone
     * work was completed without tiring early, the tiredness rating was 3 or less, and, when tuner data
     * exists, the average absolute drift stayed under the threshold. Tiring early lowers it by one step.
     */
    fun nextHoldTarget(
        currentSec: Int,
        tiredEarly: Boolean,
        tirednessRating: Int?,
        meanAbsDriftCents: Double?,
        config: AdaptationConfig,
    ): Int = when {
        tiredEarly -> (currentSec - config.holdStepSec).coerceAtLeast(config.holdMinSec)
        (tirednessRating ?: 3) > 3 -> currentSec
        meanAbsDriftCents != null && meanAbsDriftCents > config.maxDriftForGrowthCents -> currentSec
        else -> (currentSec + config.holdStepSec).coerceAtMost(config.holdMaxSec)
    }

    /**
     * Embouchure endurance cap after a session: after N sessions in a row without "lips tired" the
     * cap grows by one step; a tired session resets the counter.
     */
    fun afterSession(state: PlayerState, lipsTired: Boolean, config: AdaptationConfig): PlayerState {
        if (lipsTired) return state.copy(sessionsWithoutFatigue = 0)
        val n = state.sessionsWithoutFatigue + 1
        return if (n >= config.staminaSessionsForStep) {
            state.copy(
                sessionsWithoutFatigue = 0,
                enduranceCapSec = (state.enduranceCapSec + config.staminaStepSec).coerceAtMost(config.staminaMaxSec),
            )
        } else state.copy(sessionsWithoutFatigue = n)
    }

    /** Was a block ended before [AdaptationConfig.tiredEarlyShare] of its planned time? */
    fun tiredEarly(plannedSec: Int, actualSec: Int, config: AdaptationConfig): Boolean =
        plannedSec > 0 && actualSec < plannedSec * config.tiredEarlyShare
}
