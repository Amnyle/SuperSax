package app.supersax.domain

import app.supersax.domain.common.RunRecord
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.program.PlayerState
import app.supersax.domain.stats.Badges
import app.supersax.domain.stats.Stats
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 12) // a Thursday
    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000
    private var n = 0
    private fun session(d: LocalDate, minutes: Int, phone: Boolean = false, runs: List<RunRecord> = emptyList()) =
        SessionRecord("s${n++}", ms(d), ms(d) + minutes * 60_000L, null, null, null, phone = phone, plannedSec = minutes * 60, playedSec = minutes * 60, runs = runs)

    @Test fun minutesHeatmapAndWeeks() {
        val s = listOf(session(today, 20), session(today, 5, phone = true), session(today.minusDays(1), 30), session(today.minusWeeks(2), 15))
        val daily = Stats.dailyMinutes(s, zone)
        assertEquals(20, daily[today]!![0]); assertEquals(5, daily[today]!![1])
        val heat = Stats.heatmap(daily, today, 4)
        assertEquals(4, heat.size)
        assertEquals(25, heat.last()[3].minutes, "Thursday of this week")
        assertEquals(-1, heat.last()[4].minutes, "Friday is in the future")
        val weeks = Stats.weeklyMinutes(daily, today, 3)
        assertEquals(listOf(15, 0, 50), weeks.map { it[0] })
    }

    @Test fun streaks() {
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(10), today.minusDays(11))
        val st = Stats.streaks(days, today)
        assertEquals(3, st[0], "alive until today ends")
        assertEquals(3, st[1])
        assertEquals(0, Stats.streaks(setOf(today.minusDays(5)), today)[0])
    }

    @Test fun cleanTempoPerScaleAndDay() {
        val s = listOf(session(today.minusDays(3), 20, runs = listOf(RunRecord("MAJOR:Eb", 60, true), RunRecord("MAJOR:Eb", 65, false))),
            session(today, 20, runs = listOf(RunRecord("MAJOR:Eb", 70, true), RunRecord("MAJOR:Eb", 75, true))))
        val t = Stats.cleanTempo(s, zone)["MAJOR:Eb"]!!
        assertEquals(listOf(60, 75), t.values.toList())
    }

    @Test fun badgesProgress() {
        val sessions = (0 until 7).map { session(today.minusDays(it.toLong()), 25) }
        val b = Badges.compute(sessions, emptyList(), emptyList(), emptyList(), PlayerState(), zone, today, 13).associateBy { it.id }
        assertTrue(b[Badges.Id.FIRST_SESSION]!!.earned)
        assertTrue(b[Badges.Id.STREAK_7]!!.earned)
        assertEquals(7, b[Badges.Id.SESSIONS_10]!!.current)
        assertEquals(2, b[Badges.Id.HOURS_5]!!.current)
        assertTrue(!b[Badges.Id.STREAK_30]!!.earned)
        assertEquals(Badges.Id.entries.size, b.size)
    }
}
