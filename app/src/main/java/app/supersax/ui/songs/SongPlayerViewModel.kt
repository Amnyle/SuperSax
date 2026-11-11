package app.supersax.ui.songs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.audio.AudioDecoder
import app.supersax.audio.PracticePlayer
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

/** Analysis progress: null when idle, 0..1 while analysing. */
data class AnalysisUi(val progress: Float? = null, val failed: Boolean = false)

class SongPlayerViewModel(private val c: AppContainer, val songId: String) : ViewModel() {
    val player = PracticePlayer(c.context)
    private val _ref = MutableStateFlow<AudioRefRecord?>(null)
    val ref = _ref.asStateFlow()
    val setup: StateFlow<SongSetup> = c.songs.observe(songId).stateIn(viewModelScope, SharingStarted.Eagerly, SongSetup())
    private val _analysis = MutableStateFlow(AnalysisUi())
    val analysis = _analysis.asStateFlow()
    private val _part = MutableStateFlow<Part?>(null)
    val part = _part.asStateFlow()

    init {
        viewModelScope.launch {
            val r = c.media.audioRefs.first().firstOrNull { it.id == songId } ?: return@launch
            _ref.value = r
            player.load(Uri.parse(r.uri))
            if (!c.songs.get(songId).analyzed) analyse()
        }
        viewModelScope.launch { while (isActive) { player.tick(); delay(40) } }
    }

    /** Decodes the audio and detects tempo, first beat and key; keeps the checkpoints and the source. */
    fun analyse() {
        val r = _ref.value ?: return
        if (_analysis.value.progress != null) return
        _analysis.value = AnalysisUi(0f)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val samples = AudioDecoder.decode(c.context, Uri.parse(r.uri)) { p -> _analysis.value = AnalysisUi((p * 0.8).toFloat()) } ?: error("no audio")
                    _analysis.value = AnalysisUi(0.85f)
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

    override fun onCleared() { player.release() }
}
