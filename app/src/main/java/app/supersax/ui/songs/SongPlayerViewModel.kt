package app.supersax.ui.songs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.audio.AudioDecoder
import app.supersax.domain.common.AudioRefRecord
import app.supersax.domain.song.Checkpoint
import app.supersax.domain.song.KeyDetector
import app.supersax.domain.song.SongBars
import app.supersax.domain.song.SongSetup
import app.supersax.domain.song.TempoDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToLong

/** A part of the song to practise, in the original recording's time. */
data class Part(val fromMs: Long, val toMs: Long, val stop: Boolean)

/** Metronome over the song and the two volumes (0..1). */
data class MixUi(val clicks: Boolean = false, val clickVolume: Float = 0.7f, val musicVolume: Float = 1f)

/** Preparing (decoding once to the cache) and analysis progress: null when idle, 0..1 while working. */
data class AnalysisUi(val progress: Float? = null, val failed: Boolean = false, val preparing: Boolean = false)

class SongPlayerViewModel(private val c: AppContainer, val songId: String) : ViewModel() {
    /** The engine is kept by the playback hub, so the song can go on playing with the screen closed. */
    val player = c.playback.practiceEngine(songId)
    private val pcm = java.io.File(java.io.File(c.context.cacheDir, "songs").apply { mkdirs() }, "$songId.pcm")
    private var pcmInfo: AudioDecoder.PcmInfo? = null
    private val _ref = MutableStateFlow<AudioRefRecord?>(null)
    val ref = _ref.asStateFlow()
    val setup: StateFlow<SongSetup> = c.songs.observe(songId).stateIn(viewModelScope, SharingStarted.Eagerly, SongSetup())
    private val _analysis = MutableStateFlow(AnalysisUi())
    val analysis = _analysis.asStateFlow()
    private val _part = MutableStateFlow<Part?>(null)
    val part = _part.asStateFlow()
    private val _mix = MutableStateFlow((c.playback.practiceMix?.takeIf { it.first == songId }?.second as? MixUi) ?: MixUi())
    val mix = _mix.asStateFlow()

    init {
        viewModelScope.launch {
            val r = c.media.audioRefs.first().firstOrNull { it.id == songId } ?: return@launch
            _ref.value = r
            if (!prepare(r)) return@launch
            val saved = c.songs.get(songId)
            if (!saved.analyzed || saved.analysisVersion < app.supersax.domain.song.CURRENT_ANALYSIS) analyse()
        }
        viewModelScope.launch {
            var wasPlaying = false
            var lastPos = 0L
            while (isActive) {
                player.tick()
                val u = player.ui.value
                // Playing from this screen hands the controls to the notification and lock screen.
                if (u.playing && !wasPlaying) _ref.value?.let { r -> c.playback.attachPractice(player, songId, r.title) }
                else if (u.playing != wasPlaying || kotlin.math.abs(u.positionMs - lastPos) > 1500) c.playback.practiceChanged(player)
                wasPlaying = u.playing; lastPos = u.positionMs
                c.playback.practiceMix = songId to _mix.value
                val st = setup.value
                val m = _mix.value
                player.setClicks(m.clicks, m.clickVolume, if (st.analyzed) st.bpm else 0.0, st.firstBeatMs, st.beatsPerBar)
                delay(40)
            }
        }
    }

    /** Decodes the song once into the cache (reused next time) and opens it in the engine. */
    private suspend fun prepare(r: AudioRefRecord): Boolean {
        var info = withContext(Dispatchers.IO) { AudioDecoder.pcmInfo(pcm) }
        if (info == null) {
            _analysis.value = AnalysisUi(0f, preparing = true)
            info = withContext(Dispatchers.IO) {
                runCatching { AudioDecoder.decodeToPcm(c.context, Uri.parse(r.uri), pcm) { p -> _analysis.value = AnalysisUi(p.toFloat(), preparing = true) } }.getOrNull()
            }
        }
        if (info == null) { _analysis.value = AnalysisUi(null, failed = true); return false }
        pcmInfo = info
        // Still open from before (it kept playing in the background): carry on where it is.
        if (!player.isReady) withContext(Dispatchers.IO) { player.open(pcm, info) }
        _analysis.value = AnalysisUi()
        return true
    }

