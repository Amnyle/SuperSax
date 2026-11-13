package app.supersax.domain

import app.supersax.domain.song.SongRenderer
import app.supersax.domain.take.ClockMap
import app.supersax.domain.take.CountIn
import app.supersax.domain.take.TakeMixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TakeMixerTest {
    private class Buf(val data: FloatArray, val rate: Int, val ch: Int) : SongRenderer.Source {
        override fun channels() = ch
        override fun sampleRate() = rate
        override fun frames() = (data.size / ch).toLong()
        override fun sample(channel: Int, frame: Long) = if (frame in 0 until frames()) data[(frame * ch + channel).toInt()] else 0f
    }

    private fun near(expected: Int, actual: Int, tol: Int) = assertTrue("expected $expected got $actual", abs(expected - actual) <= tol)

    private fun peakAt(out: FloatArray, from: Int, to: Int): Int {
        var best = from; var v = 0f
        for (i in from until to) { val a = abs(out[2 * i]); if (a > v) { v = a; best = i } }
        return best
    }

    @Test fun musicAndMicLineUpOnTheVideoTimeLine() {
        val rate = 44100
        // Music: a click at music frame 44100 (1 s into the music stream). The speaker plays music frame 0 at t = 2.0 s.
        val music = FloatArray(rate * 3 * 2).also { it[2 * rate] = 0.5f; it[2 * rate + 1] = 0.5f }
        val musicClock = ClockMap(rate, 0, 2_000_000_000L)
        // Mic (48 kHz): the player's note is captured at mic frame 96000 + 4800, mic frame 0 at t = 1.0 s → t = 3.1 s.
        val micRate = 48000
        val mic = FloatArray(micRate * 4).also { it[2 * micRate + 4800] = 0.5f; it[2 * micRate + 4801] = 0.5f }
        val micClock = ClockMap(micRate, 0, 1_000_000_000L)
        // Video starts at t = 0.5 s: the music click is at 2.5 s on the video, the note at 2.6 s.
        val m = TakeMixer(Buf(music, rate, 2), musicClock, Buf(mic, micRate, 1), micClock, 500_000_000L)
        m.setMicScale(1f)
        m.setBalance(0.5f)
        val n = rate * 3
        val out = FloatArray(n * 2)
        m.render(0, out, n)
        near((2.5 * rate).toInt(), peakAt(out, (2.4 * rate).toInt(), (2.55 * rate).toInt()), 1)
        near((2.6 * rate).toInt(), peakAt(out, (2.55 * rate).toInt(), (2.7 * rate).toInt()), 2)

        // Nudge the music 100 ms later: the click moves to 2.6 s too.
        m.setNudgeMs(100.0)
        m.setBalance(0f) // music only
        m.render(0, out, n)
        near((2.6 * rate).toInt(), peakAt(out, (2.4 * rate).toInt(), (2.7 * rate).toInt()), 1)
    }

    @Test fun balanceEnds() {
        val rate = 8000
        val music = FloatArray(rate).also { it.fill(0.2f) }
        val mic = FloatArray(rate).also { it.fill(0.3f) }
        val m = TakeMixer(Buf(music, rate, 1), ClockMap(rate, 0, 0), Buf(mic, rate, 1), ClockMap(rate, 0, 0), 0)
        val out = FloatArray(200)
        m.setBalance(0f); m.render(10, out, 100); assertEquals(0.2f, out[0], 1e-4f)
        m.setBalance(1f); m.render(10, out, 100); assertEquals(0.3f, out[0], 1e-4f)
        m.setBalance(0.5f); m.render(10, out, 100); assertEquals(0.5f, out[0], 1e-4f)
    }

    @Test fun loudPeaksAreRoundedNotClipped() {
        val rate = 8000
        val loud = FloatArray(rate).also { it.fill(0.9f) }
        val m = TakeMixer(Buf(loud, rate, 1), ClockMap(rate, 0, 0), Buf(loud, rate, 1), ClockMap(rate, 0, 0), 0)
        val out = FloatArray(200)
        m.render(10, out, 100)
        assertTrue(out.all { it <= 1f && it > 0.8f })
    }

    @Test fun micIsNormalised() {
        val quiet = FloatArray(1000).also { it[500] = 0.1f }
        assertEquals(7f, TakeMixer.normalise(Buf(quiet, 8000, 1)), 1e-4f)
        assertEquals(1f, TakeMixer.normalise(Buf(FloatArray(100), 8000, 1)), 0f)
    }

    @Test fun countInHasFourClicksAndTheSongStartsOnBeatFive() {
        val rate = 44100
        val c = CountIn.render(rate, 1, 120.0, 4)
        assertEquals(2 * rate, c.size)
        val onsets = (0 until 4).map { (it * 0.5 * rate).toInt() }
        onsets.forEach { at -> assertTrue((at until at + 200).any { abs(c[it]) > 0.1f }) }
        // Silence before the next beat.
        assertTrue((rate * 2 - 2000 until rate * 2).all { c[it] == 0f })
    }
}
