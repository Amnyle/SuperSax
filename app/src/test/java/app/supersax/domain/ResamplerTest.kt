package app.supersax.domain

import app.supersax.audio.AudioDecoder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The decoder's resampler (pure part of the Android decoder): length and waveform after 44.1 kHz → 22.05 kHz. */
class ResamplerTest {
    @Test fun halvesTheRateAndKeepsTheWave() {
        val r = AudioDecoder.Resampler(44100, AudioDecoder.RATE, Int.MAX_VALUE)
        val n = 44100
        for (i in 0 until n) r.push(sin(2 * PI * 440 * i / 44100.0).toFloat())
        val out = r.result()
        assertTrue(abs(out.size - n / 2) <= 1, "${out.size}")
        for (i in 100 until 200) assertEquals(sin(2 * PI * 440 * i / 22050.0), out[i].toDouble(), 0.02)
    }

    @Test fun stopsAtTheLimit() {
        val r = AudioDecoder.Resampler(22050, AudioDecoder.RATE, 1000)
        repeat(5000) { r.push(0f) }
        assertTrue(r.full())
        assertEquals(1000, r.result().size)
    }
}
