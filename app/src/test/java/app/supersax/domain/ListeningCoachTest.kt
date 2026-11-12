package app.supersax.domain

import app.supersax.domain.audio.PitchFrame
import app.supersax.domain.audio.RunDetector
import app.supersax.domain.drills.SightReading
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ListeningCoachTest {
    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Frames every 10 ms: each note held [noteSec], then [silenceSec] of silence. */
    private fun frames(notes: List<Int>, noteSec: Double = 0.3, start: Double = 0.0, silenceSec: Double = 1.0): List<PitchFrame> {
        val out = mutableListOf<PitchFrame>()
        var t = start
        notes.forEach { m -> var k = 0.0; while (k < noteSec) { out += PitchFrame(t, hz(m), 0.95, 0.1); t += 0.01; k += 0.01 } }
        var k = 0.0
        while (k < silenceSec) { out += PitchFrame(t, null, 0.0, 0.0); t += 0.01; k += 0.01 }
        return out
    }

    private val eMajorConcert = listOf(63, 65, 67, 68, 70, 72, 74, 75, 74, 72, 70, 68, 67, 65, 63) // E♭ major up and down

    @Test fun aCleanRunInAnyOctaveIsClean() {
        val d = RunDetector(eMajorConcert, 440.0)
        val runs = frames(eMajorConcert.map { it + 12 }).mapNotNull { d.feed(it) }
        assertEquals(1, runs.size)
        assertTrue(runs[0].clean)
        assertEquals(null, runs[0].firstMistake())
    }

    @Test fun aWrongNoteIsFoundAndNamed() {
        val d = RunDetector(eMajorConcert, 440.0)
        val played = eMajorConcert.toMutableList().also { it[3] = 69 } // A instead of A♭
        val run = frames(played).mapNotNull { d.feed(it) }.single()
        assertFalse(run.clean)
        val m = assertNotNull(run.firstMistake())
        assertEquals(3, m[0]); assertEquals(69, m[1])
    }

    @Test fun twoRunsSeparatedBySilenceAreTwoRuns() {
        val d = RunDetector(eMajorConcert, 440.0)
        val all = frames(eMajorConcert) + frames(eMajorConcert, start = 6.0)
        assertEquals(2, all.mapNotNull { d.feed(it) }.size)
    }

    @Test fun sightReadingMelodiesStayInTheLevel() {
        for (level in 1..5) for (seed in 1L..20L) {
            val m = SightReading.melody(level, seed)
            assertEquals(SightReading.NOTES, m.size)
            val range = app.supersax.domain.drills.ReadingDrill.level(level)
            assertTrue(m.all { it.midi in range.lowWrittenMidi..range.highWrittenMidi }, "$level $seed $m")
            assertTrue(m.zipWithNext().all { (a, b) -> kotlin.math.abs(a.midi - b.midi) <= 7 })
            assertEquals(2, SightReading.score(m, 80).measures.size)
        }
    }
}
