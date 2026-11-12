package app.supersax.domain

import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.solo.Blues
import app.supersax.domain.solo.Groove
import app.supersax.domain.solo.SoloGenerator
import app.supersax.domain.solo.SoloGenerator.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoloGeneratorTest {
    @Test fun everySoloFitsItsBarsRangeAndEndsOnTheRoot() {
        for (st in Groove.Style.entries) for (key in Blues.KEYS) for (lv in Level.entries) for (seed in 1L..12L) {
            val k = PitchClass.parse(key)
            val s = SoloGenerator.generate(st, k, lv, seed)
            val bars = SoloGenerator.bars(st)
            // no overlaps, nothing past the end
            s.zipWithNext().forEach { (a, b) -> assertTrue(a.start + a.beats <= b.start + 1e-9, "$st $key $lv $seed overlap at ${a.start}") }
            assertTrue(s.last().start + s.last().beats <= bars * 4 + 1e-9)
            val range = when (lv) { Level.EASY -> 60..77; Level.MEDIUM -> 58..81; Level.HARD -> 58..84; Level.EXTREME -> 58..89 }
            s.mapNotNull { it.midi }.forEach { assertTrue(it + 9 in (range.first - 1)..(range.last + 1), "$st $lv written ${it + 9}") }
            val last = s.last { it.midi != null }
            assertEquals(k.semitone, Math.floorMod(last.midi!!, 12), "$st $key $lv $seed ends on the root")
            // the score has full bars
            val score = SoloGenerator.toScore(s, st, k, st.defaultBpm)
            assertEquals(bars, score.measures.size)
            score.measures.forEach { m -> assertEquals(1.0, m.notes.sumOf { it.duration.value }, 1e-9, "$st $lv $seed bar") }
            if (lv == Level.EASY) assertTrue(s.all { it.beats >= 1 - 1e-9 }, "easy = quarters and longer")
        }
    }

    @Test fun regenerateGivesSomethingDifferent() {
        val k = PitchClass.parse("Bb")
        val a = SoloGenerator.generate(Groove.Style.BLUES, k, Level.MEDIUM, 1).map { it.midi }
        val b = SoloGenerator.generate(Groove.Style.BLUES, k, Level.MEDIUM, 2).map { it.midi }
        assertTrue(a != b)
        val mix = Groove.render(Groove.Style.BLUES, 46, 80.0, 8000, null, 70, null, SoloGenerator.generate(Groove.Style.BLUES, k, Level.HARD, 3))
        assertTrue(mix.stems[Groove.Part.LEAD.ordinal].any { kotlin.math.abs(it) > 0.01f })
    }
}
