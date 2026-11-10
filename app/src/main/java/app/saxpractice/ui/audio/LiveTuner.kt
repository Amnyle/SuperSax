package app.saxpractice.ui.audio

import app.saxpractice.audio.MicCapture
import app.saxpractice.domain.audio.HoldConfig
import app.saxpractice.domain.audio.HoldReport
import app.saxpractice.domain.audio.HoldTracker
import app.saxpractice.domain.pitch.Tuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the tuner shows right now. Notes are concert MIDI; the UI converts to written names. */
data class TunerReading(
    val frequencyHz: Double? = null,
    val concertMidi: Int? = null,
    val cents: Double = 0.0,
    val holding: Boolean = false,
    val holdSec: Double = 0.0,
    val lastReport: HoldReport? = null,
)

/**
 * Turns microphone frames into a live reading and hold reports. When [targetMidi] is set, cents are
 * measured against it (exercise target); otherwise against the nearest semitone.
 */
class LiveTuner(
    private val scope: CoroutineScope,
    private val mic: MicCapture,
    private val a4: () -> Double,
    private val toleranceCents: Double = 10.0,
    private val onHold: suspend (HoldReport) -> Unit = {},
) {
    private val _reading = MutableStateFlow(TunerReading())
    val reading: StateFlow<TunerReading> = _reading.asStateFlow()
    var targetMidi: Int? = null
        set(value) { field = value; tracker.targetMidi = value }
    private var tracker = HoldTracker(HoldConfig(toleranceCents = toleranceCents, a4 = a4()))
    private var job: Job? = null

    fun start(hasPermission: Boolean) {
        if (job != null) return
        tracker = HoldTracker(HoldConfig(toleranceCents = toleranceCents, a4 = a4()), targetMidi)
        mic.start(hasPermission)
        job = scope.launch {
            mic.frames.collect { f ->
                val report = tracker.push(f)
                val hz = f.frequencyHz?.takeIf { f.confidence >= 0.8 }
                val midiF = hz?.let { Tuning.frequencyToMidi(it, a4()) }
                val ref = targetMidi ?: midiF?.roundToInt()
                val cents = if (hz != null && ref != null) Tuning.cents(hz, Tuning.midiToFrequency(ref, a4())) else 0.0
                _reading.value = _reading.value.copy(
                    frequencyHz = hz, concertMidi = midiF?.roundToInt(), cents = cents,
                    holding = tracker.holding, holdSec = tracker.holdSeconds(),
                    lastReport = report ?: _reading.value.lastReport,
                )
                if (report != null) onHold(report)
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        mic.stop()
        _reading.value = _reading.value.copy(frequencyHz = null, holding = false, holdSec = 0.0)
    }

    fun clearReport() { _reading.value = _reading.value.copy(lastReport = null) }
}
