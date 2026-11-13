package app.supersax.ui.take

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.audio.AudioDecoder
import app.supersax.audio.TakeAudio
import app.supersax.domain.song.SongSetup
import app.supersax.domain.take.TakeMixer
import app.supersax.take.TakeExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Set-up, camera framing (not recording yet), recording, closing the files, then review and export. */
enum class TakeStage { SETUP, CAMERA, RECORDING, FINISHING, REVIEW }

/** A song and its AI versions (id → removed instrument, null for the original). */
data class TakeSong(val id: String, val title: String, val versions: List<Pair<String, String?>>)

data class TakeSetupUi(
    val songs: List<TakeSong> = emptyList(),
    val songId: String? = null,
    val versionId: String? = null,
    val front: Boolean = true,
    val startBar: Int = 1,
    val bars: Int = 0,
    val speed: Float = 1f,
    val semitones: Int = 0,
    val headphones: Boolean = false,
    val setup: SongSetup = SongSetup(),
    val preparing: Float? = null,
    val ready: Boolean = false,
    val error: String? = null,
)

data class TakeLiveUi(val beat: Int = -1, val songMs: Long = 0, val elapsedMs: Long = 0)

data class TakeReviewUi(
    val durationMs: Long = 0,
    val aspect: Float = 9f / 16f,
    val balance: Float = 0.5f,
    val nudgeMs: Int = 0,
    val fromMs: Long = 0,
    val toMs: Long = 0,
    val previewing: Boolean = false,
    /** Where the sync check plays from (ms on the video), and the first note you played (null if not found). */
    val listenMs: Long = 0,
    val firstNoteMs: Long? = null,
    val exportProgress: Float? = null,
    val exported: File? = null,
    val saved: String? = null,
    val error: String? = null,
)

class TakeViewModel(private val c: AppContainer) : ViewModel() {
    private val dir = File(c.context.cacheDir, "takes").apply { mkdirs() }
    private val videoFile = File(dir, "video.mp4")
    private val musicFile = File(dir, "music.pcm")
    private val micFile = File(dir, "mic.pcm")
    private val outFile = File(dir, "take.mp4")

    private val _stage = MutableStateFlow(TakeStage.SETUP)
    val stage = _stage.asStateFlow()
    private val _setup = MutableStateFlow(TakeSetupUi(headphones = headphonesConnected()))
    val setup = _setup.asStateFlow()
    private val _live = MutableStateFlow(TakeLiveUi())
    val live = _live.asStateFlow()
    private val _review = MutableStateFlow(TakeReviewUi())
    val review = _review.asStateFlow()

