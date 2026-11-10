package app.saxpractice.domain

import app.saxpractice.domain.AudioSignals.SR
import app.saxpractice.domain.audio.PitchTracker
import app.saxpractice.domain.audio.Yin
import app.saxpractice.domain.pitch.Tuning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YinTest {
    private val yin = Yin()
    private val detunes = listOf(-50.0, -33.0, -12.0, 0.0, 7.0, 25.0, 49.0)

    private fun check(label: String, toleranceCents: Double, make: (Double) -> FloatArray) {
        var worst = 0.0
        for (midi in 49..89) { // concert D♭3 (138.6 Hz) .. F6 (1397 Hz)
            for (d in detunes) {
                val f = Tuning.midiToFrequency(midi + d / 100.0)
                val signal = make(f)
                val r = yin.detect(signal, 1000)
                val detected = r.frequencyHz
                assertTrue(detected != null, "$label: no pitch for $f Hz")
                val err = Tuning.cents(detected, f)
                assertTrue(abs(err) < 600, "$label: octave error at $f Hz: detected $detected")
                worst = maxOf(worst, abs(err))
                assertTrue(abs(err) <= toleranceCents, "$label: $f Hz detected $detected (${"%.2f".format(err)} cents)")
            }
        }
        println("$label worst error ${"%.3f".format(worst)} cents")
    }

    @Test fun pureSines() = check("sine", 2.0) { AudioSignals.tone(it, 0.2) }

    /**
     * Noise goes through the real pipeline: [PitchTracker] frames, median-smoothed over 5 hops, as the
     * tuner shows them. Single raw frames of the lowest notes wobble more (see docs/DECISIONS.md).
     */
    private fun noisy(noise: Double, tolerance: Double): Double {
        var worst = 0.0
        for (midi in 49..89) for (d in detunes) {
            val f = Tuning.midiToFrequency(midi + d / 100.0)
            val frames = PitchTracker(yin).push(AudioSignals.tone(f, 0.3, noise = noise, seed = midi * 100 + d.toInt()))
            val shown = frames.drop(6).mapNotNull { it.frequencyHz }
            assertTrue(shown.isNotEmpty(), "no pitch for $f")
            shown.forEach {
                val e = abs(Tuning.cents(it, f)); worst = maxOf(worst, e)
                assertTrue(e <= tolerance, "noise $noise: $f Hz shown $it (${"%.2f".format(e)} cents)")
            }
        }
        return worst
    }

    /** About 24 dB SNR (a sax close to the phone): a few cents. */
    @Test fun sinesWithModerateNoise() = println("sine+noise 24dB worst error ${"%.3f".format(noisy(0.04, 5.0))} cents")

    /** About 18 dB SNR (harsh): no octave errors, still a few cents. */
    @Test fun sinesWithHeavyNoise() = println("sine+noise 18dB worst error ${"%.3f".format(noisy(0.08, 5.0))} cents")

    @Test fun harmonicRich() = check("harmonic", 2.0) { AudioSignals.tone(it, 0.2, listOf(1.0, 0.7, 0.5, 0.35, 0.25, 0.18, 0.12, 0.08)) }

    /** Sax-like: the second harmonic is stronger than the fundamental. Must not jump an octave up. */
    @Test fun strongSecondHarmonic() = check("strong 2nd", 3.0) { AudioSignals.tone(it, 0.2, listOf(0.6, 1.0, 0.5, 0.3), noise = 0.02) }

    @Test fun confidenceHighForTonesAndSilenceUnvoiced() {
        val r = yin.detect(AudioSignals.tone(440.0, 0.1), 0)
        assertTrue(r.confidence > 0.9)
        assertNull(yin.detect(AudioSignals.silence(0.1), 0).frequencyHz)
        val noise = AudioSignals.tone(440.0, 0.1, amp = 0.0, noise = 0.3)
        assertNull(yin.detect(noise, 0).frequencyHz, "white noise should not give a pitch")
    }

    @Test fun trackerHopsAndSmooths() {
        val tracker = PitchTracker(yin)
        val signal = AudioSignals.tone(Tuning.midiToFrequency(63), 1.0)
        val frames = signal.toList().chunked(300).flatMap { tracker.push(it.toFloatArray()) }
        // (44100 - 3072) / 512 + 1 frames, one every hop.
        assertTrue(frames.size in 78..82, "frames=${frames.size}")
        val dt = frames[1].timeSec - frames[0].timeSec
        assertTrue(abs(dt - 512.0 / SR) < 1e-9)
        frames.forEach { assertTrue(abs(Tuning.cents(it.frequencyHz!!, Tuning.midiToFrequency(63))) < 1.0) }
    }
}
