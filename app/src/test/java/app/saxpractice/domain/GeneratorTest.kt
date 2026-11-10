package app.saxpractice.domain

import app.saxpractice.TestAssets
import app.saxpractice.domain.generator.Generator
import app.saxpractice.domain.program.Block
import app.saxpractice.domain.program.BlockKind
import app.saxpractice.domain.program.BlockKind.LONG_TONES
import app.saxpractice.domain.program.BlockKind.NEW_MATERIAL
import app.saxpractice.domain.program.BlockKind.PLAY_FOR_FUN
import app.saxpractice.domain.program.BlockKind.SCALES
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.domain.program.Focus
import app.saxpractice.domain.program.Pillar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorTest {
    private val config = TestAssets.generatorConfig

    private fun split(m: Int) = Generator.baseSplit(m, config)

    @Test fun tableSplits() {
        assertEquals(mapOf(LONG_TONES to 3, SCALES to 4, NEW_MATERIAL to 0, PLAY_FOR_FUN to 3), split(10))
        assertEquals(mapOf(LONG_TONES to 4, SCALES to 6, NEW_MATERIAL to 5, PLAY_FOR_FUN to 5), split(20))
        assertEquals(mapOf(LONG_TONES to 5, SCALES to 8, NEW_MATERIAL to 9, PLAY_FOR_FUN to 8), split(30))
    }

    @Test fun interpolatedSplitsSumExactly() {
        for (m in 3..90) {
            val s = split(m)
            assertEquals(m, s.values.sum(), "total for $m")
            assertTrue(s.values.all { it >= 0 })
        }
        // 15 minutes sits between the 10 and 20 rows.
        val s15 = split(15)
        assertTrue(s15.getValue(NEW_MATERIAL) in 2..3, "$s15")
        assertTrue(s15.getValue(SCALES) == 5, "$s15")
        // Monotonic in session length for each block.
        for (m in 10..29) BlockKind.entries.filter { it != BlockKind.BREATHING }.forEach { k ->
            assertTrue(split(m + 1).getValue(k) >= split(m).getValue(k) - 1, "$k at $m")
        }
    }

    @Test fun generatedProgramUsesTheSplit() {
        listOf(10, 20, 30).forEach { m ->
            val p = Generator.generate(input(minutes = m), config)
            assertEquals(3, p.days.size)
            p.days.forEach { d ->
                assertEquals(m, d.minutes)
                assertEquals(split(m).filterValues { it > 0 }, d.blocks.associate { it.kind to it.minutes })
                // Playing order.
                assertEquals(d.blocks.map { it.kind }.sortedBy { it.ordinal }, d.blocks.map { it.kind })
            }
        }
        // No new material at 10 minutes.
        assertTrue(Generator.generate(input(minutes = 10), config).days.first().blocks.none { it.kind == NEW_MATERIAL })
    }

    @Test fun newMaterialDisappearsFirstWhenTimeIsShort() {
        val day = Generator.generate(input(minutes = 30), config).days.first()
        val cut = Generator.fitToMinutes(day.blocks, 20, config)
        assertTrue(cut.none { it.kind == NEW_MATERIAL })
        assertEquals(20, cut.sumOf { it.minutes })
        assertTrue(cut.any { it.kind == PLAY_FOR_FUN })
        // 20 -> 10 gives the 10-minute split.
        val day20 = Generator.generate(input(minutes = 20), config).days.first()
        val cut10 = Generator.fitToMinutes(day20.blocks, 10, config).associate { it.kind to it.minutes }
        assertEquals(mapOf(LONG_TONES to 3, SCALES to 4, PLAY_FOR_FUN to 3), cut10)
        // Very short: play for fun is protected, scales go after new material.
        val tiny = Generator.fitToMinutes(day20.blocks, 2, config)
        assertTrue(tiny.any { it.kind == PLAY_FOR_FUN })
        assertEquals(2, tiny.sumOf { it.minutes })
        // Enough time: unchanged.
        assertEquals(day20.blocks, Generator.fitToMinutes(day20.blocks, 25, config))
    }

    @Test fun focusShiftsMinutes() {
        val base = split(20)
        val tone = Generator.adjustedSplit(20, listOf(Focus.TONE), null, config)
        assertEquals(base.getValue(LONG_TONES) + 2, tone.getValue(LONG_TONES))
        assertEquals(20, tone.values.sum())
        assertEquals(base.getValue(PLAY_FOR_FUN), tone.getValue(PLAY_FOR_FUN), "play for fun never donates")
        val tech = Generator.adjustedSplit(20, listOf(Focus.TECHNIQUE, Focus.REPERTOIRE), null, config)
        assertEquals(base.getValue(SCALES) + 2, tech.getValue(SCALES))
        assertEquals(20, tech.values.sum())
        // Reading focus is served by phone drills, not sax minutes.
        assertEquals(base, Generator.adjustedSplit(20, listOf(Focus.READING), null, config))
    }

    @Test fun limiterAdjustments() {
        val base = split(20)
        assertEquals(base.getValue(LONG_TONES) + 2, Generator.adjustedSplit(20, emptyList(), Pillar.SOUND, config).getValue(LONG_TONES))
        assertEquals(base.getValue(SCALES) + 2, Generator.adjustedSplit(20, emptyList(), Pillar.TECHNIQUE, config).getValue(SCALES))
        assertEquals(base.getValue(NEW_MATERIAL) + 2, Generator.adjustedSplit(20, emptyList(), Pillar.REPERTOIRE, config).getValue(NEW_MATERIAL))
        val range = Generator.generate(input(minutes = 20, limiter = Pillar.RANGE), config)
        assertTrue(range.days.first().blocks.first { it.kind == LONG_TONES }.exercises.any { it.type == ExerciseType.RANGE_EXTENSION })
        val musician = Generator.generate(input(minutes = 30, limiter = Pillar.MUSICIANSHIP), config)
        val plain = Generator.generate(input(minutes = 30), config)
        assertTrue(musician.phoneDrills.size > plain.phoneDrills.size)
        val consistency = Generator.generate(input(minutes = 30, limiter = Pillar.CONSISTENCY), config)
        assertEquals(listOf(Generator.NOTE_CONSISTENCY_PREFIX + "20"), consistency.notes)
    }

    @Test fun phoneDrillsCappedAtTwentyPercent() {
        val drills = listOf(ExerciseType.READING, ExerciseType.THEORY_LESSON)
        for (days in 1..7) for (m in listOf(5, 10, 15, 20, 30, 45, 60)) {
            val p = Generator.generate(input(minutes = m, days = (1..days).toSet(), drills = drills), config)
            assertTrue(p.weeklyPhoneMinutes <= p.weeklySaxMinutes * 0.2 + 1e-9, "days=$days m=$m")
        }
        val p = Generator.generate(input(minutes = 30, drills = drills), config) // 90 sax min -> cap 18
        assertEquals(3, p.phoneDrills.size)
        assertEquals(15, p.weeklyPhoneMinutes)
        assertTrue(Generator.generate(input(minutes = 30, drills = emptyList()), config).phoneDrills.isEmpty())
        val reading = Generator.generate(input(minutes = 30, drills = drills, focus = listOf(Focus.THEORY)), config)
        assertEquals(ExerciseType.THEORY_LESSON, reading.phoneDrills.first().type)
    }

    @Test fun deterministic() {
        val a = Generator.generate(input(minutes = 25, focus = listOf(Focus.TONE), limiter = Pillar.RANGE), config)
        val b = Generator.generate(input(minutes = 25, focus = listOf(Focus.TONE), limiter = Pillar.RANGE), config)
        assertEquals(a, b)
    }

    @Test fun fitKeepsBlocksAtLeastOneMinute() {
        val blocks = listOf(Block("a", LONG_TONES, 5, emptyList()), Block("b", SCALES, 5, emptyList()), Block("c", PLAY_FOR_FUN, 5, emptyList()))
        val cut = Generator.fitToMinutes(blocks, 4, config)
        assertTrue(cut.all { it.minutes >= 1 })
        assertEquals(4, cut.sumOf { it.minutes })
    }

    private fun input(
        minutes: Int,
        days: Set<Int> = setOf(1, 3, 5),
        focus: List<Focus> = emptyList(),
        limiter: Pillar? = null,
        drills: List<ExerciseType> = listOf(ExerciseType.READING),
    ) = Generator.Input("p", "Test", days, minutes, 300, focus, limiter, drills)
}
