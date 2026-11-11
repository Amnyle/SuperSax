package app.supersax.domain.goals

import app.supersax.domain.common.GoalRecord
import app.supersax.domain.common.GoalType
import app.supersax.domain.common.PieceProgressRecord
import app.supersax.domain.common.PieceStage
import app.supersax.domain.common.RecordingRecord
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.program.PlayerState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Computed progress of a goal. [fraction] is 0..1; [current] and [target] are in the goal's unit. */
data class GoalProgress(val fraction: Double, val done: Boolean, val current: Int, val target: Int)

/** Everything goal progress is computed from. */
data class GoalInputs(
    val sessions: List<SessionRecord>,
    val player: PlayerState,
    val pieceProgress: List<PieceProgressRecord>,
    val recordings: List<RecordingRecord>,
)

/** Pure goal progress, computed from logs. */
object GoalEvaluator {

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun progress(goal: GoalRecord, inputs: GoalInputs, nowMs: Long, zone: ZoneId): GoalProgress = when (goal.type) {
        GoalType.TIME -> {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val start = weekStart(today).atStartOfDay(zone).toInstant().toEpochMilli()
            val minutes = inputs.sessions.filter { !it.phone && it.startedAtMs >= start && it.startedAtMs <= nowMs }
                .sumOf { it.playedSec } / 60
            val target = goal.weeklyMinutes ?: 0
            ratio(minutes, target)
        }
        GoalType.TEMPO -> {
            val key = goal.scaleKey
            val best = maxOf(
                inputs.player.scale(key ?: "")?.bestCleanBpm ?: 0,
                inputs.sessions.flatMap { s -> cleanPairs(s).filter { it.first == key }.map { it.second } }.maxOrNull() ?: 0,
            )
            ratio(best, goal.bpm ?: 0)
        }
        GoalType.SONG_MILESTONE -> {
            val target = goal.stage?.let { PieceStage.valueOf(it) } ?: PieceStage.CLEAN_AT_TEMPO
            val stage = inputs.pieceProgress
                .filter { it.pieceId == goal.pieceId && it.sectionId == (goal.sectionId ?: "") }
                .maxOfOrNull { it.stage.ordinal } ?: 0
            ratio(stage, target.ordinal)
        }
        GoalType.RECORDING -> {
            val deadline = goal.deadlineEpochDay?.let { LocalDate.ofEpochDay(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
            val n = inputs.recordings.count {
                it.benchmark && (it.exerciseKey == goal.benchmarkKey || it.pieceId == goal.benchmarkKey) &&
                    it.atMs >= goal.createdAtMs && (deadline == null || it.atMs < deadline)
            }
            ratio(n.coerceAtMost(1), 1)
        }
    }

    private fun ratio(current: Int, target: Int): GoalProgress {
        if (target <= 0) return GoalProgress(0.0, false, current, target)
        val f = (current.toDouble() / target).coerceIn(0.0, 1.0)
        return GoalProgress(f, current >= target, current, target)
    }

    /** (scaleKey, bpm) for each pair of consecutive clean runs of the same scale in a session. */
    fun cleanPairs(s: SessionRecord): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        s.runs.groupBy { it.scaleKey }.forEach { (key, runs) ->
            var streak = 0
            runs.forEach { r ->
                streak = if (r.clean) streak + 1 else 0
                if (streak >= 2) { out += key to r.bpm; streak = 0 }
            }
        }
        return out
    }
}
