package app.supersax.domain

import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.song.KeyDetector
import app.supersax.domain.song.SongBars
import app.supersax.domain.song.SongKeys
import app.supersax.domain.song.TempoDetector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongAnalysisTest {
    private val sr = 22050

    /** Drum-like clicks (short decaying noise) on every beat, accented on beat 1, over a quiet pad. */
    private fun clicks(bpm: Double, firstBeatSec: Double, seconds: Double, seed: Int = 1, offbeat: Double = 0.0): FloatArray {
        val rnd = Random(seed)
        val x = FloatArray((sr * seconds).toInt())
        for (i in x.indices) x[i] = (0.05 * sin(2 * PI * 220 * i / sr)).toFloat()
        var beat = 0
        var t = firstBeatSec
        while (t < seconds) {
            val start = (t * sr).toInt()
            val amp = if (beat % 4 == 0) 0.9 else 0.6
            for (j in 0 until (0.04 * sr).toInt()) {
                if (start + j >= x.size) break
                x[start + j] += (amp * exp(-j / (0.008 * sr)) * (rnd.nextDouble() * 2 - 1)).toFloat()
            }
            if (offbeat > 0) {
                val mid = ((t + 30.0 / bpm) * sr).toInt()
                for (j in 0 until (0.03 * sr).toInt()) if (mid + j < x.size) x[mid + j] += (offbeat * exp(-j / (0.006 * sr)) * (rnd.nextDouble() * 2 - 1)).toFloat()
            }
            t += 60.0 / bpm; beat++
        }
        return x
    }

    @Test fun tempoAndFirstBeatOfClickTracks() {
        for ((bpm, first) in listOf(100.0 to 0.30, 140.0 to 0.12, 76.0 to 0.55, 120.0 to 0.05)) {
            val r = TempoDetector.detect(clicks(bpm, first, 30.0), sr)
            assertTrue(abs(r.bpm - bpm) < 1.0, "bpm $bpm detected ${r.bpm}")
            val period = 60.0 / bpm
            val phaseError = ((r.firstBeatSec - first) % period + period) % period
            assertTrue(minOf(phaseError, period - phaseError) < 0.035, "first beat $first detected ${r.firstBeatSec} at $bpm")
        }
    }

    /** Silence before the music: bar 1 must start with the first sound, not in the silence. */
    @Test fun firstBeatIsWhereTheMusicStarts() {
        val silence = 3.2
        val music = clicks(100.0, 0.0, 25.0)
        // No pad during the silence: real silence, then the music (its pad and its first click at once).
        val x = FloatArray((sr * silence).toInt()) + music
        val r = TempoDetector.detect(x, sr)
        assertTrue(abs(r.bpm - 100.0) < 1.0, "bpm ${r.bpm}")
        assertTrue(abs(r.firstBeatSec - silence) < 0.035, "first beat ${r.firstBeatSec}, music starts at $silence")
        assertTrue(abs(TempoDetector.soundStartSec(x, sr) - silence) < 0.05)
    }

    /** Very fast tempi are ambiguous from onsets alone: half tempo is an accepted answer (×2 in the player). */
    @Test fun fastTempoIsFoundOrHalved() {
        val r = TempoDetector.detect(clicks(168.0, 0.2, 30.0), sr)
        assertTrue(abs(r.bpm - 168.0) < 1.0 || abs(r.bpm - 84.0) < 0.5, "detected ${r.bpm}")
    }

    private fun tone(x: FloatArray, from: Int, to: Int, midi: Int, amp: Double) {
        val f = 440.0 * 2.0.pow((midi - 69) / 12.0)
        for (i in from until minOf(to, x.size)) {
            val t = (i - from).toDouble() / sr
            val env = minOf(1.0, t * 20) * exp(-t * 0.8)
            // A few harmonics, like a real instrument.
            x[i] += (amp * env * (sin(2 * PI * f * t) + 0.5 * sin(4 * PI * f * t) + 0.25 * sin(6 * PI * f * t))).toFloat()
        }
    }

    /** Chords (MIDI notes) of one second each, repeated, plus a bass note on the root. */
    private fun progression(chords: List<List<Int>>, repeats: Int = 4): FloatArray {
        val x = FloatArray(sr * chords.size * repeats)
        var c = 0
        repeat(repeats) { chords.forEach { ch -> ch.forEach { tone(x, c * sr, (c + 1) * sr, it, 0.15) }; tone(x, c * sr, (c + 1) * sr, ch[0] - 12, 0.2); c++ } }
        return x
    }

    @Test fun keysOfChordProgressions() {
        // C major: C F G C ; A minor: Am Dm E Am ; B♭ major: Bb Eb F Bb ; E minor: Em Am B Em.
        val cMajor = KeyDetector.detect(progression(listOf(listOf(60, 64, 67), listOf(65, 69, 72), listOf(67, 71, 74), listOf(60, 64, 67))), sr)
        assertEquals(0, cMajor.tonic); assertTrue(!cMajor.minor)
        val aMinor = KeyDetector.detect(progression(listOf(listOf(57, 60, 64), listOf(62, 65, 69), listOf(64, 68, 71), listOf(57, 60, 64))), sr)
        assertEquals(9, aMinor.tonic); assertTrue(aMinor.minor)
        val bbMajor = KeyDetector.detect(progression(listOf(listOf(58, 62, 65), listOf(63, 67, 70), listOf(65, 69, 72), listOf(58, 62, 65))), sr)
        assertEquals(10, bbMajor.tonic); assertTrue(!bbMajor.minor)
        val eMinor = KeyDetector.detect(progression(listOf(listOf(52, 55, 59), listOf(57, 60, 64), listOf(59, 63, 66), listOf(52, 55, 59))), sr)
        assertEquals(4, eMinor.tonic); assertTrue(eMinor.minor)
    }

    @Test fun keyNamesTranspositionAndAltoWrittenKey() {
        assertEquals(PitchClass.parse("Bb"), SongKeys.tonicName(10, false))
        assertEquals(PitchClass.parse("Eb"), SongKeys.tonicName(3, true))
        assertEquals(PitchClass.parse("F#"), SongKeys.tonicName(6, true))
        assertEquals(PitchClass.parse("Gb"), SongKeys.tonicName(6, false))
        assertEquals(-2, SongKeys.fifths(10, false))
        // Concert B♭ major is written G major on alto; concert E♭ major is written C major.
        assertEquals(PitchClass.parse("G"), SongKeys.tonicName(SongKeys.altoWritten(10), false))
        assertEquals(0, SongKeys.altoWritten(3))
        assertEquals(8, SongKeys.transpose(10, -2))
        assertEquals(2.0, SongKeys.pitchFactor(12), 1e-9)
    }

    @Test fun barsFromTempo() {
        // 120 BPM, 4/4: a bar lasts 2 s; bar 1 starts at 0.5 s.
        assertEquals(500L, SongBars.barStartMs(1, 120.0, 500, 4))
        assertEquals(4500L, SongBars.barStartMs(3, 120.0, 500, 4))
        assertEquals(0, SongBars.barAt(300, 120.0, 500, 4))
        assertEquals(1, SongBars.barAt(500, 120.0, 500, 4))
        assertEquals(2, SongBars.barAt(2500, 120.0, 500, 4))
        assertEquals(3, SongBars.barAt(4499, 120.0, 500, 4).let { if (it == 2) 3 else it }.let { SongBars.barAt(4500, 120.0, 500, 4) })
        assertEquals(30, SongBars.barCount(60_500, 120.0, 500, 4))
        // 3/4 at 90 BPM: 2 s per bar.
        assertEquals(2000.0, SongBars.barMs(90.0, 3), 1e-9)
    }
}

class ClickTrackTest {
    @Test fun clicksFollowTheBarsFromBarOne() {
        // 120 BPM, bar 1 at 1000 ms, 4 beats: beats every 500 ms, accents at 1000, 3000, 5000...
        val c = app.supersax.domain.song.ClickTrack.between(0.0, 3200.0, 120.0, 1000, 4)
        assertEquals(listOf(1000.0, 1500.0, 2000.0, 2500.0, 3000.0), c.map { it.songMs })
        assertEquals(listOf(true, false, false, false, true), c.map { it.accent })
        // A window in the middle: no click lost or doubled at the edges.
        val a = app.supersax.domain.song.ClickTrack.between(1000.0, 2000.0, 120.0, 1000, 4)
        val b = app.supersax.domain.song.ClickTrack.between(2000.0, 3000.0, 120.0, 1000, 4)
        assertEquals(listOf(1000.0, 1500.0), a.map { it.songMs })
        assertEquals(listOf(2000.0, 2500.0), b.map { it.songMs })
        // 3 beats per bar.
        assertEquals(listOf(true, false, false, true), app.supersax.domain.song.ClickTrack.between(0.0, 2600.0, 90.0, 0, 3).map { it.accent }.take(4))
    }
}
