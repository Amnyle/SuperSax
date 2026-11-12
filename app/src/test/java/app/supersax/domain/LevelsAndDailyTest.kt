package app.supersax.domain

import app.supersax.TestAssets
import app.supersax.domain.common.DrillKind
import app.supersax.domain.common.DrillResultRecord
import app.supersax.domain.drills.DailyMix
import app.supersax.domain.drills.FingeringChart
import app.supersax.domain.drills.FingeringDrill
import app.supersax.domain.drills.Levels
import app.supersax.domain.pitch.SpelledPitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LevelsAndDailyTest {
    private var t = 0L
    private fun result(kind: DrillKind, level: Int, correct: Int, total: Int) =
        DrillResultRecord("r${t}", t++, kind, level, total, correct, 1000)

    @Test fun starsFromScore() {
        assertEquals(0, Levels.stars(5, 10))
        assertEquals(1, Levels.stars(6, 10))
        assertEquals(1, Levels.stars(7, 10))
        assertEquals(2, Levels.stars(8, 10))
        assertEquals(2, Levels.stars(14, 15))
        assertEquals(3, Levels.stars(15, 15))
        assertEquals(2, Levels.stars(12, 15), "80 percent exactly")
    }

    @Test fun twoStarsOpenTheNextLevel() {
        val r = mutableListOf<DrillResultRecord>()
        assertEquals(1, Levels.unlocked(r, DrillKind.READING, 5))
        r += result(DrillKind.READING, 1, 9, 15) // 1 star
        assertEquals(1, Levels.unlocked(r, DrillKind.READING, 5))
        r += result(DrillKind.READING, 1, 12, 15) // 2 stars
        assertEquals(2, Levels.unlocked(r, DrillKind.READING, 5))
        // The best round counts: a worse round later does not lock anything.
        r += result(DrillKind.READING, 1, 3, 15)
        assertEquals(2, Levels.best(r, DrillKind.READING, 1))
        assertEquals(2, Levels.unlocked(r, DrillKind.READING, 5))
        // Other drills and the daily mix never count for a ladder.
        r += result(DrillKind.SCALE_RECOGNITION, 2, 8, 8)
        r += result(DrillKind.DAILY, 0, 12, 12)
        assertEquals(2, Levels.unlocked(r, DrillKind.READING, 5))
        assertEquals(1, Levels.unlocked(r, DrillKind.SCALE_RECOGNITION, 8), "level 2 without level 1 does not open level 3")
        // Capped at the last level.
        (1..5).forEach { r += result(DrillKind.READING, it, 15, 15) }
        assertEquals(5, Levels.unlocked(r, DrillKind.READING, 5))
        assertEquals(15, Levels.totalStars(r, DrillKind.READING, 5))
    }

    @Test fun fingeringLevelsGrowAndStayInTheStandardRange() {
        val chart = TestAssets.json.decodeFromString(FingeringChart.serializer(), TestAssets.text("fingerings/alto_fingerings.json"))
        val sizes = (1..FingeringDrill.LEVELS).map { FingeringDrill.pool(chart, it).size }
        assertEquals(4, sizes[0], "left hand: G A B C")
        assertTrue(sizes.zipWithNext().all { (a, b) -> b > a }, "$sizes")
        (1..FingeringDrill.LEVELS).forEach { l -> assertTrue(FingeringDrill.pool(chart, l).none { it.altissimo }) }
        assertTrue(FingeringDrill.pool(chart, 3).all { SpelledPitch.parse(it.note).alter == 0 })
        val round = FingeringDrill.round(chart, 1, 10, 7L)
        assertEquals(10, round.size)
    }

    @Test fun dailyMixCountsAndOrder() {
        val noData = DailyMix.counts(false, emptyMap())
        assertEquals(mapOf(DrillKind.READING to 5, DrillKind.SCALE_RECOGNITION to 4, DrillKind.FINGERING to 3, DrillKind.EAR to 2, DrillKind.RHYTHM to 1), noData)
        val withTheory = DailyMix.counts(true, mapOf(DrillKind.READING to 0.95, DrillKind.FINGERING to 0.5))
        assertEquals(5, withTheory[DrillKind.FINGERING], "the weakest drill gets 2 more")
        assertEquals(2, withTheory[DrillKind.THEORY_QUIZ])
        assertEquals(17, withTheory.values.sum())
        val order = DailyMix.order(withTheory, 3L)
        assertEquals(17, order.size)
        withTheory.forEach { (k, n) -> assertEquals(n, order.count { it == k }) }
        assertTrue(order.zipWithNext().none { (a, b) -> a == b }, "$order")
    }

    @Test fun streakCountsDaysInARow() {
        assertEquals(0, DailyMix.streak(emptySet(), 100))
        assertEquals(1, DailyMix.streak(setOf(100), 100))
        assertEquals(3, DailyMix.streak(setOf(98, 99, 100), 100))
        assertEquals(2, DailyMix.streak(setOf(98, 99), 100), "still alive until today ends")
        assertEquals(0, DailyMix.streak(setOf(97, 98), 100), "missed yesterday")
        assertNotEquals(4, DailyMix.streak(setOf(96, 98, 99, 100), 100))
    }
}