    /** Detects tempo, first beat and key from the cached audio; keeps the checkpoints and the source. */
    fun analyse() {
        val info = pcmInfo ?: run { _ref.value?.let { r -> viewModelScope.launch { if (prepare(r)) analyse() } }; return }
        if (_analysis.value.progress != null) return
        _analysis.value = AnalysisUi(0.1f)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val samples = AudioDecoder.monoFromPcm(pcm, info)
                    _analysis.value = AnalysisUi(0.6f)
                    val tempo = TempoDetector.detect(samples, AudioDecoder.RATE)
                    _analysis.value = AnalysisUi(0.93f)
                    val key = KeyDetector.detect(samples, AudioDecoder.RATE)
                    tempo to key
                }
            }
            val (tempo, key) = result.getOrNull() ?: run { _analysis.value = AnalysisUi(null, failed = true); return@launch }
            val old = c.songs.get(songId)
            c.songs.save(songId, old.copy(
                analyzed = true, detectedBpm = tempo.bpm, bpm = tempo.bpm, firstBeatMs = (tempo.firstBeatSec * 1000).roundToLong(),
                keyTonic = key.tonic, keyMinor = key.minor, keyConfidence = key.confidence,
                analysisVersion = app.supersax.domain.song.CURRENT_ANALYSIS,
            ))
            _analysis.value = AnalysisUi()
        }
    }

    private fun update(f: (SongSetup) -> SongSetup) = viewModelScope.launch { c.songs.save(songId, f(c.songs.get(songId))) }

    // --- tempo and key ------------------------------------------------------------------------

    /** Playback tempo in BPM for the current speed. */
    fun playingBpm(s: SongSetup): Double = s.bpm * player.ui.value.speed

    /** Plays at [bpm] (50 to 150 percent of the original). */
    fun setPlayingBpm(bpm: Double) {
        val s = setup.value
        if (s.bpm <= 0) return
        player.setSpeed((bpm / s.bpm).coerceIn(0.5, 1.5).toFloat())
    }

    fun setSemitones(n: Int) = player.setSemitones(n.coerceIn(-6, 6))

    /** Tempo in steps of 10 percent of the original (100 → 90 → 80 …), from 50 to 150 percent. */
    fun stepTempo(steps: Int) {
        val tenths = (Math.round(player.ui.value.speed * 10) + steps).coerceIn(5, 15)
        player.setSpeed(tenths / 10f)
    }

    // --- metronome and volumes ----------------------------------------------------------------

    fun setClicks(on: Boolean) { _mix.value = _mix.value.copy(clicks = on) }
    fun setClickVolume(v: Float) { _mix.value = _mix.value.copy(clickVolume = v) }
    fun setMusicVolume(v: Float) { _mix.value = _mix.value.copy(musicVolume = v); player.setVolume(v) }

    // --- bars ---------------------------------------------------------------------------------

    fun barStart(n: Int, s: SongSetup = setup.value) = SongBars.barStartMs(n, s.bpm, s.firstBeatMs, s.beatsPerBar).coerceAtLeast(0)

    fun seekToBar(n: Int) = player.seekTo(barStart(n))

    /** Corrections: bar 1 starts at the current position; tempo nudges; ×2 / ÷2; beats per bar. */
    fun barOneHere() { val pos = player.position(); update { it.copy(firstBeatMs = pos) } }
    fun nudgeBpm(delta: Double) = update { it.copy(bpm = (it.bpm + delta).coerceIn(30.0, 300.0)) }
    fun scaleBpm(factor: Double) = update { it.copy(bpm = (it.bpm * factor).coerceIn(30.0, 300.0)) }
    fun setBeatsPerBar(n: Int) = update { it.copy(beatsPerBar = n) }
    fun resetBars() = update { it.copy(bpm = it.detectedBpm) }

    // --- practising a part --------------------------------------------------------------------

    /** Practise from [fromMs] to [toMs]: loop it, or stop at the end (then Play starts it again). */
    fun playPart(fromMs: Long, toMs: Long, stop: Boolean) {
        if (toMs <= fromMs) return
        _part.value = Part(fromMs, toMs, stop)
        if (stop) { player.setLoop(null, null); player.setStopAt(toMs, fromMs) }
        else { player.setStopAt(null, null); player.setLoop(fromMs, toMs) }
        player.seekTo(fromMs)
        player.play()
    }

    fun playBars(from: Int, to: Int, stop: Boolean) = playPart(barStart(from), barStart(to + 1), stop)

    fun clearPart() {
        _part.value = null
        player.setLoop(null, null)
        player.setStopAt(null, null)
    }

    // --- checkpoints --------------------------------------------------------------------------

    fun addCheckpoint(name: String) {
        val pos = player.position()
        update { s -> s.copy(checkpoints = (s.checkpoints + Checkpoint(UUID.randomUUID().toString(), name, pos)).sortedBy { it.ms }) }
    }

    fun deleteCheckpoint(id: String) = update { s -> s.copy(checkpoints = s.checkpoints.filterNot { it.id == id }) }

    /** From this checkpoint to the next one (or the end of the song). */
    fun playFromCheckpoint(cp: Checkpoint, stop: Boolean) {
        val next = setup.value.checkpoints.firstOrNull { it.ms > cp.ms }?.ms ?: player.ui.value.durationMs
        playPart(cp.ms, next, stop)
    }

    override fun onCleared() { c.playback.practiceScreenClosed(player) }
}
