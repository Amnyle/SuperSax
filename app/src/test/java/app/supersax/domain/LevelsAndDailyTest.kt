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
        // Accuracy only (theory): 80 / 90 / 100 percent.
        assertEquals(0, Levels.stars(7, 10))
        assertEquals(1, Levels.stars(8, 10))
        assertEquals(2, Levels.stars(9, 10))
        assertEquals(3, Levels.stars(15, 15))
        // Drills with a speed ceiling: perfect but slow is only 1 star; 3 stars need expert speed.
        assertEquals(1, Levels.stars(DrillKind.READING, 15, 15, 4000))
        assertEquals(2, Levels.stars(DrillKind.READING, 14, 15, 2000))
        assertEquals(1, Levels.stars(DrillKind.READING, 13, 15, 900), "87 percent is 1 star however fast")
        assertEquals(2, Levels.stars(DrillKind.READING, 15, 15, 1500))
        assertEquals(3, Levels.stars(DrillKind.READING, 15, 15, 1100))
        assertEquals(3, Levels.stars(DrillKind.RHYTHM, 8, 8, 30))
        assertEquals(2, Levels.stars(DrillKind.RHYTHM, 8, 8, 60))
        // Timed reading.
        assertEquals(0, Levels.speedStars(14, 14))
        assertEquals(1, Levels.speedStars(20, 21))
        assertEquals(0, Levels.speedStars(30, 40), "too many wrong")
        assertEquals(3, Levels.speedStars(45, 47))
    }

    @Test fun twoStarsOpenTheNextLevel() {
        val r = mutableListOf<DrillResultRecord>()
        assertEquals(1, Levels.unlocked(r, DrillKind.READING, 5))
        r += result(DrillKind.READING, 1, 11, 15) // 73 percent: no star
        assertEquals(1, Levels.unlocked(r, DrillKind.READING, 5))
        r += result(DrillKind.READING, 1, 12, 15) // 80 percent: 1 star opens level 2
        assertEquals(2, Levels.unlocked(r, DrillKind.READING, 5))
        // The best round counts: a worse round later does not lock anything.
        r += result(DrillKind.READING, 1, 3, 15)
        assertEquals(1, Levels.best(r, DrillKind.READING, 1))
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
        assertEquals(mapOf(DrillKind.READING to 5, DrillKind.SCALE_RECOGNITION to 4, DrillKind.FINGERING to 3, DrillKind.EAR to 2), noData)
        val withTheory = DailyMix.counts(true, mapOf(DrillKind.READING to 0.95, DrillKind.FINGERING to 0.5))
        assertEquals(5, withTheory[DrillKind.FINGERING], "the weakest drill gets 2 more")
        assertEquals(2, withTheory[DrillKind.THEORY_QUIZ])
        assertEquals(16, withTheory.values.sum())
        val order = DailyMix.order(withTheory, 3L)
        assertEquals(16, order.size)
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
