package app.saxpractice.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.domain.audio.HoldReport
import app.saxpractice.domain.common.RankEventKind
import app.saxpractice.domain.common.RankEventRecord
import app.saxpractice.domain.common.TunerHoldRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Saves a hold report and, for a steady hold, a tuner-verified range note. Shared by every listening screen. */
suspend fun AppContainer.saveHold(r: HoldReport, verifiedRangeMinSec: Double = 1.0) {
    val now = clock.nowMs()
    progress.saveHold(
        TunerHoldRecord(UUID.randomUUID().toString(), now, r.refMidi, r.stableSec, r.inTuneShare, r.toleranceCents, r.meanCents,
            r.driftCents, r.slopeCentsPerSec, r.minCents, r.maxCents, r.trace, r.traceHz),
    )
    if (r.stableSec >= verifiedRangeMinSec && kotlin.math.abs(r.meanCents) <= 50) {
        progress.addRankEvent(
            RankEventRecord(UUID.randomUUID().toString(), now, RankEventKind.RANGE_NOTE,
                writtenMidi = settings.current().instrument.concertToWrittenMidi(r.refMidi), verified = true),
        )
    }
}

/** The tuner screen: live reading, hold reports saved to the history, optional target note and drone. */
class TunerViewModel(private val c: AppContainer) : ViewModel() {
    val tuner = LiveTuner(viewModelScope, c.mic, { c.audio.a4 }) { c.saveHold(it) }
    val micState = c.mic.state
    val output = c.audio.state
    private val _target = MutableStateFlow<Int?>(null)
    /** Target note as concert MIDI (null = nearest note). */
    val target: StateFlow<Int?> = _target.asStateFlow()
    val holds = c.progress.holds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun start(granted: Boolean) = tuner.start(granted)
    fun retry(granted: Boolean) { tuner.stop(); c.mic.retry(granted); tuner.start(granted) }

    fun setTarget(concertMidi: Int?) {
        _target.value = concertMidi
        tuner.targetMidi = concertMidi
        if (c.audio.state.value.droneMidi != null) c.audio.setDrone(concertMidi)
    }

    fun toggleDrone() {
        val t = _target.value ?: return
        c.audio.setDrone(if (c.audio.state.value.droneMidi == null) t else null)
    }

    fun playReference() { _target.value?.let { c.audio.playTone(it, 1500) } }

    override fun onCleared() {
        tuner.stop()
        c.audio.setDrone(null)
        viewModelScope.launch { }
    }
}
