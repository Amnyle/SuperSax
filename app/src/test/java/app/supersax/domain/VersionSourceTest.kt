package app.supersax.domain

import app.supersax.audio.VersionSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionSourceTest {
    /** A synthetic "song": bass and drum hits (kept in the version) plus a melody (the "sax", removed). */
    private class Song(val rate: Int, val lateFrames: Int, val withSax: Boolean, val seconds: Int = 40) : VersionSource.Pcm {
        override fun channels() = 1
        override fun sampleRate() = rate
        override fun frames() = rate.toLong() * seconds
        override fun sample(channel: Int, frame: Long): Float {
            val f = frame - lateFrames
            if (f < 0 || f >= frames()) return 0f
            val t = f.toDouble() / rate
            val beat = t % 0.5
            val drum = if (beat < 0.03) (1 - beat / 0.03) * sin(2 * PI * 90 * t) else 0.0
            val bass = 0.3 * sin(2 * PI * 55 * t) * (if ((t * 2).toInt() % 4 == 0) 1.0 else 0.5)
            val sax = if (withSax) 0.3 * sin(2 * PI * (440 + 40 * sin(t)) * t) else 0.0
            return (0.5 * drum + bass + sax).toFloat()
        }
    }

    @Test fun findsTheMp3Delay() {
        val main = Song(44100, 0, true)
        val late = Song(44100, 1105, false)
        assertEquals(1105.0, VersionSource.align(main, late), 3.0)
        val otherRate = Song(48000, 1200, false)
        assertEquals(1200.0, VersionSource.align(main, otherRate), 6.0)
    }

    @Test fun switchingHasNoJump() {
        val main = Song(44100, 0, true)
        val v = Song(44100, 1105, false)
        val src = VersionSource(main)
        val off = VersionSource.align(main, v)
        assertEquals(1105.0, off, 3.0)
        val idx = src.add(v, off)
        // Play 0.5 s, switch, play on: the largest step between samples stays like the music's own.
        var prev = src.sample(0, 0)
        var maxStep = 0f
        val at = 44100L * 5
        for (f in 1 until 44100L * 6) {
            if (f == at) src.select(idx, at)
            val s = src.sample(0, f)
            if (f > at - 100) maxStep = maxOf(maxStep, abs(s - prev))
            prev = s
        }
        var natural = 0f
        var p2 = main.sample(0, 44100L * 5 - 100)
        for (f in 44100L * 5 - 99 until 44100L * 6) { val s = main.sample(0, f); natural = maxOf(natural, abs(s - p2)); p2 = s }
        assertTrue(maxStep < natural * 1.6f, "switch step $maxStep vs natural $natural")
        // After the fade, the version is heard, in time.
        val f = 44100L * 6 + 7000
        assertEquals(v.sample(0, f + Math.round(off)), src.sample(0, f), 1e-3f)
    }
}
