package app.saxpractice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.saxpractice.domain.audio.PitchFrame
import app.saxpractice.domain.audio.PitchTracker
import app.saxpractice.domain.audio.Yin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Microphone state, shown to the user (errors are visible and recoverable). */
sealed interface MicState {
    data object Idle : MicState
    data object Listening : MicState
    /** The recorder could not start (mic busy, no permission...). */
    data class Failed(val reason: Reason) : MicState
    enum class Reason { NO_PERMISSION, BUSY, ERROR }
}

/**
 * Captures the microphone with [AudioRecord] and turns it into pitch frames with the pure YIN
 * [PitchTracker]. Audio is analysed live and never stored.
 */
class MicCapture {
    private val sampleRate = 44_100
    private val _frames = MutableSharedFlow<PitchFrame>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val frames: SharedFlow<PitchFrame> = _frames.asSharedFlow()
    private val _state = MutableStateFlow<MicState>(MicState.Idle)
    val state: StateFlow<MicState> = _state.asStateFlow()
    /** Raw samples for the experimental scale checker (optional listener). */
    @Volatile var rawListener: ((FloatArray, Int) -> Unit)? = null

    @Volatile private var thread: Thread? = null
    @Volatile private var running = false
    private var users = 0

    /** Starts listening (reference counted). The caller must hold RECORD_AUDIO. */
    @Synchronized
    fun start(hasPermission: Boolean) {
        if (!hasPermission) { _state.value = MicState.Failed(MicState.Reason.NO_PERMISSION); return }
        users++
        if (running) return
        running = true
        thread = Thread(::loop, "sax-mic").apply { start() }
    }

    @Synchronized
    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) running = false
    }

    /** Retries after a failure. */
    fun retry(hasPermission: Boolean) { synchronized(this) { users = 0; running = false }; thread?.join(500); start(hasPermission) }

    @SuppressLint("MissingPermission") // checked by the caller, reported as NO_PERMISSION otherwise
    private fun loop() {
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(minBytes, 8192))
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                ?: AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(minBytes, 8192))
        } catch (e: SecurityException) {
            fail(MicState.Reason.NO_PERMISSION); return
        } catch (e: Exception) {
            fail(MicState.Reason.ERROR); return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); fail(MicState.Reason.BUSY); return }
        val tracker = PitchTracker(Yin(sampleRate = sampleRate))
        val buf = FloatArray(512)
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) { fail(MicState.Reason.BUSY); return }
            _state.value = MicState.Listening
            while (running) {
                val n = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n < 0) { fail(MicState.Reason.ERROR); break }
                rawListener?.invoke(buf, n)
                tracker.push(buf, n).forEach { _frames.tryEmit(it) }
            }
        } catch (e: Exception) {
            fail(MicState.Reason.ERROR)
        } finally {
            runCatching { record.stop() }
            record.release()
            if (_state.value == MicState.Listening) _state.value = MicState.Idle
        }
    }

    private fun fail(reason: MicState.Reason) {
        running = false
        synchronized(this) { users = 0 }
        _state.value = MicState.Failed(reason)
    }
}
