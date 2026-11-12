package app.supersax.domain

import app.supersax.domain.audio.Band
import app.supersax.domain.audio.SampledInstrument
import app.supersax.domain.solo.Blues
import app.supersax.domain.solo.Groove
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SamplesTest {
    private val band = Band.load { File("src/main/assets/sounds/$it.bin").inputStream() }

    /** Strongest frequency between lo and hi Hz (Goertzel scan in 0.5 Hz steps). */
    private fun peakHz(x: FloatArray, rate: Int, lo: Double, hi: Double): Double {
        var best = 0.0; var bestF = lo
        var f = lo
        val n = minOf(x.size, rate)
        while (f <= hi) {
            var re = 0.0; var im = 0.0
            for (i in 0 until n) { val w = 2 * PI * f * i / rate; re += x[i] * cos(w); im += x[i] * sin(w) }
            val p = re * re + im * im
            if (p > best) { best = p; bestF = f }
            f += 0.5
        }
        return bestF
    }

    private fun note(inst: SampledInstrument, midi: Double): FloatArray {
        val out = FloatArray(32000 * 2)
        inst.play(out, 32000, 0, midi, 0.8, 1.5, 0.2)
        return out
    }

    @Test fun everyInstrumentLoads() {
        listOf(band.piano, band.epiano, band.bass, band.ebass, band.altoSax, band.jazzKit, band.brushKit, band.funkKit).forEach { assertNotNull(it) }
        assertTrue(band.jazzKit!!.has(51) && band.jazzKit!!.has(36) && band.brushKit!!.has(40))
    }

    @Test fun pitchesAreRight() {
        // A4 on the piano and alto sax, A2 on the bass, a note between two samples on the piano.
        assertEquals(440.0, peakHz(note(band.piano!!, 69.0), 32000, 400.0, 480.0), 3.0)
        assertEquals(440.0, peakHz(note(band.altoSax!!, 69.0), 32000, 400.0, 480.0), 3.0)
        assertEquals(110.0, peakHz(note(band.bass!!, 45.0), 32000, 95.0, 125.0), 2.0)
        assertEquals(110.0, peakHz(note(band.ebass!!, 45.0), 32000, 95.0, 125.0), 2.0)
        assertEquals(523.3, peakHz(note(band.altoSax!!, 72.0), 32000, 490.0, 560.0), 4.0)
        assertEquals(277.2, peakHz(note(band.piano!!, 61.0), 32000, 250.0, 300.0), 3.0)
    }

    @Test fun louderNotesUseLouderRecordings() {
        val kit = band.jazzKit!!
        val soft = FloatArray(16000); val loud = FloatArray(16000)
        kit.play(soft, 32000, 0, 38.0, 1.0, 0.5, 0.05, 0.2, SampledInstrument.Expression.NONE)
        kit.play(loud, 32000, 0, 38.0, 1.0, 0.5, 0.05, 0.95, SampledInstrument.Expression.NONE)
        assertTrue(loud.maxOf { abs(it) } > soft.maxOf { abs(it) } * 1.1f, "the loud snare recording is louder")
    }

    @Test fun saxHoldsLongNotesAndBends() {
        val out = FloatArray(32000 * 4)
        band.altoSax!!.play(out, 32000, 0, 69.0, 0.8, 3.5, 0.1, 0.8, SampledInstrument.Expression(50.0, 0.07, 12.0, 0.3, 0.0, 0.0))
        val late = out.copyOfRange(32000 * 3, 32000 * 3 + 3200)
        assertTrue(late.maxOf { abs(it) } > 0.05f, "a looped note still sounds after 3 s")
        assertEquals(440.0, peakHz(out.copyOfRange(16000, out.size), 32000, 400.0, 480.0), 4.0)
    }

    @Test fun stereoMixIsSeamlessAndBounded() {
        val st = Groove.Style.JAZZ_251
        val mix = Groove.render(st, 46, 120.0, 32000, null, 70, band)
        val out = ShortArray(1024)
        var pos = 0
        var maxStep = 0
        var prev = 0
        repeat(mix.frames() / 512 + 4) {
            val n = minOf(512, mix.frames() - pos)
            mix.mixStereo(out, n, pos, floatArrayOf(1f, 1f, 1f, 1f))
            for (i in 0 until n) { maxStep = maxOf(maxStep, abs(out[2 * i] - prev)); prev = out[2 * i].toInt() }
            pos += n; if (pos >= mix.frames()) pos = 0
        }
        assertTrue(maxStep < 20000, "no clicks: $maxStep")
    }

    @Test fun backingWithBandHasAllPartsAndExportsWav() {
        val dir = File("build/audio").apply { mkdirs() }
        for (st in Groove.Style.entries) {
            val calls = (0 until st.bars() / 4).map { Blues.callPhrase(it.toLong()) }
            val mix = Groove.render(st, 46, st.defaultBpm.toDouble(), 32000, calls, 70, band)
            assertEquals(32000 * Groove.loopSeconds(st, st.defaultBpm.toDouble()) * Groove.choruses(st), mix.frames().toDouble(), 2.0)
            for (p in Groove.Part.entries) assertTrue(mix.stems[p.ordinal].any { abs(it) > 0.01f }, "$st $p is silent")
            val out = mix.mix(floatArrayOf(1f, 1f, 1f, 1f))
            assertTrue(out.all { abs(it) <= 1f })
            wav(File(dir, "${st.name.lowercase()}.wav"), out, 32000)
            val stereo = ShortArray(mix.frames() * 2)
            mix.mixStereo(stereo, mix.frames(), 0, floatArrayOf(1f, 1f, 1f, 1f))
            wav2(File(dir, "${st.name.lowercase()}-stereo.wav"), stereo, 32000)
        }
    }

    private fun wav2(f: File, x: ShortArray, rate: Int) {
        val b = java.nio.ByteBuffer.allocate(44 + x.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + x.size * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2)
            .putInt(rate).putInt(rate * 4).putShort(4).putShort(16).put("data".toByteArray()).putInt(x.size * 2)
        x.forEach { b.putShort(it) }
        f.writeBytes(b.array())
    }

    private fun wav(f: File, x: FloatArray, rate: Int) {
        val b = java.nio.ByteBuffer.allocate(44 + x.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + x.size * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(x.size * 2)
        x.forEach { b.putShort((it * 32767).toInt().coerceIn(-32768, 32767).toShort()) }
        f.writeBytes(b.array())
    }
}
