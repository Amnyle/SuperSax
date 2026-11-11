package app.saxpractice.data.repo

import app.saxpractice.AppContainer
import app.saxpractice.domain.common.RankSnapshotRecord
import app.saxpractice.domain.rank.RankCalculator
import app.saxpractice.domain.rank.RankEvents
import app.saxpractice.domain.rank.RankResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** Computes the rank from stored logs and keeps one snapshot per change (for the weekly review). No notifications. */
class RankService(private val c: AppContainer) {
    private data class Part1(val events: List<app.saxpractice.domain.common.RankEventRecord>, val holds: List<app.saxpractice.domain.common.TunerHoldRecord>,
        val sessions: List<app.saxpractice.domain.common.SessionRecord>, val program: app.saxpractice.domain.program.Program?)
    private data class Part2(val progress: List<app.saxpractice.domain.common.PieceProgressRecord>, val pieces: List<app.saxpractice.domain.library.Piece>,
        val drills: List<app.saxpractice.domain.common.DrillResultRecord>, val lessons: List<app.saxpractice.domain.common.LessonProgressRecord>)

    val events: Flow<RankEvents> = combine(
        combine(c.progress.rankEvents, c.progress.holds, c.practice.sessions, c.practice.activeProgram) { e, h, s, p -> Part1(e, h, s, p) },
        combine(c.library.progress, c.libraryService.pieces, c.progress.drills, c.progress.lessons) { pp, ps, d, l -> Part2(pp, ps, d, l) },
    ) { a, b ->
        RankEvents(
            rankEvents = a.events, holds = a.holds, sessions = a.sessions,
            plannedDays = a.program?.days?.map { it.dayOfWeek }?.toSet() ?: emptySet(),
            pieceProgress = b.progress, pieceDifficulty = b.pieces.associate { it.id to it.difficulty },
            drills = b.drills, lessons = b.lessons, zone = ZoneId.systemDefault(),
        )
    }

    val result: Flow<RankResult> = combine(events, c.progress.holds) { e, _ -> compute(e) }

    fun compute(e: RankEvents): RankResult = RankCalculator.computeRank(e, c.content.rankConfig, c.clock.nowMs())

    suspend fun current(): RankResult = compute(events.first())

    /** Stores a snapshot when the overall rank or a pillar changed since the last one. */
    suspend fun snapshotIfChanged(r: RankResult) {
        val tiers = r.pillars.mapKeys { it.key.name }.mapValues { it.value.tier }
        val last = c.progress.snapshotsNow().maxByOrNull { it.atMs }
        if (last == null || last.overallTier != r.overallTier || last.division != r.division || last.pillarTiers != tiers) {
            c.progress.saveSnapshot(RankSnapshotRecord(c.clock.nowMs(), r.overallTier, r.division, tiers))
        }
    }
}
