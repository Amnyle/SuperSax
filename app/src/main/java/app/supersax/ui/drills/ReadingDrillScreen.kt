package app.supersax.ui.drills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.DrillRules
import app.supersax.domain.drills.ReadingDrill
import app.supersax.domain.pitch.Letter
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.notation.StaffLayout
import app.supersax.notation.StaffView
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReadingUi(
    val loading: Boolean = true,
    val level: Int = 1,
    val questions: List<SpelledPitch> = emptyList(),
    val index: Int = 0,
    val correct: Int = 0,
    val feedback: Boolean? = null,
    val accidental: Int = 0,
    val times: List<Long> = emptyList(),
    val done: Boolean = false,
    val nextLevel: Int? = null,
)

/** Fixed-length note-reading round; level adapts from one round to the next. */
class ReadingDrillViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ReadingUi())
    val ui = _ui.asStateFlow()
    private var shownAt = 0L
    private val startedAt = c.clock.nowMs()

    init {
        viewModelScope.launch {
            val level = lastLevel(c.progress.drills.first(), DrillKind.READING, default = (c.practice.playerNow().readingLevel).coerceIn(1, 3))
            _ui.value = ReadingUi(false, level, ReadingDrill.round(level, c.clock.nowMs()))
            shownAt = c.clock.elapsedMs()
        }
    }

    fun setAccidental(a: Int) { _ui.value = _ui.value.copy(accidental = a) }

    fun answer(letter: Letter) {
        val u = _ui.value
        if (u.feedback != null || u.done) return
        val q = u.questions[u.index]
        val ok = ReadingDrill.isCorrect(q, PitchClass(letter, u.accidental))
        _ui.value = u.copy(feedback = ok, correct = u.correct + if (ok) 1 else 0, times = u.times + (c.clock.elapsedMs() - shownAt))
    }

    fun next() {
        val u = _ui.value
        if (u.index + 1 >= u.questions.size) finish() else {
            _ui.value = u.copy(index = u.index + 1, feedback = null, accidental = 0)
            shownAt = c.clock.elapsedMs()
        }
    }

    private fun finish() {
        val u = _ui.value
        val mean = u.times.average().toLong()
        val next = DrillRules.nextLevel(u.level, u.correct, u.questions.size, mean)
        _ui.value = u.copy(done = true, nextLevel = next)
        viewModelScope.launch { c.savePhoneDrill(DrillKind.READING, u.level, u.questions.size, u.correct, mean, startedAt, "next=$next") }
    }
}

@Composable
fun ReadingDrillScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReadingDrillViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val naming = LocalSettings.current.noteNaming
    ScreenScaffold(stringResource(R.string.ex_reading), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (ui.loading) return@ScrollColumn
            if (ui.done) {
                DoneForToday(ui.correct, ui.questions.size, ui.times.average().toLong(), ui.level, ui.nextLevel, onBack)
                return@ScrollColumn
            }
            val q = ui.questions[ui.index]
            Text(stringResource(R.string.question_of, ui.index + 1, ui.questions.size) + " · " + stringResource(R.string.level_n, ui.level),
                style = MaterialTheme.typography.labelLarge)
            StaffView(StaffLayout.singleNote(q), staffSpace = 14.dp, showTime = false, description = stringResource(R.string.staff_note_description))
            NoteKeypad(naming, ui.accidental, enabled = ui.feedback == null, onAccidental = vm::setAccidental, onLetter = vm::answer)
            ui.feedback?.let { ok ->
                Text(
                    if (ok) stringResource(R.string.correct) else stringResource(R.string.it_was, naming.format(q.pitchClass)),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                )
                Button(onClick = vm::next, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.next)) }
            }
        }
    }
}

/** Seven note-name keys in the chosen naming system, plus a ♭ / ♮ / ♯ toggle. */
@Composable
fun NoteKeypad(naming: NoteNaming, accidental: Int, enabled: Boolean, onAccidental: (Int) -> Unit, onLetter: (Letter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-1 to "♭", 0 to "♮", 1 to "♯").forEach { (a, sym) ->
            FilterChip(selected = accidental == a, onClick = { onAccidental(a) }, label = { Text(sym, style = MaterialTheme.typography.titleLarge) },
                modifier = Modifier.heightIn(min = 56.dp))
        }
    }
    listOf(Letter.entries.take(4), Letter.entries.drop(4)).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { l ->
                Button(onClick = { onLetter(l) }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                    Text(naming.letterName(l), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