    private var pcmInfo: AudioDecoder.PcmInfo? = null
    private var audio: TakeAudio? = null
    var recording: Recording? = null
    private var videoStartNanos = Long.MAX_VALUE
    private var recordStartNanos = 0L
    private var mixer: TakeMixer? = null
    private val openFiles = ArrayList<RandomAccessFile>()
    private var prepareJob: Job? = null
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val refs = c.media.audioRefs.first()
            val setups = refs.associate { it.id to c.songs.get(it.id) }
            val songs = refs.filter { setups[it.id]?.parentId == null }.map { r ->
                TakeSong(r.id, r.title, listOf(r.id to null) + refs.filter { setups[it.id]?.parentId == r.id }.map { it.id to setups[it.id]?.removed })
            }
            _setup.value = _setup.value.copy(songs = songs)
        }
    }

    fun headphonesConnected(): Boolean {
        val am = c.context.getSystemService(AudioManager::class.java) ?: return false
        val types = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }

    fun pickSong(id: String) {
        val song = _setup.value.songs.firstOrNull { it.id == id } ?: return
        // The song player's speed and key when this song (or a version) is the one being practised.
        val (speed, semis) = c.playback.practiceSettings(song.versions.map { it.first }) ?: (1f to 0)
        _setup.value = _setup.value.copy(songId = id, versionId = song.versions.last().first, speed = speed, semitones = semis, startBar = 1)
        prepare()
    }

    fun pickVersion(id: String) { _setup.value = _setup.value.copy(versionId = id); prepare() }
    fun setFront(v: Boolean) { _setup.value = _setup.value.copy(front = v) }
    fun setStartBar(v: Int) { _setup.value = _setup.value.copy(startBar = v.coerceIn(1, maxOf(1, _setup.value.bars))) }
    fun setSpeed(v: Float) { _setup.value = _setup.value.copy(speed = (Math.round(v * 20) / 20f).coerceIn(0.5f, 1.5f)) }
    fun setSemitones(v: Int) { _setup.value = _setup.value.copy(semitones = v.coerceIn(-12, 12)) }
    fun setHeadphones(v: Boolean) { _setup.value = _setup.value.copy(headphones = v) }

    /** Decodes the chosen version (once, shared with the song player) and reads the bars. */
    private fun prepare() {
        val id = _setup.value.versionId ?: return
        prepareJob?.cancel()
        _setup.value = _setup.value.copy(ready = false, preparing = 0f, error = null)
        prepareJob = viewModelScope.launch {
            val ref = c.media.audioRefs.first().firstOrNull { it.id == id } ?: return@launch
            val info = c.playback.pcm(ref) { p -> _setup.value = _setup.value.copy(preparing = p.toFloat()) }
            if (info == null) { _setup.value = _setup.value.copy(preparing = null, error = "decode"); return@launch }
            pcmInfo = info
            val st = c.songs.get(_setup.value.songId ?: id)
            val durationMs = info.frames * 1000 / info.rate
            val bars = if (st.analyzed && st.bpm > 0) ((durationMs - st.firstBeatMs) / barMs(st)).toInt().coerceAtLeast(1) else 0
            _setup.value = _setup.value.copy(setup = st, bars = bars, preparing = null, ready = true, startBar = _setup.value.startBar.coerceIn(1, maxOf(1, bars)))
        }
    }

    private fun barMs(st: SongSetup) = st.beatsPerBar * 60000.0 / st.bpm

    /** Where the take starts in the song (ms) for the chosen bar. */
    fun startMs(): Long {
        val s = _setup.value
        val st = s.setup
        return if (s.bars > 0) (st.firstBeatMs + (s.startBar - 1) * barMs(st)).toLong().coerceAtLeast(0) else 0
    }

    /** Playback tempo of the count-in. */
    fun countBpm(): Double = _setup.value.setup.let { if (it.analyzed && it.bpm > 0) it.bpm else 100.0 } * _setup.value.speed

    fun videoOutput() = videoFile

    /** Shows the camera to frame the shot; nothing plays or records yet. */
    fun openCamera() { if (_setup.value.ready) { c.playback.pauseAll(); _stage.value = TakeStage.CAMERA } }
    fun closeCamera() { if (_stage.value == TakeStage.CAMERA) _stage.value = TakeStage.SETUP }

    /** Called once the camera is bound: the recording starts with [start]; audio starts when the video does. */
    fun beginRecording(start: () -> Recording?) {
        stopPreview()
        mixer = null; closeFiles()
        listOf(videoFile, musicFile, micFile, outFile).forEach { it.delete() }
        videoStartNanos = Long.MAX_VALUE
        _review.value = TakeReviewUi()
        _live.value = TakeLiveUi()
        _stage.value = TakeStage.RECORDING
        recording = start()
        if (recording == null) { _stage.value = TakeStage.SETUP; _setup.value = _setup.value.copy(error = "camera") }
    }

    fun onVideoEvent(e: VideoRecordEvent) {
        when (e) {
            is VideoRecordEvent.Start -> {
                recordStartNanos = System.nanoTime()
                val s = _setup.value
                val vid = s.versionId ?: return
                val info = pcmInfo ?: return
                val a = TakeAudio(c.playback.pcmFile(vid), info, startMs(), s.speed, s.semitones, countBpm(), musicFile, micFile)
                audio = a
                a.start()
                viewModelScope.launch {
                    while (isActive && audio === a) {
                        _live.value = TakeLiveUi(a.beat, a.songMs, (System.nanoTime() - recordStartNanos) / 1_000_000)
                        if (a.done) { stop(); break }
                        delay(30)
                    }
                }
            }
            is VideoRecordEvent.Status -> {
                // The first frame's time: now less the recorded duration (the smallest estimate has the least delay).
                val d = e.recordingStats.recordedDurationNanos
                if (d > 0) videoStartNanos = minOf(videoStartNanos, System.nanoTime() - d)
            }
            is VideoRecordEvent.Finalize -> {
                recording = null
                if (e.hasError() && e.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE && !videoFile.exists()) {
                    _stage.value = TakeStage.SETUP
                    _setup.value = _setup.value.copy(error = "camera")
                } else openReview()
            }
            else -> {}
        }
    }

    /** Ends the take (the user's stop, or the end of the song). */
    fun stop() {
        if (_stage.value != TakeStage.RECORDING) return
        _stage.value = TakeStage.FINISHING
        val a = audio
        viewModelScope.launch(Dispatchers.IO) {
            a?.stop()
            withContext(Dispatchers.Main) { recording?.stop() ?: openReview() }
        }
    }

    private fun openReview() {
        viewModelScope.launch {
            val a = audio ?: run { _stage.value = TakeStage.SETUP; return@launch }
            val vi = withContext(Dispatchers.IO) { TakeExport.info(videoFile) }
            if (vi == null || !musicFile.exists() || !micFile.exists()) { _stage.value = TakeStage.SETUP; _setup.value = _setup.value.copy(error = "take"); return@launch }
            val m = withContext(Dispatchers.IO) {
                closeFiles()
                val mf = RandomAccessFile(musicFile, "r").also { openFiles += it }
                val kf = RandomAccessFile(micFile, "r").also { openFiles += it }
                val mic = TakeAudio.open(kf)
                val start = if (videoStartNanos == Long.MAX_VALUE) recordStartNanos else videoStartNanos
                TakeMixer(TakeAudio.open(mf), a.musicClock, mic, a.micClock, start).also { it.setMicScale(TakeMixer.normalise(mic)) }
            }
            mixer = m
            val start = if (videoStartNanos == Long.MAX_VALUE) recordStartNanos else videoStartNanos
            // Start half a second before the song (after the count-in) by default.
            val songAt = ((a.musicClock.nanosAt(a.songStartFrame.toDouble()) - start) / 1e6).toLong()
            val from = (songAt - 500).coerceIn(0, maxOf(0, vi.durationMs - 1000))
            val balance = if (_setup.value.headphones) 0.5f else 1f
            m.setBalance(balance)
            // The sync is checked where the sax plays: from just before your first note, not during the intro.
            val first = withContext(Dispatchers.IO) { m.firstSoundMs(maxOf(0L, songAt).toDouble(), vi.durationMs.toDouble())?.toLong() }
            val listen = ((first ?: from) - 1500).coerceIn(0, maxOf(0, vi.durationMs - 1000))
            _review.value = TakeReviewUi(durationMs = vi.durationMs, aspect = vi.aspect, balance = balance, fromMs = from, toMs = vi.durationMs,
                listenMs = listen, firstNoteMs = first)
            _stage.value = TakeStage.REVIEW
        }
    }

    fun setBalance(b: Float) { mixer?.setBalance(b); _review.value = _review.value.copy(balance = b, exported = null, saved = null) }
    fun setNudge(ms: Int) { mixer?.setNudgeMs(ms.toDouble()); _review.value = _review.value.copy(nudgeMs = ms, exported = null, saved = null) }
    fun setTrim(from: Long, to: Long) { _review.value = _review.value.copy(fromMs = from, toMs = maxOf(to, from + 1000), exported = null, saved = null) }

    fun setListenAt(ms: Long) {
        _review.value = _review.value.copy(listenMs = ms.coerceIn(0, maxOf(0, _review.value.durationMs - 1000)))
    }

    fun listenAtFirstNote() { _review.value.firstNoteMs?.let { setListenAt(it - 1500) } }

    /**
     * Plays the mix (audio only) as a 6-second loop from [TakeReviewUi.listenMs] until stopped, so the balance and
     * sync sliders can be moved while listening (they apply at once).
     */
    fun togglePreview() {
        if (previewJob != null) { stopPreview(); return }
        val m = mixer ?: return
        _review.value = _review.value.copy(previewing = true)
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            val rate = m.rate
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            try {
                // The loop follows the "listen from" slider: moving it jumps there.
                var at = -1L
                var start = 0L; var end = 0L; var frame = 0L
                val buf = FloatArray(1024 * 2)
                track.play()
                while (isActive) {
                    val r = _review.value
                    if (r.listenMs != at) {
                        at = r.listenMs
                        start = at * rate / 1000; end = minOf(r.durationMs, at + 6_000) * rate / 1000; frame = start
                    }
                    m.render(frame, buf, 1024)
                    track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                    frame += 1024
                    if (frame >= end) frame = start
                }
            } finally {
                runCatching { track.stop() }; track.release()
                previewJob = null
                _review.value = _review.value.copy(previewing = false)
            }
        }
    }

    private fun stopPreview() { previewJob?.cancel(); previewJob = null; _review.value = _review.value.copy(previewing = false) }

    fun export() {
        val m = mixer ?: return
        stopPreview()
        val r = _review.value
        _review.value = r.copy(exportProgress = 0f, error = null, exported = null, saved = null)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    outFile.delete()
                    TakeExport.export(videoFile, m, r.fromMs, r.toMs, outFile) { p -> _review.value = _review.value.copy(exportProgress = p) }
                }
            }
            _review.value = _review.value.copy(exportProgress = null, exported = if (ok.isSuccess) outFile else null, error = ok.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName })
        }
    }

    fun title(): String {
        val s = _setup.value
        return s.songs.firstOrNull { it.id == s.songId }?.title ?: "Super Sax"
    }

    fun save() {
        val f = _review.value.exported ?: return
        viewModelScope.launch {
            val where = withContext(Dispatchers.IO) { runCatching { TakeExport.saveToGallery(c.context, f, title()) } }
            _review.value = _review.value.copy(saved = where.getOrNull(), error = where.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName })
        }
    }

    fun shareIntent() = _review.value.exported?.let { TakeExport.shareIntent(c.context, it, title()) }

    /** Back to the set-up for another take. */
    fun again() { stopPreview(); _stage.value = TakeStage.SETUP }

    private fun closeFiles() { openFiles.forEach { runCatching { it.close() } }; openFiles.clear() }

    override fun onCleared() {
        audio?.let { a -> Thread { a.stop() }.start() }
        recording?.stop()
        previewJob?.cancel()
        closeFiles()
    }
}
