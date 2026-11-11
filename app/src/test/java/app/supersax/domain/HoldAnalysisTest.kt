package app.supersax.domain

import app.supersax.domain.AudioSignals.plus
import app.supersax.domain.audio.HoldAnalysis
import app.supersax.domain.audio.HoldConfig
import app.supersax.domain.audio.HoldReport
import app.supersax.domain.audio.HoldTracker
import app.supersax.domain.audio.PitchDirection
import app.supersax.domain.audio.PitchFrame
import app.supersax.domain.audio.PitchTracker
import app.supersax.domain.pitch.Tuning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoldAnalysisTest {
    private val a = 63 // concert E♭4 = written C5
    private val fa = Tuning.midiToFrequency(a)

    private fun frames(signal: FloatArray): List<PitchFrame> = PitchTracker().push(signal)

    @Test fun steadyNote() {
        val sig = AudioSignals.silence(0.3) + AudioSignals.tone(fa, 4.0) + AudioSignals.silence(0.5)
        val holds = HoldAnalysis.analyseAll(frames(sig))
        assertEquals(1, holds.size)
        val h = holds.single()
        assertEquals(a, h.refMidi)
        assertTrue(abs(h.meanCents) < 1.0)
        assertTrue(h.inTuneShare > 0.99)
        assertTrue(abs(h.driftCents) < 1.0)
        assertEquals(PitchDirection.CENTERED, h.direction)
        // 4 s of sound minus 0.3 s attack and 0.2 s release, give or take a window.
        assertTrue(abs(h.stableSec - 3.5) < 0.1, "stable ${h.stableSec}")
        assertTrue(h.trace.size in 33..37, "trace size ${h.trace.size}")
    }

    @Test fun driftingFlatNote() {
        val sig = AudioSignals.silence(0.2) + AudioSignals.drifting(fa, 6.0, 0.0, -30.0) + AudioSignals.silence(0.5)
        val h = HoldAnalysis.analyseAll(frames(sig), a).single()
        assertTrue(h.driftCents < -20, "drift ${h.driftCents}")
        assertTrue(abs(h.slopeCentsPerSec - (-5.0)) < 0.6, "slope ${h.slopeCentsPerSec}")
        assertEquals(PitchDirection.FLAT, h.direction)
        assertTrue(h.endCents < -20, "ends ${h.endCents}")
        assertTrue(h.minCents < -25 && h.maxCents > -5)
        // In tune (±10) only for the first third or so.
        assertTrue(h.inTuneShare in 0.2..0.45, "share ${h.inTuneShare}")
    }

    @Test fun wobblingNote() {
        val sig = AudioSignals.drifting(fa, 4.0, 0.0, 0.0, vibratoCents = 25.0, vibratoHz = 3.0) + AudioSignals.silence(0.5)
        val h = HoldAnalysis.analyseAll(frames(sig)).single()
        assertTrue(abs(h.meanCents) < 4.0, "mean ${h.meanCents}")
        assertTrue(h.maxCents > 18 && h.minCents < -18)
        assertTrue(h.inTuneShare < 0.6, "share ${h.inTuneShare}")
    }

    @Test fun attackAndReleaseAreTrimmed() {
        // A wildly sharp 250 ms attack and a flat 150 ms tail around a perfect note.
        val sig = AudioSignals.tone(fa * Math.pow(2.0, 80 / 1200.0), 0.25) + AudioSignals.tone(fa, 3.0) +
            AudioSignals.tone(fa * Math.pow(2.0, -80 / 1200.0), 0.15) + AudioSignals.silence(0.5)
        val h = HoldAnalysis.analyseAll(frames(sig), a).single()
        assertTrue(h.maxCents < 15 && h.minCents > -15, "min ${h.minCents} max ${h.maxCents}")
        assertTrue(h.inTuneShare > 0.9)
    }

    @Test fun silenceGivesNothing() {
        assertTrue(HoldAnalysis.analyseAll(frames(AudioSignals.silence(3.0))).isEmpty())
        // Too short to be a hold (100 ms).
        assertTrue(HoldAnalysis.analyseAll(frames(AudioSignals.silence(0.2) + AudioSignals.tone(fa, 0.1) + AudioSignals.silence(0.5))).isEmpty())
    }

    @Test fun twoHoldsAndLiveTracker() {
        val b = Tuning.midiToFrequency(65)
        val sig = AudioSignals.tone(fa, 2.0) + AudioSignals.silence(0.6) + AudioSignals.tone(b, 2.0) + AudioSignals.silence(0.6)
        val fr = frames(sig)
        val batch = HoldAnalysis.analyseAll(fr)
        assertEquals(listOf(63, 65), batch.map { it.refMidi })
        val tracker = HoldTracker()
        val live = fr.mapNotNull { tracker.push(it) }
        assertEquals(batch.map { it.refMidi }, live.map { it.refMidi })
        assertEquals(batch[0].stableSec, live[0].stableSec, 1e-9)
    }

    @Test fun recomputeFromStoredTrace() {
        val sig = AudioSignals.drifting(fa, 5.0, 0.0, -20.0) + AudioSignals.silence(0.5)
        val h = HoldAnalysis.analyseAll(frames(sig), a, HoldConfig().withToleranceCents(10.0)).single()
        val at10 = HoldReport.inTuneShareAt(h.trace, 10.0)
        val at30 = HoldReport.inTuneShareAt(h.trace, 30.0)
        assertTrue(abs(at10 - h.inTuneShare) < 0.1)
        assertEquals(1.0, at30)
        assertNull(HoldAnalysis.analyse(emptyList()))
    }
}
