package app.supersax.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.supersax.domain.audio.MelodyNote
import app.supersax.domain.audio.MelodyPlayer
import app.supersax.domain.audio.MetronomeGenerator
import app.supersax.domain.audio.Timbre
import app.supersax.domain.audio.ToneVoice
import app.supersax.domain.pitch.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the output engine is doing (for the UI). */
data class OutputState(
    val metronomeOn: Boolean = false,
    val bpm: Int = 80,
    val beatsPerBar: Int = 4,
    val subdivision: Int = 1,
    val accent: Boolean = true,
    val droneMidi: Int? = null,
    val melodyPlaying: Boolean = false,
    val melodyIndex: Int = -1,
    val error: String? = null,
)

/**
 * One streaming [AudioTrack] that mixes the metronome, the drone, single reference tones and melody
 * playback. All timing comes from sample counts in the pure generators (no Handler timing). The
 * thread runs only while something is sounding.
 */
class AudioEngine {
    private val sampleRate = 44_100
    private val lock = Any()
    private val _state = MutableStateFlow(OutputState())
    val state: StateFlow<OutputState> = _state.asStateFlow()

    private var metronome: MetronomeGenerator? = null
    private var drone: ToneVoice? = null
    private val tones = mutableListOf<Pair<ToneVoice, Long>>() // voice, samples left before release
    private var melody: MelodyPlayer? = null
    private var onMelodyDone: (() -> Unit)? = null

    var a4: Double = Tuning.DEFAULT_A4
    var timbre: Timbre = Timbre.SINE

    @Volatile private var thread: Thread? = null
    @Volatile private var running = false

    fun setMetronome(on: Boolean, bpm: Int = _state.value.bpm, beatsPerBar: Int = _state.value.beatsPerBar, subdivision: Int = _state.value.subdivision, accent: Boolean = _state.value.accent) {
        synchronized(lock) {
            if (on) {
                val m = metronome
                if (m == null) metronome = MetronomeGenerator(sampleRate, bpm, beatsPerBar, subdivision, accent)
                else { m.bpm = bpm; m.beatsPerBar = beatsPerBar; m.subdivision = subdivision; m.accent = accent }
            } else metronome = null
        }
        _state.update { it.copy(metronomeOn = on, bpm = bpm.coerceIn(MetronomeGenerator.MIN_BPM, MetronomeGenerator.MAX_BPM), beatsPerBar = beatsPerBar, subdivision = subdivision, accent = accent) }
        ensureRunning()
    }

    /** Starts (or changes) the drone on a concert MIDI note; null stops it. */
    fun setDrone(concertMidi: Int?) {
        synchronized(lock) {
            drone?.release()
            drone = concertMidi?.let { ToneVoice(sampleRate, Tuning.midiToFrequency(it, a4), timbre, 0.25f) }
        }
        _state.update { it.copy(droneMidi = concertMidi) }
        ensureRunning()
    }

    /** Plays a reference tone (concert MIDI) for [millis]. */
    fun playTone(concertMidi: Int, millis: Long = 1500) {
        synchronized(lock) {
            tones += ToneVoice(sampleRate, Tuning.midiToFrequency(concertMidi, a4), timbre, 0.3f) to millis * sampleRate / 1000
        }
        ensureRunning()
    }

    /** Plays a melody with the tone synth at [bpm] (quarter-note beats). */
    fun playMelody(notes: List<MelodyNote>, bpm: Int, onDone: () -> Unit = {}) {
        synchronized(lock) {
            melody = MelodyPlayer(sampleRate, notes, bpm, a4, timbre)
            onMelodyDone = onDone
        }
        _state.update { it.copy(melodyPlaying = true, melodyIndex = 0) }
        ensureRunning()
    }

    fun stopMelody() {
        synchronized(lock) { melody = null; onMelodyDone = null }
        _state.update { it.copy(melodyPlaying = false, melodyIndex = -1) }
    }

    fun stopAll() {
        synchronized(lock) { metronome = null; drone = null; tones.clear(); melody = null }
        _state.update { OutputState(bpm = it.bpm, beatsPerBar = it.beatsPerBar, subdivision = it.subdivision, accent = it.accent) }
    }

    private fun busy(): Boolean = synchronized(lock) { metronome != null || drone != null || tones.isNotEmpty() || melody != null }

    private fun ensureRunning() {
        if (running || !busy()) return
        running = true
        thread = Thread(::loop, "sax-audio-out").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun loop() {
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBytes, 4096 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
            running = false
            return
        }
        val buf = FloatArray(512)
        try {
            track.play()
            _state.update { it.copy(error = null) }
            var idleBuffers = 0
            while (running) {
                buf.fill(0f)
                var done: (() -> Unit)? = null
                synchronized(lock) {
                    metronome?.mixInto(buf)
                    drone?.mixInto(buf)
                    if (drone?.finished == true) drone = null
                    val it = tones.listIterator()
                    while (it.hasNext()) {
                        val (v, left) = it.next()
                        v.mixInto(buf)
                        val nl = left - buf.size
                        if (nl <= 0) v.release()
                        if (v.finished) it.remove() else it.set(v to nl)
                    }
                    melody?.let { m ->
                        m.mixInto(buf)
                        if (_state.value.melodyIndex != m.currentIndex) _state.update { s -> s.copy(melodyIndex = m.currentIndex) }
                        if (m.finished) { melody = null; done = onMelodyDone; onMelodyDone = null }
                    }
                }
                done?.let { _state.update { s -> s.copy(melodyPlaying = false, melodyIndex = -1) }; it() }
                for (i in buf.indices) buf[i] = buf[i].coerceIn(-1f, 1f)
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                idleBuffers = if (busy()) 0 else idleBuffers + 1
                if (idleBuffers > 20) running = false
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
        } finally {
            runCatching { track.stop() }
            track.release()
            running = false
            if (busy()) ensureRunning()
        }
    }
}
