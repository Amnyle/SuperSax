package app.supersax.domain.review

import app.supersax.domain.common.RankSnapshotRecord
import app.supersax.domain.program.BlockKind
import java.time.LocalDate

data class BlockStat(val kind: BlockKind, val plannedMin: Int, val actualMin: Int)

/** Best clean-pair tempo of a scale before the week and during it (null = none). */
data class ScaleTempo(val scaleKey: String, val before: Int?, val during: Int?)

/** Facts of one week (Monday to Sunday). */
data class WeeklyReview(
    val weekStart: LocalDate,
    val plannedSessions: Int,
    val doneSessions: Int,
    val extraSessions: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val perBlock: List<BlockStat>,
    val skippedDays: List<Int>,
    val tiredBlocks: Int,
    val scales: List<ScaleTempo>,
    val holdCount: Int,
    val avgInTune: Double?,
    val avgDrift: Double?,
    val rankBefore: RankSnapshotRecord?,
    val rankAfter: RankSnapshotRecord?,
    val phoneMinutes: Int,
)
