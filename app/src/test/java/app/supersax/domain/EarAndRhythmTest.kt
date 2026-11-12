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

class RhythmHoldTest {
    private val beat = 1000.0
    private fun spans(vararg b: Double) = b.toList().chunked(2).map { doubleArrayOf(it[0] * beat, it[1] * beat) }

    @Test fun spansFollowDurationsAndSwing() {
        val q = RhythmDrill.round(3, 60, 5L).first { it.durations.contains(2) }
        val s = q.spans()
        assertEquals(q.durations.count { it > 0 }, s.size)
        assertEquals(4.0, s.last()[1] + q.durations.reversed().takeWhile { it < 0 }.sumOf { -it } / 4.0, 1e-9)
        val sw = RhythmDrill.round(7, 60, 5L).first { it.durations.take(2) == listOf(2, 2) }
        assertEquals(2.0 / 3, sw.spans()[0][1], 1e-9)
    }

    @Test fun holdsJudged() {
        val target = spans(0.0, 1.0, 1.0, 3.0, 3.0, 4.0) // quarter, half, quarter
        val good = listOf(doubleArrayOf(130.0, 1080.0), doubleArrayOf(1120.0, 3050.0), doubleArrayOf(3110.0, 4150.0))
        assertTrue(RhythmDrill.judgeHolds(target, good, 100.0, beat).correct, "constant delay removed")
        val short = listOf(doubleArrayOf(0.0, 950.0), doubleArrayOf(1000.0, 1500.0), doubleArrayOf(3000.0, 3950.0))
        assertEquals(RhythmDrill.Verdict.SHORT, RhythmDrill.judgeHolds(target, short, 100.0, beat).verdicts[1])
        val long = listOf(doubleArrayOf(0.0, 2000.0), doubleArrayOf(2050.0, 3000.0), doubleArrayOf(3000.0, 3950.0))
        val r = RhythmDrill.judgeHolds(target, long, 100.0, beat)
        assertFalse(r.correct)
        assertEquals(RhythmDrill.Verdict.LONG, r.verdicts[0])
        val missing = listOf(doubleArrayOf(0.0, 950.0), doubleArrayOf(3000.0, 3950.0))
        assertEquals(RhythmDrill.Verdict.MISSED, RhythmDrill.judgeHolds(target, missing, 100.0, beat).verdicts[1])
        assertFalse(RhythmDrill.judgeHolds(target, emptyList(), 100.0, beat).correct)
    }
}

class RhythmTapTest {
    private val beat = 1000.0
    private val spans = listOf(doubleArrayOf(0.0, 1000.0), doubleArrayOf(1000.0, 3000.0), doubleArrayOf(3000.0, 4000.0))

    @Test fun constantDelayIsRemovedAndScored() {
        val taps = listOf(doubleArrayOf(150.0, 400.0), doubleArrayOf(1160.0, 2900.0), doubleArrayOf(3140.0, 3300.0))
        val r = RhythmDrill.scoreTaps(spans, taps, 100.0, beat, 0.0)
        assertTrue(r.passed); assertTrue(r.meanErrorMs < 15, "${r.meanErrorMs}")
        assertEquals(100.0, r.percent, 1e-9)
    }

    @Test fun shortNotesOnlyNeedTheirStartLongNotesMustBeHeld() {
        val quickRelease = listOf(doubleArrayOf(0.0, 80.0), doubleArrayOf(1000.0, 1150.0), doubleArrayOf(3000.0, 3080.0))
        val r = RhythmDrill.scoreTaps(spans, quickRelease, 100.0, beat, 0.0)
        assertEquals(RhythmDrill.Verdict.OK, r.notes[0].verdict)
        assertEquals(RhythmDrill.Verdict.SHORT, r.notes[1].verdict, "the half note was let go at once")
        assertFalse(r.passed)
    }

    @Test fun lateMissedAndExtra() {
        val late = listOf(doubleArrayOf(0.0, 500.0), doubleArrayOf(1170.0, 2900.0), doubleArrayOf(3000.0, 3500.0), doubleArrayOf(3500.0, 3600.0))
        val r = RhythmDrill.scoreTaps(spans, late, 100.0, beat, 0.0)
        assertEquals(RhythmDrill.Verdict.LATE, r.notes[1].verdict)
        assertEquals(1, r.extra)
        val missed = RhythmDrill.scoreTaps(spans, listOf(doubleArrayOf(0.0, 500.0)), 100.0, beat, 0.0)
        assertEquals(RhythmDrill.Verdict.MISSED, missed.notes[1].verdict)
        assertFalse(missed.passed)
    }

    @Test fun countingLine() {
        val q = RhythmDrill.round(3, 80, 3L).first { it.durations == listOf(4, 8, 2, 2) || it.durations.first() == 8 }
        assertTrue(RhythmDrill.counting(q).startsWith("1"))
    }
}

class KeyReadingTest {
    @Test fun levelFourReadsTheKeySignature() {
        val qs = app.supersax.domain.drills.ReadingDrill.questions(4, 200, 9L)
        assertTrue(qs.all { it.fifths != 0 && kotlin.math.abs(it.fifths) <= 3 })
        qs.forEach { q -> assertEquals(app.supersax.domain.pitch.KeySignature(q.fifths).alterFor(q.pitch.letter), q.pitch.alter, "level 4 has no accidentals in the bar") }
        assertTrue(qs.count { it.pitch.alter != 0 } > 80, "most notes are on a letter the key alters")
        val five = app.supersax.domain.drills.ReadingDrill.questions(5, 300, 4L)
        assertTrue(five.any { app.supersax.domain.pitch.KeySignature(it.fifths).alterFor(it.pitch.letter) != it.pitch.alter }, "level 5 has accidentals")
        assertEquals(qs, app.supersax.domain.drills.ReadingDrill.questions(4, 200, 9L))
    }
}
