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
import app.supersax.domain.song.withSharedFrom
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
    /** The song and its versions loaded in the engine (id → removed instrument or null), and the one heard. */
    private val _versions = MutableStateFlow<List<Pair<String, String?>>>(emptyList())
    val versions = _versions.asStateFlow()
    private val _active = MutableStateFlow(songId)
    /** The version to switch to as soon as it is loaded (opened from the list). */
    private var pendingVersion: String? = null
    val active = _active.asStateFlow()
    private val _mix = MutableStateFlow((c.playback.practiceMix?.takeIf { it.first == songId }?.second as? MixUi) ?: MixUi())
    val mix = _mix.asStateFlow()

    init {
        viewModelScope.launch {
            val r = c.media.audioRefs.first().firstOrNull { it.id == songId } ?: return@launch
            _ref.value = r
            // The version chips show at once; the versions load into the engine in the background.
            _versions.value = familySetups().map { (id, s) -> id to s.removed }
            pendingVersion = c.playback.openVersion?.takeIf { v -> _versions.value.any { it.first == v } }
            c.playback.openVersion = null
            pendingVersion?.let { v -> _active.value = v; c.media.audioRefs.first().firstOrNull { it.id == v }?.let { _ref.value = it } }
            if (!prepare(r)) return@launch
            carryOn()
            prepareFamily()
            // A version of a song (or the original of versions) uses the family's analysis: never analysed twice.
            val shared = familySetups().filter { (id, s) -> id != songId && s.analyzed && s.analysisVersion >= app.supersax.domain.song.CURRENT_ANALYSIS }
            val saved = c.songs.get(songId)
            val best = shared.firstOrNull { it.first == (saved.parentId ?: songId) } ?: shared.firstOrNull()
            if (best != null) c.songs.save(songId, saved.withSharedFrom(best.second))
            else if (!saved.analyzed || saved.analysisVersion < app.supersax.domain.song.CURRENT_ANALYSIS) analyse()
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

    /**
     * Carries on playback: from the other version of this song (switching Original / No sax keeps the place,
     * speed and key), or from the music player if it was playing this song.
     */
    private suspend fun carryOn() {
        val h = c.playback.handoff?.takeIf { it.songId == songId }
        if (h != null) {
            c.playback.handoff = null
            player.setSpeed(h.speed); player.setSemitones(h.semitones)
            player.seekTo(h.positionMs)
            if (h.playing) player.play()
            return
        }
        val listening = c.playback.takeListening(songId) ?: pendingVersion?.let { c.playback.takeListening(it) } ?: return
        player.seekTo(listening.first)
        if (listening.second) player.play()
    }

    /** Before opening [otherId] (another version of this song): note where we are and stop here. */
    fun handoffTo(otherId: String) {
        val u = player.ui.value
        c.playback.handoff = app.supersax.playback.Handoff(otherId, u.positionMs, u.playing, u.speed, u.semitones)
        player.pause()
    }

    /**
     * Loads the other versions of this song into the engine (decoding them once, in the background), lined up
     * with this one, so switching between them is seamless.
     */
    private fun prepareFamily() = viewModelScope.launch(Dispatchers.IO) {
        val dir = java.io.File(c.context.cacheDir, "songs")
        val refs = c.media.audioRefs.first()
        val loaded = c.playback.practiceVersions
        // The engine may still be playing a version from before (kept in the background): show it as heard.
        val heard = player.version
        loaded.entries.firstOrNull { it.value == heard }?.key?.let { id -> _active.value = id; refs.firstOrNull { it.id == id }?.let { _ref.value = it } }
        val family = familySetups().sortedBy { if (it.first == pendingVersion) 0 else 1 }
        for ((id, _) in family) {
            if (id in loaded) { if (id == pendingVersion) { pendingVersion = null; withContext(Dispatchers.Main) { selectVersion(id) } }; continue }
            val f = java.io.File(dir, "$id.pcm")
            var info = AudioDecoder.pcmInfo(f)
            if (info == null) {
                val r = refs.firstOrNull { it.id == id } ?: continue
                info = runCatching { AudioDecoder.decodeToPcm(c.context, Uri.parse(r.uri), f) { } }.getOrNull() ?: continue
            }
            val index = runCatching { player.addVersion(f, info) }.getOrDefault(-1)
            if (index >= 0) loaded[id] = index
            if (id == pendingVersion && index >= 0) { pendingVersion = null; withContext(Dispatchers.Main) { selectVersion(id) } }
        }
    }

    /** Hears version [id] from here on, without a gap. False if it is not loaded yet (then open it instead). */
    fun selectVersion(id: String): Boolean {
        val index = c.playback.practiceVersions[id] ?: return false
        player.setVersion(index)
        _active.value = id
        viewModelScope.launch { c.media.audioRefs.first().firstOrNull { it.id == id }?.let { _ref.value = it } }
        return true
    }

    /**
     * Deletes the AI version being heard (its file, cache and playlist places). Returns the song to open next:
     * the original when this page was opened on that version, null to stay here (switched back to the original).
     */
    suspend fun deleteVersion(): String? {
        val id = _active.value
        val s = c.songs.get(id)
        val parent = s.parentId ?: return null
        val r = c.media.audioRefs.first().firstOrNull { it.id == id } ?: return null
        if (id == songId) player.pause() else selectVersion(c.playback.practiceVersions.entries.firstOrNull { it.value == 0 }?.key ?: songId)
        c.playback.practiceVersions.remove(id)
        _versions.value = _versions.value.filterNot { it.first == id }
        c.media.deleteAudioRef(r.id)
        c.songs.delete(r.id)
        c.playlists.forget(r.id)
        withContext(Dispatchers.IO) {
            java.io.File(java.io.File(c.context.cacheDir, "songs"), "${r.id}.pcm").delete()
            val path = Uri.parse(r.uri).path
            if (r.uri.startsWith("file:") && path != null && path.startsWith(c.songsDir.absolutePath)) java.io.File(path).delete()
        }
        return if (id == songId) parent else null
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
            val analysed = old.copy(
                analyzed = true, detectedBpm = tempo.bpm, bpm = tempo.bpm, firstBeatMs = (tempo.firstBeatSec * 1000).roundToLong(),
                keyTonic = key.tonic, keyMinor = key.minor, keyConfidence = key.confidence,
                analysisVersion = app.supersax.domain.song.CURRENT_ANALYSIS,
            )
            c.songs.save(songId, analysed)
            // The original and its versions get the result too.
            familySetups().forEach { (id, s) -> if (id != songId) c.songs.save(id, s.withSharedFrom(analysed)) }
            _analysis.value = AnalysisUi()
        }
    }

    /** Changes this song's setup, and the bars, tempo and checkpoints of its whole family with it. */
    private fun update(f: (SongSetup) -> SongSetup) = viewModelScope.launch {
        val changed = f(c.songs.get(songId))
        c.songs.save(songId, changed)
        familySetups().forEach { (id, s) -> if (id != songId) c.songs.save(id, s.withSharedFrom(changed)) }
    }

    /** This song, its original and the original's other versions, with their setups. */
    private suspend fun familySetups(): List<Pair<String, SongSetup>> {
        val me = c.songs.get(songId)
        val root = me.parentId ?: songId
        val refs = c.media.audioRefs.first()
        return refs.mapNotNull { r ->
            val s = if (r.id == songId) me else c.songs.get(r.id)
            if (r.id == root || s.parentId == root) r.id to s else null
        }
    }

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
