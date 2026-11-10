package app.saxpractice.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.domain.common.ExerciseInfo
import app.saxpractice.domain.session.SessionRun
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the session player. The run itself lives in [app.saxpractice.data.repo.SessionManager];
 * this ViewModel only ticks a monotonic "now" for the countdown and forwards actions.
 */
class SessionViewModel(private val c: AppContainer) : ViewModel() {
    val run: StateFlow<SessionRun?> = c.sessions.run
    private val _now = MutableStateFlow(c.clock.elapsedMs())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** Offer to raise the tempo now, after two clean runs in a row: (scaleKey, newBpm). */
    private val _bumpOffer = MutableStateFlow<Pair<String, Int>?>(null)
    val bumpOffer: StateFlow<Pair<String, Int>?> = _bumpOffer.asStateFlow()

    /** Fired once per step change, so the screen can play a soft cue. */
    private val _stepChanged = MutableStateFlow(0)
    val stepChanged: StateFlow<Int> = _stepChanged.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                val t = c.clock.elapsedMs()
                _now.value = t
                val r = run.value
                if (r != null && !r.finished && !r.isPaused && r.remainingMs(t) == 0L) {
                    c.sessions.update { cur, n -> cur.catchUp(n) }
                    _stepChanged.value++
                }
                delay(200)
            }
        }
    }

    fun exerciseInfo(catalogId: String?): ExerciseInfo? = c.content.exercises.byId(catalogId)

    fun pause() = c.sessions.update { r, n -> r.pause(n) }
    fun resume() = c.sessions.update { r, n -> r.resume(n) }
    fun addMinute() = c.sessions.update { r, _ -> r.addMinute() }
    fun next() { c.sessions.update { r, n -> r.next(n, restSec = restSec()) }; _stepChanged.value++ }
    fun lipsTired() { c.sessions.update { r, n -> r.next(n, lipsTired = true, restSec = restSec()) }; _stepChanged.value++ }
    fun endEarly() = c.sessions.update { r, n -> r.finish(n) }

    private fun restSec(): Int = run.value?.steps?.firstOrNull { it.kind == app.saxpractice.domain.planner.StepKind.REST }?.durationSec ?: 60

    /** Logs a scale run. Two clean runs in a row offer an immediate tempo bump. */
    fun scaleRun(scaleKey: String, bpm: Int, clean: Boolean) {
        c.sessions.update { r, _ -> r.recordRun(scaleKey, bpm, clean) }
        val r = run.value ?: return
        val step = c.content.generatorConfig.adaptation
        if (clean && r.cleanStreak(scaleKey) > 0 && r.cleanStreak(scaleKey) % step.cleanRunsForBump == 0) {
            _bumpOffer.value = scaleKey to (bpm + step.bpmStep).coerceAtMost(step.maxBpm)
        }
    }

    /** Accepts the immediate bump: this and later steps of the same scale use the new tempo. */
    fun acceptBump() {
        val (key, bpm) = _bumpOffer.value ?: return
        c.sessions.update { r, _ ->
            r.copy(session = r.session.copy(steps = r.steps.mapIndexed { i, st ->
                if (i >= r.current && st.target.scaleKey == key) st.copy(target = st.target.copy(bpm = bpm)) else st
            }))
        }
        _bumpOffer.value = null
    }

    fun dismissBump() { _bumpOffer.value = null }

    fun complete(tiredness: Int?, note: String, onSaved: () -> Unit) {
        viewModelScope.launch { c.sessions.complete(tiredness, note); onSaved() }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch { c.sessions.discard(); onDone() }
    }
}
