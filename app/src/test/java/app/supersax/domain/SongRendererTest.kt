package app.supersax.domain

import app.supersax.domain.audio.Fft
import app.supersax.domain.song.SongRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongRendererTest {
    private val sr = 44100

    private class ArraySource(val x: FloatArray, val rate: Int = 44100) : SongRenderer.Source {
        override fun channels() = 1
        override fun sampleRate() = rate
        override fun frames() = x.size.toLong()
        override fun sample(channel: Int, frame: Long) = if (frame in x.indices) x[frame.toInt()] else 0f
    }

    /** Renders [seconds] of output in 512-frame blocks. */
    private fun play(r: SongRenderer, seconds: Double): Pair<FloatArray, List<SongRenderer.Block>> {
        val blocks = (seconds * sr / 512).toInt()
        val out = FloatArray(blocks * 512)
        val buf = FloatArray(512)
        val info = mutableListOf<SongRenderer.Block>()
        for (b in 0 until blocks) { info += r.render(buf, 512); buf.copyInto(out, b * 512) }
        return out to info
    }

    @Test fun normalSpeedPassesTheSamplesThrough() {
        val x = FloatArray(sr * 2) { (0.4 * sin(2 * PI * 440 * it / sr)).toFloat() }
        val (y, _) = play(SongRenderer(ArraySource(x)), 1.5)
        for (i in y.indices) assertEquals(x[i], y[i])
    }

    @Test fun clicksLandOnTheBeatAtAnySpeed() {
        val silent = FloatArray(sr * 10)
        val r = SongRenderer(ArraySource(silent))
        r.setClicks(true, 1f, 120.0, 1000, 4)
        val (y, _) = play(r, 2.2)
        // First click at 1.000 s of the song = sample 44100 at normal speed, the next at 1.5 s.
        // (the click's first sample is sin(0) = 0, so the first non-zero sample is one later)
        assertTrue(y.indexOfFirst { abs(it) > 1e-6 } in sr..sr + 1)
        assertTrue(abs(y[sr * 3 / 2 + 5]) > 0.01)
        // At half speed the click at song time 1.0 s plays at output time 2.0 s.
        val half = SongRenderer(ArraySource(silent))
        half.setClicks(true, 1f, 120.0, 1000, 4)
        half.setSpeed(0.5f)
        val (z, info) = play(half, 2.3)
        val first = z.indexOfFirst { abs(it) > 1e-6 }
        assertTrue(abs(first - 2 * sr) <= 2, "first click at $first")
        assertEquals(256.0 / sr * 1000, info[0].songEndMs - info[0].songStartMs, 1e-6)
    }

    @Test fun loopAndStopAtTheEndOfAPart() {
        val x = FloatArray(sr * 10) { 0.1f }
        val loop = SongRenderer(ArraySource(x))
        loop.seek(1000); loop.setLoop(1000, 2000)
        play(loop, 1.5)
        assertTrue(loop.positionMs() in 1000.0..1600.0, "looped back: ${loop.positionMs()}")
        val stop = SongRenderer(ArraySource(x))
        stop.seek(1000); stop.setStopAt(2000, 1000)
        // The engine pauses on the block that reports "stopped": the position is back at the part start.
        val buf = FloatArray(512)
        var stopped = false
        repeat(200) { if (!stopped) stopped = stop.render(buf, 512).stopped }
        assertTrue(stopped)
        assertEquals(1000.0, stop.positionMs(), 1.0)
    }

    @Test fun keyShiftChangesThePitchNotTheTempo() {
        val x = FloatArray(sr * 6) { (0.3 * sin(2 * PI * 440 * it / sr)).toFloat() }
        val r = SongRenderer(ArraySource(x))
        r.setSemitones(12)
        val (y, info) = play(r, 3.0)
        // One octave up, same tempo: 1 s of output covers 1 s of the song.
        assertEquals(1000.0, info.last().songEndMs - info[info.size - 1 - (sr / 512)].songEndMs, 15.0)
        val n = 16384
        val re = DoubleArray(n) { y[sr + it] * (0.5 - 0.5 * kotlin.math.cos(2 * PI * it / n)) }
        val im = DoubleArray(n)
        Fft.transform(re, im)
        val k = (1 until n / 2).maxBy { hypot(re[it], im[it]) }
        assertEquals(880.0, k.toDouble() * sr / n, 4.0)
    }
}
