package app.supersax.domain

import app.supersax.domain.audio.Fft
import app.supersax.domain.song.TimeStretcher
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeStretcherTest {
    private val sr = 44100

    private fun tones(seconds: Double, vararg freqs: Double): FloatArray =
        FloatArray((sr * seconds).toInt()) { i -> freqs.sumOf { f -> 0.3 * sin(2 * PI * f * i / sr) }.toFloat() }

    /** Stretches mono [x] by [stretch] (output duration / input duration). */
    private fun stretch(x: FloatArray, stretch: Double): FloatArray {
        val ts = TimeStretcher(1)
        val src = TimeStretcher.Source { _, f -> if (f in x.indices) x[f.toInt()] else 0f }
        val ha = TimeStretcher.HOP / stretch
        val hops = ((x.size - TimeStretcher.N) / ha).toInt()
        val out = Array(1) { FloatArray(hops * TimeStretcher.HOP) }
        var pos = 0.0
        for (h in 0 until hops) { ts.synthesize(src, pos, out, h * TimeStretcher.HOP); pos += ha }
        return out[0]
    }

    /** The strongest frequencies (Hz) in the middle of [x]. */
    private fun peaks(x: FloatArray, count: Int): List<Double> {
        val n = 16384
        val off = x.size / 2 - n / 2
        val re = DoubleArray(n) { x[off + it] * (0.5 - 0.5 * kotlin.math.cos(2 * PI * it / n)) }
        val im = DoubleArray(n)
        Fft.transform(re, im)
        val mag = DoubleArray(n / 2) { hypot(re[it], im[it]) }
        val found = mutableListOf<Int>()
        repeat(count) {
            val k = mag.indices.filter { i -> found.none { abs(it - i) < 20 } }.maxBy { mag[it] }
            found += k
        }
        return found.map { it.toDouble() * sr / n }.sorted()
    }

    private fun rms(x: FloatArray, from: Int, to: Int) = sqrt((from until to).sumOf { x[it].toDouble() * x[it] } / (to - from))

    @Test fun slowerKeepsThePitchAndTheLoudness() {
        val x = tones(3.0, 440.0)
        val y = stretch(x, 1.25)
        // Duration scales with the stretch.
        assertTrue(abs(y.size - x.size * 1.25) < x.size * 0.05, "${y.size} vs ${x.size}")
        assertEquals(440.0, peaks(y, 1)[0], 3.0)
        assertEquals(rms(x, sr / 2, sr), rms(y, sr, 2 * sr), 0.03)
    }

    @Test fun chordsKeepEveryNote() {
        val y = stretch(tones(3.0, 440.0, 660.0), 1.5)
        val p = peaks(y, 2)
        assertEquals(440.0, p[0], 3.0)
        assertEquals(660.0, p[1], 3.0)
        assertTrue(y.none { it.isNaN() })
    }

    @Test fun fasterKeepsThePitch() {
        val y = stretch(tones(3.0, 523.25), 0.8)
        assertEquals(523.25, peaks(y, 1)[0], 3.0)
    }

    @Test fun unchangedSpeedReproducesTheInput() {
        val x = tones(2.0, 330.0, 495.0)
        val y = stretch(x, 1.0)
        // After the first frame the output matches the input (same positions).
        var num = 0.0; var dx = 0.0; var dy = 0.0
        for (i in 8192 until 40000) { num += x[i] * y[i]; dx += x[i] * x[i].toDouble(); dy += y[i] * y[i].toDouble() }
        assertTrue(num / sqrt(dx * dy) > 0.99, "correlation ${num / sqrt(dx * dy)}")
    }
}
