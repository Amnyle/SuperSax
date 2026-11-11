package app.saxpractice.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Records the microphone to an AAC .m4a file in app-private storage. */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    var file: File? = null
        private set

    val recording: Boolean get() = recorder != null

    /** Starts recording into [target]. Throws if the microphone cannot be opened. */
    fun start(target: File) {
        stopQuietly()
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(128_000)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release(); target.delete(); throw e
        }
        recorder = r
        file = target
        startedAt = SystemClock.elapsedRealtime()
    }

    /** Stops and returns the duration in ms, or null if nothing usable was recorded. */
    fun stop(): Long? {
        val r = recorder ?: return null
        val duration = SystemClock.elapsedRealtime() - startedAt
        recorder = null
        return try { r.stop(); duration } catch (e: RuntimeException) { file?.delete(); null } finally { r.release() }
    }

    fun elapsedMs(): Long = if (recorder != null) SystemClock.elapsedRealtime() - startedAt else 0

    private fun stopQuietly() { runCatching { stop() } }
}
