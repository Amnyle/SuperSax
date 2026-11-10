package app.saxpractice.domain.session

import app.saxpractice.domain.planner.PlannedSession
import app.saxpractice.domain.planner.PlannedStep
import app.saxpractice.domain.planner.StepKind
import kotlinx.serialization.Serializable

/** One scale run reported during a session ("clean run" or "mistake"). */
@Serializable
data class RunEvent(val stepIndex: Int, val scaleKey: String, val bpm: Int, val clean: Boolean)

/** What actually happened in one step. */
@Serializable
data class StepLog(
    val stepIndex: Int,
    val plannedSec: Int,
    val actualSec: Int,
    val lipsTired: Boolean = false,
    val skipped: Boolean = false,
)

/**
 * Pure state machine of a running session. All times are monotonic milliseconds
 * (`SystemClock.elapsedRealtime()` on Android) passed in by the caller, never tick counts, so the
 * state can be persisted and the timer survives the app being backgrounded or killed.
 */
@Serializable
data class SessionRun(
    val session: PlannedSession,
    val current: Int = 0,
    val stepStartMs: Long = 0,
    val pausedAtMs: Long? = null,
    val pausedInStepMs: Long = 0,
    val extraSec: Int = 0,
    val logs: List<StepLog> = emptyList(),
    val runs: List<RunEvent> = emptyList(),
    val startedWallMs: Long = 0,
    val finished: Boolean = false,
) {
    val steps: List<PlannedStep> get() = session.steps
    val step: PlannedStep? get() = steps.getOrNull(current)
    val isPaused: Boolean get() = pausedAtMs != null

    /** Milliseconds spent in the current step, excluding pauses. */
    fun elapsedMs(nowMs: Long): Long {
        val end = pausedAtMs ?: nowMs
        return (end - stepStartMs - pausedInStepMs).coerceAtLeast(0)
    }

    fun stepLengthMs(): Long = ((step?.durationSec ?: 0) + extraSec) * 1000L

    fun remainingMs(nowMs: Long): Long = (stepLengthMs() - elapsedMs(nowMs)).coerceAtLeast(0)

    fun pause(nowMs: Long): SessionRun = if (isPaused || finished) this else copy(pausedAtMs = nowMs)

    fun resume(nowMs: Long): SessionRun {
        val p = pausedAtMs ?: return this
        return copy(pausedAtMs = null, pausedInStepMs = pausedInStepMs + (nowMs - p))
    }

    /** +1 minute on the current step. */
    fun addMinute(): SessionRun = copy(extraSec = extraSec + 60)

    /** Can the current step be skipped with Next? Enforced rests cannot. */
    fun canSkip(nowMs: Long): Boolean = step?.enforced != true || remainingMs(nowMs) == 0L

    /**
     * Ends the current step at [nowMs] and moves on. With [lipsTired], the step is marked tired and a
     * rest of [restSec] is enforced before the next playing step (the following rest, if any, becomes
     * enforced and at least that long).
     */
    fun next(nowMs: Long, lipsTired: Boolean = false, restSec: Int = 60): SessionRun {
        if (finished) return this
        val s = step ?: return copy(finished = true)
        if (!lipsTired && !canSkip(nowMs)) return this
        val actual = (elapsedMs(nowMs) / 1000).toInt().coerceAtMost(s.durationSec + extraSec)
        val log = StepLog(s.index, s.durationSec + extraSec, actual, lipsTired = lipsTired,
            skipped = actual < s.durationSec + extraSec && remainingMs(nowMs) > 0)
        var newSteps = steps
        if (lipsTired && s.kind == StepKind.PLAY) {
            val following = steps.getOrNull(current + 1)
            newSteps = if (following?.kind == StepKind.REST) {
                steps.mapIndexed { i, st ->
                    if (i == current + 1) st.copy(enforced = true, durationSec = maxOf(st.durationSec, restSec)) else st
                }
            } else {
                val forced = PlannedStep(0, StepKind.REST, restSec, enforced = true)
                (steps.take(current + 1) + forced + steps.drop(current + 1)).mapIndexed { i, st -> st.copy(index = i) }
            }
        }
        val nextIndex = current + 1
        val endMs = pausedAtMs ?: nowMs
        return copy(
            session = session.copy(steps = newSteps),
            current = nextIndex,
            stepStartMs = endMs,
            pausedAtMs = if (isPaused) endMs else null,
            pausedInStepMs = 0,
            extraSec = 0,
            logs = logs + log,
            finished = nextIndex >= newSteps.size,
        )
    }

    /**
     * Advances through every step whose time ran out by [nowMs] (each logged at its full length),
     * so a session left in the background catches up correctly.
     */
    fun catchUp(nowMs: Long): SessionRun {
        var r = this
        var guard = 0
        while (!r.finished && !r.isPaused && r.remainingMs(nowMs) == 0L && guard++ < 1000) {
            val endOfStep = r.stepStartMs + r.pausedInStepMs + r.stepLengthMs()
            r = r.next(endOfStep)
        }
        return r
    }

    /** Ends the session now; remaining steps are logged as skipped with zero time. */
    fun finish(nowMs: Long): SessionRun {
        var r = this
        if (!r.finished) {
            val s = r.step
            if (s != null) {
                val actual = (r.elapsedMs(nowMs) / 1000).toInt()
                r = r.copy(logs = r.logs + StepLog(s.index, s.durationSec + r.extraSec, actual, skipped = true))
            }
            val rest = r.steps.drop(r.current + 1).map { StepLog(it.index, it.durationSec, 0, skipped = true) }
            r = r.copy(logs = r.logs + rest, finished = true, current = r.steps.size)
        }
        return r
    }

    fun recordRun(scaleKey: String, bpm: Int, clean: Boolean): SessionRun =
        copy(runs = runs + RunEvent(step?.index ?: -1, scaleKey, bpm, clean))

    /** Consecutive clean runs at the end of this session's runs for [scaleKey]. */
    fun cleanStreak(scaleKey: String): Int =
        runs.filter { it.scaleKey == scaleKey }.reversed().takeWhile { it.clean }.size

    fun totalPlayedSec(): Int = logs.filter { steps.getOrNull(it.stepIndex)?.kind == StepKind.PLAY }.sumOf { it.actualSec }

    companion object {
        fun start(session: PlannedSession, nowMs: Long, wallMs: Long) =
            SessionRun(session, stepStartMs = nowMs, startedWallMs = wallMs, finished = session.steps.isEmpty())
    }
}
