package app.saxpractice.ui.drills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.R
import app.saxpractice.domain.common.DrillKind
import app.saxpractice.domain.drills.MatchDrill
import app.saxpractice.domain.pitch.SpelledPitch
import app.saxpractice.notation.StaffLayout
import app.saxpractice.notation.StaffView
import app.saxpractice.ui.audio.LiveTuner
import app.saxpractice.ui.audio.MicErrorBanner
import app.saxpractice.ui.audio.TunerDisplay
import app.saxpractice.ui.audio.hasMicPermission
import app.saxpractice.ui.audio.rememberMicRequest
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.writtenNoteLabel
import app.saxpractice.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

data class MatchUi(val notes: List<Int> = emptyList(), val index: Int = 0, val results: List<Boolean> = emptyList(), val matched: Boolean = false, val done: Boolean = false)

/** Match the note: hear the reference (the sounding pitch of the written note), play it, the tuner confirms. */
class MatchNoteViewModel(private val c: AppContainer) : ViewModel() {
    val tuner = LiveTuner(viewModelScope, c.mic, { c.audio.a4 }, MatchDrill.TOLERANCE_CENTS)
    private val _ui = MutableStateFlow(MatchUi())
    val ui = _ui.asStateFlow()
    val micState = c.mic.state
    private val startedAt = c.clock.nowMs()
    private val instrument get() = app.saxpractice.domain.pitch.Instrument.ALTO_SAX

    init {
        viewModelScope.launch {
            val p = c.practice.playerNow()
            val notes = MatchDrill.round(p.writtenLowMidi.coerceAtLeast(60), p.writtenHighMidi.coerceAtMost(84), seed = c.clock.nowMs())
            _ui.value = MatchUi(notes)
            select(0)
        }
        viewModelScope.launch {
            tuner.reading.collect { r ->
                val u = _ui.value
                if (u.done || u.matched || u.notes.isEmpty()) return@collect
                val target = instrument.writtenToConcertMidi(u.notes[u.index])
                if (r.frequencyHz != null && abs(r.cents) <= MatchDrill.TOLERANCE_CENTS && r.holdSec >= MatchDrill.HOLD_SEC && tuner.targetMidi == target) {
                    _ui.value = u.copy(matched = true)
                }
            }
        }
    }

    fun concertOf(written: Int) = instrument.writtenToConcertMidi(written)

    private fun select(i: Int) {
        val u = _ui.value
        tuner.targetMidi = concertOf(u.notes[i])
        tuner.clearReport()
        c.audio.playTone(concertOf(u.notes[i]), 1500)
    }

    fun replay() { val u = _ui.value; if (u.notes.isNotEmpty()) c.audio.playTone(concertOf(u.notes[u.index]), 1500) }

    fun next(success: Boolean) {
        val u = _ui.value
        val results = u.results + success
        if (u.index + 1 >= u.notes.size) {
            _ui.value = u.copy(results = results, done = true)
            tuner.stop()
            viewModelScope.launch { c.savePhoneDrill(DrillKind.MATCH_NOTE, 1, results.size, results.count { it }, 0, startedAt) }
        } else {
            _ui.value = u.copy(index = u.index + 1, results = results, matched = false)
            select(u.index + 1)
        }
    }

    fun start(granted: Boolean) = tuner.start(granted)
    override fun onCleared() { tuner.stop() }
}

@Composable
fun MatchNoteScreen(onBack: () -> Unit) {
    val vm = appViewModel { MatchNoteViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val reading by vm.tuner.reading.collectAsStateWithLifecycle()
    val mic by vm.micState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val request = rememberMicRequest { vm.start(it) }
    LaunchedEffect(Unit) { request() }
    DisposableEffect(Unit) { onDispose { vm.tuner.stop() } }
    ScreenScaffold(stringResource(R.string.match_note), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (ui.notes.isEmpty()) return@ScrollColumn
            if (ui.done) {
                DoneForToday(ui.results.count { it }, ui.results.size, null, null, null, onBack)
                return@ScrollColumn
            }
            MicErrorBanner(mic) { vm.tuner.stop(); vm.start(hasMicPermission(context)) }
            val written = ui.notes[ui.index]
            Text(stringResource(R.string.question_of, ui.index + 1, ui.notes.size), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.match_hint, writtenNoteLabel(vm.concertOf(written))), style = MaterialTheme.typography.titleMedium)
            StaffView(StaffLayout.singleNote(SpelledPitch.fromMidi(written)), staffSpace = 12.dp, showTime = false)
            TunerDisplay(reading, tolerance = 25.0, large = false)
            Text(stringResource(R.string.headphones_warning), style = MaterialTheme.typography.bodySmall)
            if (ui.matched) Text(stringResource(R.string.matched), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = vm::replay, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.play_reference)) }
                if (ui.matched) FilledTonalButton(onClick = { vm.next(true) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.next)) }
                else OutlinedButton(onClick = { vm.next(false) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.skip)) }
            }
        }
    }
}
