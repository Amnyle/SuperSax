package app.supersax.domain

import app.supersax.domain.longtone.DroneChord
import app.supersax.domain.longtone.DroneInterval
import app.supersax.domain.longtone.DroneSound
import app.supersax.domain.longtone.HoldFirstLatch
import app.supersax.domain.longtone.LongToneMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LongToneTest {
    @Test fun intervalsPlaceThirdAndFifthAboveRoot() {
        assertEquals(listOf(61, 65, 68), DroneInterval.notes(61, setOf(DroneInterval.UNISON, DroneInterval.MAJOR_THIRD, DroneInterval.FIFTH)))
        assertEquals(listOf(68), DroneInterval.notes(61, setOf(DroneInterval.FIFTH)))
        assertEquals(listOf(61), DroneInterval.notes(61, emptySet()))
    }

    @Test fun holdFirstLatchesTheFirstSteadyNote() {
        val l = HoldFirstLatch(0.6, 0.15)
        var t = 0.0
        // A short blip on 60 is not enough, then a steady 62.
        repeat(5) { l.push(t, 60); t += 0.02 }
        repeat(20) { l.push(t, null); t += 0.02 }
        assertNull(l.latched)
        repeat(40) { l.push(t, 62); t += 0.02 }
        assertEquals(62, l.latched)
        // Later notes do not move it.
        repeat(40) { l.push(t, 64); t += 0.02 }
        assertEquals(62, l.latched)
        l.reset()
        assertNull(l.latched)
    }

    @Test fun meterCountsHoldAndInTuneShare() {
        val m = LongToneMeter(10.0, 0.3)
        var t = 0.0
        repeat(100) { i -> m.push(t, 60, if (i < 75) 3.0 else 20.0); t += 0.02 }
        assertTrue(abs(m.holdSec - 1.98) < 1e-6)
        assertEquals(75, m.inTunePercent)
        // A silence longer than the gap ends the hold; the best is kept.
        repeat(30) { m.push(t, null, 0.0); t += 0.02 }
        assertEquals(0.0, m.holdSec, 0.0)
        assertTrue(m.bestSec > 1.9)
        // A new note starts a new hold.
        m.push(t, 62, 0.0)
        assertEquals(100, m.inTunePercent)
    }

    @Test fun decibels() {
        assertEquals(0.0, LongToneMeter.decibels(1.0), 1e-9)
        assertEquals(-20.0, LongToneMeter.decibels(0.1), 1e-9)
        assertEquals(-90.0, LongToneMeter.decibels(0.0), 1e-9)
    }

    @Test fun droneChordSoundsAndFadesOut() {
        for (s in listOf(DroneSound.PURE, DroneSound.WARM, DroneSound.ORGAN, DroneSound.SAX)) {
            val d = DroneChord(44100, listOf(57, 61, 64), 440.0, s, 1f, null)
            val buf = FloatArray(44100)
            d.mixInto(buf, buf.size)
            val peak = buf.maxOf { abs(it) }
            assertTrue("$s peak $peak", peak in 0.05f..1.0f)
            d.release()
            val tail = FloatArray(44100)
            d.mixInto(tail, tail.size)
            assertTrue(d.finished)
        }
    }

    @Test fun saxDroneLoopsForMinutes() {
        val sax = app.supersax.domain.audio.Band.load { java.io.File("src/main/assets/sounds/$it.bin").inputStream() }.altoSax!!
        assertTrue((49..80).all { sax.loops(it) })
        val d = DroneChord(44100, listOf(61, 68), 440.0, DroneSound.SAX, 1f, sax)
        assertEquals(DroneSound.SAX, d.sound)
        val buf = FloatArray(44100)
        repeat(20) { buf.fill(0f); d.mixInto(buf, buf.size) }
        assertTrue(buf.maxOf { abs(it) } > 0.02f)
    }
}
