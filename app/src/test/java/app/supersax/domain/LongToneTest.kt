package app.supersax.domain

import app.supersax.domain.longtone.DroneChord
import app.supersax.domain.longtone.DroneInterval
import app.supersax.domain.longtone.DroneSound
import app.supersax.domain.longtone.HoldFirstLatch
import app.supersax.domain.longtone.LongToneMeter
import app.supersax.domain.longtone.LongTonePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.test.assertTrue as check
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

    @Test fun followLatchMovesToANewSteadyNote() {
        val l = HoldFirstLatch(0.6, 0.15)
        l.setFollow(true)
        var t = 0.0
        repeat(40) { l.push(t, 62); t += 0.02 }
        assertEquals(62, l.latched)
        // A quick passing note does not move it; a held one does.
        repeat(10) { l.push(t, 64); t += 0.02 }
        assertEquals(62, l.latched)
        repeat(40) { l.push(t, 64); t += 0.02 }
        assertEquals(64, l.latched)
    }

    @Test fun planWorksMoreOnTheNotesHeldWorst() {
        val c = LongTonePlan.candidates(62, 74, emptyList())
        assertEquals(listOf(62, 64, 65, 67, 69, 71, 72, 74), c)
        // C5 (72) held badly, every other note held well.
        val holds = c.flatMap { m ->
            (0 until 3).map { i -> if (m == 72) LongTonePlan.Hold(m, i.toLong(), 2.0, 0.3) else LongTonePlan.Hold(m, i.toLong(), 9.0, 0.95) }
        }
        assertTrue(LongTonePlan.struggle(72, holds) > 0.6)
        assertTrue(LongTonePlan.struggle(64, holds) < 0.1)
        assertEquals(-1.0, LongTonePlan.struggle(66, holds), 0.0)
        val rnd = java.util.Random(1)
        val counts = mutableMapOf<Int, Int>()
        repeat(4000) { counts.merge(LongTonePlan.next(c, holds, emptyList(), rnd).writtenMidi, 1, Int::plus) }
        val c5 = counts[72]!!
        // The struggling note comes up several times as often as any other, but every note still comes up.
        check(c.all { (counts[it] ?: 0) > 100 }, "$counts")
        check(c.filter { it != 72 }.all { c5 > 3 * counts[it]!! }, "$counts")
        // Never the same note twice in a row.
        assertTrue((0 until 50).none { LongTonePlan.next(c, holds, listOf(72), rnd).writtenMidi == 72 })
    }

    @Test fun planAddsAccidentalsAlreadyPlayed() {
        val c = LongTonePlan.candidates(62, 74, listOf(LongTonePlan.Hold(66, 0, 5.0, 0.8), LongTonePlan.Hold(80, 0, 5.0, 0.8)))
        assertTrue(66 in c)
        assertTrue(80 !in c)
    }
}
