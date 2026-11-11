package app.supersax.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.Answer
import app.supersax.domain.drills.FingeringDrill
import app.supersax.domain.drills.FingeringEntry
import app.supersax.domain.drills.QuestionType
import app.supersax.domain.drills.SaxKey
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.notation.StaffLayout
import app.supersax.notation.StaffView
import app.supersax.ui.components.ErrorBanner
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.drills.DoneForToday
import app.supersax.ui.drills.savePhoneDrill
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FingeringUi(val questions: List<Pair<FingeringEntry, Boolean>> = emptyList(), val index: Int = 0, val correct: Int = 0,
    val last: Boolean? = null, val done: Boolean = false)

class FingeringViewModel(private val c: AppContainer) : ViewModel() {
    val chart = c.content.fingerings
    private val _ui = MutableStateFlow(FingeringUi(FingeringDrill.round(chart, seed = c.clock.nowMs())))
    val ui = _ui.asStateFlow()
    private val startedAt = c.clock.nowMs()

    fun answerKeys(keys: Set<SaxKey>) = record(FingeringDrill.checkKeys(current(), keys))
    fun answerNote(p: SpelledPitch) = record(p.midi == SpelledPitch.parse(current().note).midi)
    private fun current() = _ui.value.questions[_ui.value.index].first
    private fun record(ok: Boolean) { val u = _ui.value; if (u.last == null) _ui.value = u.copy(last = ok, correct = u.correct + if (ok) 1 else 0) }

    fun next() {
        val u = _ui.value
        if (u.index + 1 < u.questions.size) { _ui.value = u.copy(index = u.index + 1, last = null); return }
        _ui.value = u.copy(done = true)
        viewModelScope.launch { c.savePhoneDrill(DrillKind.FINGERING, 1, u.questions.size, u.correct, 0, startedAt) }
    }
}

/** Fingering trainer: tap the keys of a note, or name the note of a fingering. Plus the full chart. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FingeringScreen(onBack: () -> Unit) {
    val vm = appViewModel { FingeringViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val naming = LocalSettings.current.noteNaming
    var chartMode by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.ex_fingering), onBack = onBack, actions = {
        TextButton(onClick = { chartMode = !chartMode }) { Text(stringResource(if (chartMode) R.string.drill else R.string.chart)) }
    }) { padding ->
        ScrollColumn(padding) {
            if (chartMode) { Chart(vm); return@ScrollColumn }
            if (ui.done) { DoneForToday(ui.correct, ui.questions.size, null, null, null, onBack); return@ScrollColumn }
            val (entry, tapKeys) = ui.questions[ui.index]
            val pitch = SpelledPitch.parse(entry.note)
            Text(stringResource(R.string.question_of, ui.index + 1, ui.questions.size), style = MaterialTheme.typography.labelLarge)
            if (tapKeys) {
                Text(stringResource(R.string.fing_tap_keys, naming.format(pitch)), style = MaterialTheme.typography.titleMedium)
                StaffView(StaffLayout.singleNote(pitch), staffSpace = 10.dp, showTime = false)
                var keys by remember(ui.index) { mutableStateOf(emptySet<SaxKey>()) }
                FingeringDiagram(keys, stringResource(R.string.fing_diagram_desc)) { k -> if (ui.last == null) keys = if (k in keys) keys - k else keys + k }
                if (ui.last == null) Button(onClick = { vm.answerKeys(keys) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.check)) }
            } else {
                Text(stringResource(R.string.fing_name_note), style = MaterialTheme.typography.titleMedium)
                FingeringDiagram(entry.keys, stringResource(R.string.fing_diagram_desc))
                AnswerInput(QuestionType.STAFF_NOTE, "fing-${ui.index}", ui.last == null) { vm.answerNote((it as Answer.StaffNote).value) }
            }
            ui.last?.let { ok ->
                Text(if (ok) stringResource(R.string.correct) else stringResource(R.string.it_was, naming.format(pitch)),
                    color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                if (!ok && tapKeys) FingeringDiagram(entry.keys, stringResource(R.string.fing_diagram_desc))
                Button(onClick = vm::next, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.next)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chart(vm: FingeringViewModel) {
    val naming = LocalSettings.current.noteNaming
    var selected by rememberSaveable { mutableStateOf("G4") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        vm.chart.entries.forEach { e ->
            FilterChip(selected = selected == e.note, onClick = { selected = e.note }, label = { Text(naming.format(SpelledPitch.parse(e.note))) })
        }
    }
    val e = vm.chart.forNote(selected) ?: return
    if (!e.verified) ErrorBanner(stringResource(R.string.fing_unverified))
    StaffView(StaffLayout.singleNote(SpelledPitch.parse(e.note)), staffSpace = 10.dp, showTime = false)
    FingeringDiagram(e.keys, stringResource(R.string.fing_diagram_desc))
    e.alternates.forEach { alt ->
        Text(stringResource(R.string.fing_alternate), style = MaterialTheme.typography.titleSmall)
        FingeringDiagram(alt, stringResource(R.string.fing_diagram_desc))
    }
}
