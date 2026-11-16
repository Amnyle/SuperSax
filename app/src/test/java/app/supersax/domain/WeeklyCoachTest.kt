package app.supersax.domain

import app.supersax.domain.common.RankSnapshotRecord
import app.supersax.domain.program.Pillar
import app.supersax.domain.program.PlayerState
import app.supersax.domain.program.ScaleProgress
import app.supersax.domain.review.Improvement
import app.supersax.domain.review.NextStep
import app.supersax.domain.review.ScaleTempo
import app.supersax.domain.review.WeeklyCoach
import app.supersax.domain.review.WeeklyReview
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyCoachTest {
    private fun week(
        scales: List<ScaleTempo> = emptyList(), holds: Int = 0, inTune: Double? = null, minutes: Int = 60,
        done: Int = 3, planned: Int = 3, skipped: List<Int> = emptyList(),
        before: RankSnapshotRecord? = null, after: RankSnapshotRecord? = null,
    ) = WeeklyReview(LocalDate.of(2026, 11, 9), planned, done, 0, 90, minutes, emptyList(), skipped, 0, scales, holds, inTune, null, before, after, 0)

    @Test fun improvementsAgainstTheWeekBefore() {
        val now = week(
            scales = listOf(ScaleTempo("MAJOR:C", 200, 240), ScaleTempo("MAJOR:G", null, 90), ScaleTempo("MAJOR:F", 100, 100)),
            holds = 5, inTune = 0.80, minutes = 90,
            before = RankSnapshotRecord(0, 1, 1, emptyMap()), after = RankSnapshotRecord(1, 1, 2, emptyMap()),
        )
        val prev = week(holds = 4, inTune = 0.70, minutes = 60)
        val got = WeeklyCoach.improvements(now, prev)
        assertTrue(Improvement.RankUp(1, 1, 1, 2) in got)
        assertTrue(Improvement.ScaleFaster("MAJOR:C", 200, 240) in got)
        assertTrue(Improvement.FirstClean("MAJOR:G", 90) in got)
        assertTrue(got.none { it is Improvement.ScaleFaster && it.scaleKey == "MAJOR:F" })
        assertTrue(Improvement.InTune(70, 80) in got)
        assertTrue(Improvement.MoreMinutes(60, 90) in got)
        assertTrue(Improvement.AllSessions(3) in got)
    }

    @Test fun nextWeekAims() {
        val player = PlayerState(scales = listOf(ScaleProgress("MAJOR:C", 120), ScaleProgress("MAJOR:G", 80)))
        val next = WeeklyCoach.next(week(skipped = listOf(2, 4)), player, Pillar.SOUND, listOf("MAJOR:C", "MAJOR:G", "MAJOR:F"), 10, listOf(72, 74))
        assertEquals(NextStep.Weakest(Pillar.SOUND), next[0])
        assertTrue(NextStep.PushScale("MAJOR:G", 80, 90) in next)
        assertTrue(NextStep.Unlock("MAJOR:F") in next)
        assertTrue(NextStep.LongTones(listOf(72, 74)) in next)
        assertTrue(NextStep.MissedDays(2) in next)
    }
}
