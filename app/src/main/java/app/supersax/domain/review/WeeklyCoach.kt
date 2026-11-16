package app.supersax.domain.review

import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Pillar
import app.supersax.domain.program.PlayerState
import app.supersax.domain.program.Program

/** Something that got better this week. */
sealed interface Improvement {
    data class ScaleFaster(val scaleKey: String, val before: Int, val now: Int) : Improvement
    data class FirstClean(val scaleKey: String, val bpm: Int) : Improvement
    data class InTune(val before: Int, val now: Int) : Improvement
    data class RankUp(val tierBefore: Int, val divisionBefore: Int, val tier: Int, val division: Int) : Improvement
    data class MoreMinutes(val before: Int, val now: Int) : Improvement
    data class AllSessions(val done: Int) : Improvement
}

/** Something to aim at next week. */
sealed interface NextStep {
    data class Weakest(val pillar: Pillar) : NextStep
    data class PushScale(val scaleKey: String, val bpm: Int, val target: Int) : NextStep
    data class Unlock(val scaleKey: String) : NextStep
    data class LongTones(val writtenMidis: List<Int>) : NextStep
    data class MissedDays(val count: Int) : NextStep
    data class StillScale(val scaleKey: String) : NextStep
}

/** Weekly minutes per block before and after a program update. */
data class ProgramChange(val kind: BlockKind, val before: Int, val after: Int)

/** Turns a week's facts into "what improved" and "what's next", and compares a program with its update. */
object WeeklyCoach {
    fun improvements(week: WeeklyReview, previous: WeeklyReview?): List<Improvement> = buildList {
        val after = week.rankAfter
        val before = week.rankBefore
        if (after != null && before != null &&
            (after.overallTier > before.overallTier || (after.overallTier == before.overallTier && after.division > before.division))) {
            add(Improvement.RankUp(before.overallTier, before.division, after.overallTier, after.division))
        }
        week.scales.forEach { s ->
            val now = s.during ?: return@forEach
            val was = s.before
            if (was == null) add(Improvement.FirstClean(s.scaleKey, now)) else if (now > was) add(Improvement.ScaleFaster(s.scaleKey, was, now))
        }
        val inTune = week.avgInTune?.takeIf { week.holdCount > 0 }?.let { Math.round(it * 100).toInt() }
        val inTuneBefore = previous?.avgInTune?.takeIf { previous.holdCount > 0 }?.let { Math.round(it * 100).toInt() }
        if (inTune != null && inTuneBefore != null && inTune >= inTuneBefore + 3) add(Improvement.InTune(inTuneBefore, inTune))
        if (previous != null && week.actualMinutes > previous.actualMinutes + 5) add(Improvement.MoreMinutes(previous.actualMinutes, week.actualMinutes))
        if (week.plannedSessions > 0 && week.doneSessions >= week.plannedSessions) add(Improvement.AllSessions(week.doneSessions))
    }

    /**
     * Next week's aims: the weakest pillar (the program gives it more time), the slowest scale to push by [step],
     * the next scale to unlock, the long-tone notes held worst, and missed days.
     */
    fun next(
        week: WeeklyReview, player: PlayerState, limiter: Pillar?, unlockOrder: List<String>, step: Int,
        weakNotes: List<Int>,
    ): List<NextStep> = buildList {
        limiter?.let { add(NextStep.Weakest(it)) }
        val slowest = player.scales.minByOrNull { it.bpm }
        if (slowest != null) add(NextStep.PushScale(slowest.scaleKey, slowest.bpm, slowest.bpm + step))
        week.scales.firstOrNull { it.during == null }?.let { s -> if (s.scaleKey != slowest?.scaleKey) add(NextStep.StillScale(s.scaleKey)) }
        unlockOrder.firstOrNull { k -> player.scale(k) == null }?.let { add(NextStep.Unlock(it)) }
        if (weakNotes.isNotEmpty()) add(NextStep.LongTones(weakNotes.take(3)))
        if (week.skippedDays.size >= 2) add(NextStep.MissedDays(week.skippedDays.size))
    }

    /** Weekly minutes per block kind, before and after (only the kinds that change). */
    fun changes(before: Program, after: Program): List<ProgramChange> {
        fun minutes(p: Program) = p.days.flatMap { it.blocks }.groupBy { it.kind }.mapValues { (_, b) -> b.sumOf { it.minutes } }
        val a = minutes(before); val b = minutes(after)
        return BlockKind.values().mapNotNull { k ->
            val x = a[k] ?: 0; val y = b[k] ?: 0
            if (x != y) ProgramChange(k, x, y) else null
        }
    }
}
