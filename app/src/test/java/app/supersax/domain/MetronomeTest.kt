package app.supersax.domain

import app.supersax.domain.audio.MelodyNote
import app.supersax.domain.audio.MelodyPlayer
import app.supersax.domain.audio.MetronomeGenerator
import app.supersax.domain.audio.PitchTracker
import app.supersax.domain.audio.Timbre
import app.supersax.domain.audio.ToneVoice
import app.supersax.domain.pitch.Tuning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetronomeTest {
    @Test fun ticksAreSampleAccurateAcrossBuffers() {
        for (bpm in listOf(30, 60, 97, 120, 177, 240)) {
            val m = MetronomeGenerator(44_100, bpm).apply { logTicks = true }
            val buf = FloatArray(733) // odd buffer size on purpose
            repeat((44_100 * 60) / 733) { buf.fill(0f); m.mixInto(buf) }
            val spt = 44_100 * 60.0 / bpm
            m.tickLog.forEachIndexed { i, pos -> assertEquals((i * spt).toLong(), pos, "bpm $bpm tick $i") }
            assertTrue(abs(m.tickLog.size - bpm) <= 1, "bpm $bpm ticks ${m.tickLog.size}")
        }
    }

    @Test fun accentAndSubdivisions() {
        val m = MetronomeGenerator(44_100, 60, beatsPerBar = 4, subdivision = 2)
        val out = FloatArray(44_100 * 4)
        m.mixInto(out)
        fun peak(at: Int) = (at until at + 500).maxOf { abs(out[it]) }
        val accent = peak(0); val beat = peak(44_100); val sub = peak(22_050)
        assertTrue(accent > beat && beat > sub, "accent $accent beat $beat sub $sub")
        assertEquals(30, MetronomeGenerator(44_100, 10).bpm)
        assertEquals(240, MetronomeGenerator(44_100, 400).bpm)
    }

    @Test fun synthToneHasTheRightPitch() {
        for (timbre in Timbre.entries) {
            val f = Tuning.midiToFrequency(57)
            val v = ToneVoice(44_100, f, timbre)
            val out = FloatArray(44_100)
            v.mixInto(out)
            val frames = PitchTracker().push(out)
            val err = frames.drop(5).mapNotNull { it.frequencyHz }.map { Tuning.cents(it, f) }
            assertTrue(err.all { abs(it) < 1.0 }, "$timbre")
        }
    }

    @Test fun melodyPlaysEveryNote() {
        val notes = listOf(MelodyNote(60, 1.0), MelodyNote(null, 1.0), MelodyNote(64, 2.0))
        val p = MelodyPlayer(44_100, notes, 120, 440.0, Timbre.SINE)
        val out = FloatArray(44_100 * 3)
        p.mixInto(out)
        assertTrue(p.finished)
        val frames = PitchTracker().push(out)
        val at = { t: Double -> frames.minBy { abs(it.timeSec - t) }.frequencyHz }
        assertTrue(abs(Tuning.cents(at(0.25)!!, Tuning.midiToFrequency(60))) < 2)
        assertEquals(null, at(0.75))
        assertTrue(abs(Tuning.cents(at(1.5)!!, Tuning.midiToFrequency(64))) < 2)
    }
}
