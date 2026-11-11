package app.supersax.domain

import app.supersax.domain.audio.AltissimoCheck
import app.supersax.domain.audio.OvertoneDrill
import app.supersax.domain.audio.PitchTracker
import app.supersax.domain.audio.ScaleChecker
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.Tuning
import app.supersax.domain.scale.ScaleGenerator
import app.supersax.domain.scale.ScaleId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancedListeningTest {
    private val sr = AudioSignals.SR

    /** Notes of [noteSec] each, with [gapSec] of silence between (tongued) or none (slurred). */
    private fun play(midis: List<Int>, noteSec: Double, gapSec: Double = 0.0): FloatArray {
        val parts = mutableListOf<FloatArray>()
        parts += AudioSignals.silence(0.2)
        midis.forEach { m ->
            parts += AudioSignals.tone(Tuning.midiToFrequency(m), noteSec, listOf(1.0, 0.6, 0.3))
            if (gapSec > 0) parts += AudioSignals.silence(gapSec)
        }
        parts += AudioSignals.silence(0.3)
        val out = FloatArray(parts.sumOf { it.size }); var o = 0
        parts.forEach { System.arraycopy(it, 0, out, o, it.size); o += it.size }
        return out
    }

    private val scale = ScaleGenerator.oneOctave(ScaleId.parse("MAJOR:Eb"), 4).map { it.midi }.let { it + it.dropLast(1).reversed() }

    @Test fun slurredScaleIsClean() {
        val frames = PitchTracker().push(play(scale, 0.25))
        val r = ScaleChecker().check(frames, scale)
        assertTrue(r.clean, "matched ${r.matched} wrong ${r.wrong} missing ${r.missing} extra ${r.extra}: ${r.played.map { it.midi }}")
        assertTrue(abs(r.notesPerMinute - 240) < 25, "npm ${r.notesPerMinute}") // eighths at 120 BPM
    }

    @Test fun tonguedScaleIsClean() {
        val frames = PitchTracker().push(play(scale, 0.2, gapSec = 0.05))
        val r = ScaleChecker().check(frames, scale)
        assertTrue(r.clean, "${r.played.map { it.midi }}")
    }

    @Test fun mistakesAreCounted() {
        val wrong = scale.toMutableList().also { it[3] = it[3] + 1 } // a wrong note
        val r1 = ScaleChecker().check(PitchTracker().push(play(wrong, 0.25)), scale)
        assertFalse(r1.clean); assertEquals(1, r1.wrong)
        val skipped = scale.filterIndexed { i, _ -> i != 5 }
        val r2 = ScaleChecker().check(PitchTracker().push(play(skipped, 0.25)), scale)
        assertEquals(1, r2.missing); assertFalse(r2.clean)
    }

    @Test fun overtonesAndAltissimo() {
        val a4 = 440.0
        val t = OvertoneDrill.Target(58, 2) // low B♭ fingering, 2nd partial
        val expected = OvertoneDrill.expectedHz(t, a4)
        assertEquals(Tuning.midiToFrequency(Instrument.ALTO_SAX.writtenToConcertMidi(58)) * 2, expected, 1e-9)
        assertEquals(70, OvertoneDrill.soundingWrittenMidi(t, a4)) // sounds as written B♭4
        assertTrue(OvertoneDrill.matches(expected * Math.pow(2.0, 20 / 1200.0), t, a4))
        assertFalse(OvertoneDrill.matches(expected * Math.pow(2.0, 60 / 1200.0), t, a4))
        // 3rd partial of low D is written A5 (octave and a fifth up).
        assertEquals(81, OvertoneDrill.soundingWrittenMidi(OvertoneDrill.Target(62, 3), a4))
        assertFalse(OvertoneDrill.milestoneDone(OvertoneDrill.milestone.drop(1).toSet()))
        assertTrue(OvertoneDrill.milestoneDone(OvertoneDrill.milestone.toSet()))
        // Altissimo G6 sounds concert B♭5; detection range reaches written D7 (concert F6, 1397 Hz).
        val g6 = Tuning.midiToFrequency(Instrument.ALTO_SAX.writtenToConcertMidi(91))
        assertTrue(AltissimoCheck.matches(g6 * Math.pow(2.0, 35 / 1200.0), 91, a4))
        assertFalse(AltissimoCheck.matches(g6 * Math.pow(2.0, 100 / 1200.0), 91, a4))
        val d7 = Tuning.midiToFrequency(Instrument.ALTO_SAX.writtenToConcertMidi(98))
        val frames = PitchTracker().push(AudioSignals.tone(d7, 0.5, listOf(1.0, 0.5)))
        assertTrue(frames.drop(6).mapNotNull { it.frequencyHz }.all { AltissimoCheck.matches(it, 98, a4) })
    }
}
