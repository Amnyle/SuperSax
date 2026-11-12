package app.supersax.domain

import app.supersax.domain.drills.EarDrill
import app.supersax.domain.drills.RhythmDrill
import app.supersax.domain.pitch.PitchClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EarAndRhythmTest {
    @Test fun earQuestionsMatchTheirLevel() {
        for (level in 1..EarDrill.LEVELS) {
            EarDrill.round(level, 30, level.toLong()).forEach { q ->
                assertEquals(EarDrill.kind(level), q.kind)
                when (q.kind) {
                    EarDrill.Kind.DIRECTION -> assertEquals(if (q.answer == 0) 1 else -1, Integer.signum(q.writtenMidi[1] - q.writtenMidi[0]))
                    EarDrill.Kind.INTERVAL -> assertEquals(q.options[q.answer], q.writtenMidi[1] - q.writtenMidi[0])
                    EarDrill.Kind.CHORD -> assertEquals(EarDrill.ChordType.values()[q.options[q.answer]].semis.size, q.writtenMidi.size)
                    EarDrill.Kind.MELODY -> {
                        assertEquals(if (level == 7) 3 else 4, q.writtenMidi.size)
                        assertTrue(EarDrill.checkMelody(q, q.writtenMidi.map { PitchClass.parse(app.supersax.domain.pitch.SpelledPitch.fromMidi(it).pitchClass.toString()) }))
                    }
                }
                assertTrue(q.writtenMidi.all { it in 55..84 }, "playable range: ${q.writtenMidi}")
            }
        }
        assertEquals(listOf(2, 4, 7, 12), EarDrill.intervals(2))
    }

    @Test fun rhythmBarsAreFullBarsOfTheLevelsCells() {
        for (level in 1..RhythmDrill.LEVELS) {
            RhythmDrill.round(level, 40, level * 7L).forEach { q ->
                assertEquals(16, q.durations.sumOf { kotlin.math.abs(it) }, q.measure())
                assertTrue(q.durations.first() > 0)
                if (level == 1) assertTrue(q.durations.all { it in listOf(4, 8, 16) })
                if (level < 6) assertTrue(q.durations.none { kotlin.math.abs(it) == 1 })
                // The score format parses.
                app.supersax.domain.library.Score.parseMeasure(q.measure())
            }
        }
    }

    @Test fun swingMovesTheOffbeat() {
        val q = RhythmDrill.round(7, 50, 3L).first { it.durations.contains(2) }
        val onsets = q.onsets()
        assertTrue(onsets.any { kotlin.math.abs(it - kotlin.math.floor(it / 4) * 4 - 8.0 / 3) < 1e-9 }, "$onsets")
    }

    @Test fun judgingIgnoresAConstantDelayButNotWrongTaps() {
        val expected = listOf(0.0, 500.0, 1000.0, 1250.0, 1500.0)
        // 120 ms late on everything (phone delay) with a little human jitter: right.
        assertTrue(RhythmDrill.judge(expected, expected.mapIndexed { i, e -> e + 120 + (if (i % 2 == 0) 20 else -15) }, 100.0).correct)
        // One note missing.
        val missing = RhythmDrill.judge(expected, listOf(0.0, 500.0, 1000.0, 1500.0), 100.0)
        assertFalse(missing.correct); assertEquals(1, missing.missed)
        // One extra tap.
        val extra = RhythmDrill.judge(expected, expected + 750.0, 100.0)
        assertFalse(extra.correct); assertEquals(1, extra.extra)
        // Rushed eighth: 200 ms early on one note.
        assertFalse(RhythmDrill.judge(expected, listOf(0.0, 500.0, 1000.0, 1050.0, 1500.0), 100.0).correct)
        assertFalse(RhythmDrill.judge(expected, emptyList(), 100.0).correct)
    }
}
