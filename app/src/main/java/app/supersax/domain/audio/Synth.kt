package app.supersax.domain.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Timbres for the tone synth: a clean sine, or a softer additive tone (a few decaying harmonics). */
enum class Timbre { SINE, SOFT }

/**
 * Pure sample generation for the tone synth, drone and melody playback. Phase is carried between
 * buffers so a continuous tone has no clicks; gain ramps avoid clicks at start and stop.
 */
class ToneVoice(private val sampleRate: Int, var frequencyHz: Double, var timbre: Timbre = Timbre.SINE, var gain: Float = 0.3f) {
    private var phase = 0.0
    private var level = 0f
    private var targetLevel = 1f
    private val rampPerSample = 1f / (0.02f * sampleRate) // 20 ms fades

    val finished: Boolean get() = targetLevel == 0f && level == 0f

    fun release() { targetLevel = 0f }

    /** Adds this voice into [out] (mixing). */
    fun mixInto(out: FloatArray, count: Int = out.size) {
        val step = 2 * PI * frequencyHz / sampleRate
        for (i in 0 until count) {
            level = when {
                level < targetLevel -> (level + rampPerSample).coerceAtMost(targetLevel)
                level > targetLevel -> (level - rampPerSample).coerceAtLeast(targetLevel)
                else -> level
            }
            if (level == 0f && targetLevel == 0f) continue
            out[i] += (sample(phase) * gain * level).toFloat()
            phase += step
            if (phase > 2 * PI) phase -= 2 * PI
        }
    }

    private fun sample(p: Double): Double = when (timbre) {
        Timbre.SINE -> sin(p)
        // Fundamental plus gently falling harmonics, normalised to about the same loudness.
        Timbre.SOFT -> (sin(p) + 0.45 * sin(2 * p) + 0.22 * sin(3 * p) + 0.10 * sin(4 * p) + 0.05 * sin(5 * p)) / 1.45
    }
}

/**
 * Sample-accurate metronome: clicks are written at exact sample positions computed from the tempo,
 * never timed with handlers. Beat 1 of each bar is accented; [subdivision] adds quieter ticks.
 */
class MetronomeGenerator(private val sampleRate: Int, bpm: Int, var beatsPerBar: Int = 4, var subdivision: Int = 1, var accent: Boolean = true) {
    var bpm: Int = bpm.coerceIn(MIN_BPM, MAX_BPM)
        set(value) { field = value.coerceIn(MIN_BPM, MAX_BPM) }

    /** Fractional sample position of the next tick, relative to the start of the next buffer. */
    private var nextTick = 0.0
    private var tickIndex = 0L
    private val clickLen = (0.025 * sampleRate).toInt()
    private val accentClick = click(2000.0, 0.9f)
    private val beatClick = click(1300.0, 0.7f)
    private val subClick = click(1300.0, 0.3f)
    /** Remaining part of a click that crossed the previous buffer boundary. */
    private var tail: FloatArray? = null
    private var tailPos = 0

    /** Positions (in samples, absolute) of every tick written so far: for tests. */
    val tickLog = mutableListOf<Long>()
    var logTicks = false
    private var written = 0L

    private fun click(freq: Double, amp: Float) = FloatArray(clickLen) { i ->
        (amp * sin(2 * PI * freq * i / sampleRate) * exp(-i / (0.004 * sampleRate))).toFloat()
    }

    fun samplesPerTick(): Double = sampleRate * 60.0 / bpm / subdivision

    fun reset() { nextTick = 0.0; tickIndex = 0; tail = null; written = 0; tickLog.clear() }

    /** Mixes clicks into [out]. */
    fun mixInto(out: FloatArray, count: Int = out.size) {
        tail?.let { t ->
            val n = minOf(t.size - tailPos, count)
            for (i in 0 until n) out[i] += t[tailPos + i]
            tailPos += n
            if (tailPos >= t.size) tail = null
        }
        while (nextTick < count) {
            val pos = nextTick.toInt()
            val inBeat = (tickIndex % subdivision).toInt()
            val beat = (tickIndex / subdivision) % beatsPerBar
            val c = when {
                inBeat != 0 -> subClick
                beat == 0L && accent -> accentClick
                else -> beatClick
            }
            if (logTicks) tickLog += written + pos
            val n = minOf(c.size, count - pos)
            for (i in 0 until n) out[pos + i] += c[i]
            if (n < c.size) { tail = c; tailPos = n }
            tickIndex++
            nextTick += samplesPerTick()
        }
        nextTick -= count
        written += count
    }

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 240
    }
}

/** A note of a melody to play back (concert MIDI or rest), with its length in beats. */
data class MelodyNote(val midi: Int?, val beats: Double)

/** Plays a melody with a [ToneVoice]-like envelope at a tempo, sample-accurately. */
class MelodyPlayer(private val sampleRate: Int, private val notes: List<MelodyNote>, bpm: Int, private val a4: Double, private val timbre: Timbre) {
    private val samplesPerBeat = sampleRate * 60.0 / bpm
    private var index = 0
    private var remaining = if (notes.isEmpty()) 0.0 else notes[0].beats * samplesPerBeat
    private var voice: ToneVoice? = startVoice(0)
    val finished: Boolean get() = index >= notes.size
    /** Index of the note being played (for highlighting). */
    val currentIndex: Int get() = index

    private fun startVoice(i: Int): ToneVoice? = notes.getOrNull(i)?.midi?.let {
        ToneVoice(sampleRate, app.supersax.domain.pitch.Tuning.midiToFrequency(it, a4), timbre, 0.3f)
    }

    fun mixInto(out: FloatArray, count: Int = out.size) {
        var pos = 0
        while (pos < count && !finished) {
            val n = minOf(remaining.toInt().coerceAtLeast(1), count - pos)
            val tmp = FloatArray(n)
            voice?.mixInto(tmp, n)
            for (i in 0 until n) out[pos + i] += tmp[i]
            // Release slightly before the next note so repeated notes are articulated.
            remaining -= n
            if (remaining < 0.03 * sampleRate) voice?.release()
            pos += n
            if (remaining <= 0) {
                index++
                if (!finished) {
                    remaining += notes[index].beats * samplesPerBeat
                    voice = startVoice(index)
                }
            }
        }
    }
}
