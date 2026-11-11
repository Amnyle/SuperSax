package app.supersax.ui.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.audio.PracticePlayer
import app.supersax.audio.Recorder
import app.supersax.domain.common.PieceStage
import app.supersax.domain.common.RankEventKind
import app.supersax.domain.common.RecordingRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class RecordUi(
    val recording: Boolean = false,
    val elapsedMs: Long = 0,
    val error: Boolean = false,
    /** Two recordings being compared, and which one is audible. */
    val compare: Pair<String, String>? = null,
    val aActive: Boolean = true,
    val playingId: String? = null,
)

/** Records takes (AAC m4a, app-private), tags them, plays and compares two with an A/B toggle. */
class RecordingsViewModel(private val c: AppContainer) : ViewModel() {
    val recordings = c.media.recordings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reeds = c.media.reeds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mouthpieces = c.media.mouthpieces.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val recorder = Recorder(c.context)
    val playerA = PracticePlayer(c.context)
    val playerB = PracticePlayer(c.context)
    private val _ui = MutableStateFlow(RecordUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                if (recorder.recording) _ui.value = _ui.value.copy(elapsedMs = recorder.elapsedMs())
                playerA.tick(); playerB.tick()
                delay(100)
            }
        }
    }

    fun piece(id: String?) = c.libraryService.piece(id)

    fun start() {
        val file = File(c.recordingsDir, "take-${System.currentTimeMillis()}.m4a")
        val ok = runCatching { recorder.start(file) }.isSuccess
        _ui.value = _ui.value.copy(recording = ok, error = !ok, elapsedMs = 0)
    }

    fun stop(exerciseKey: String?, pieceId: String?, mouthpieceId: String?, benchmark: Boolean, note: String) {
        val file = recorder.file
        val duration = recorder.stop()
        _ui.value = _ui.value.copy(recording = false)
        if (duration == null || file == null) { _ui.value = _ui.value.copy(error = true); return }
        viewModelScope.launch {
            val s = c.settings.current()
            c.media.saveRecording(RecordingRecord(UUID.randomUUID().toString(), c.clock.nowMs(), file.name, duration,
                exerciseKey?.takeIf { it.isNotBlank() }, pieceId, s.currentReedId, mouthpieceId ?: s.currentMouthpieceId, benchmark, note))
        }
    }

    fun play(r: RecordingRecord) {
        stopAll()
        playerA.load(Uri.fromFile(c.media.fileOf(r))); playerA.play()
        _ui.value = _ui.value.copy(playingId = r.id, compare = null)
    }

    fun stopAll() { playerA.pause(); playerB.pause(); _ui.value = _ui.value.copy(playingId = null) }

    /** Loads two takes; both play together and the toggle chooses which one is heard. */
    fun compare(a: RecordingRecord, b: RecordingRecord) {
        stopAll()
        playerA.load(Uri.fromFile(c.media.fileOf(a))); playerB.load(Uri.fromFile(c.media.fileOf(b)))
        _ui.value = _ui.value.copy(compare = a.id to b.id, aActive = true)
        playerA.setVolume(1f); playerB.setVolume(0f)
    }

    fun comparePlayPause() {
        if (playerA.ui.value.playing || playerB.ui.value.playing) { playerA.pause(); playerB.pause() }
        else {
            val pos = minOf(playerA.position(), playerB.position())
            playerA.seekTo(pos); playerB.seekTo(pos); playerA.play(); playerB.play()
        }
    }

    fun toggleAB() {
        val a = !_ui.value.aActive
        playerA.setVolume(if (a) 1f else 0f); playerB.setVolume(if (a) 0f else 1f)
        _ui.value = _ui.value.copy(aActive = a)
    }

    fun closeCompare() { stopAll(); _ui.value = _ui.value.copy(compare = null) }

    fun delete(r: RecordingRecord) = viewModelScope.launch { c.media.deleteRecording(r) }
    fun setBenchmark(r: RecordingRecord, on: Boolean) = viewModelScope.launch { c.media.saveRecording(r.copy(benchmark = on)) }

    /** Attaches a take as evidence: to the tagged piece's stage, or to the latest clean pair of the tagged scale. */
    fun attachEvidence(r: RecordingRecord) = viewModelScope.launch {
        r.pieceId?.let { pid ->
            val existing = c.library.progressNow().firstOrNull { it.pieceId == pid && it.sectionId.isEmpty() }
            c.libraryService.setStage(pid, "", existing?.stage ?: PieceStage.LEARNING, c.clock.nowMs(), evidence = r.id)
        }
        r.exerciseKey?.let { key ->
            c.progress.rankEvents.first().filter { it.kind == RankEventKind.CLEAN_PAIR && it.scaleKey == key }.maxByOrNull { it.atMs }?.let {
                c.progress.addRankEvent(it.copy(evidenceRecordingId = r.id))
            }
        }
    }

    override fun onCleared() { runCatching { recorder.stop() }; playerA.release(); playerB.release() }
}
