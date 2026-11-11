package app.supersax.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.supersax.domain.drills.DrillRules
import app.supersax.domain.drills.ScaleRecognition
import app.supersax.domain.drills.ScaleRecognition.Kind
import app.supersax.domain.library.Fraction
import app.supersax.domain.library.Measure
import app.supersax.domain.library.Score
import app.supersax.domain.library.ScoreNote
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.scale.ScaleType
import app.supersax.notation.StaffView
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.scaleTypeLabel
import app.supersax.ui.drills.DoneForToday
import app.supersax.ui.drills.NoteKeypad
import app.supersax.ui.drills.lastLevel
import app.supersax.ui.drills.savePhoneDrill
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ScaleUi(
    val loading: Boolean = true, val level: Int = 1, val questions: List<ScaleRecognition.Question> = emptyList(),
    val index: Int = 0, val correct: Int = 0, val last: Boolean? = null, val times: List<Long> = emptyList(),
    val done: Boolean = false, val nextLevel: Int? = null,
)

class ScaleRecognitionViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ScaleUi())
    val ui = _ui.asStateFlow()
    private val startedAt = c.clock.nowMs()
    private var shownAt = 0L

    init {
        viewModelScope.launch {
            val level = lastLevel(c.progress.drills.first(), DrillKind.SCALE_RECOGNITION, default = (c.practice.playerNow().theoryLevel).coerceIn(1, 3))
            _ui.value = ScaleUi(false, level, ScaleRecognition.round(level, c.clock.nowMs()))
            shownAt = c.clock.elapsedMs()
        }
    }

    fun answerName(tonic: PitchClass, type: ScaleType) = record(ScaleRecognition.checkName(current(), tonic, type))
    fun answerBuild(a: Answer.Notes) = record(ScaleRecognition.checkBuild(current(), a.values))
    private fun current() = _ui.value.questions[_ui.value.index]

    private fun record(ok: Boolean) {
        val u = _ui.value
        if (u.last != null) return
        _ui.value = u.copy(last = ok, correct = u.correct + if (ok) 1 else 0, times = u.times + (c.clock.elapsedMs() - shownAt))
    }

    fun next() {
        val u = _ui.value
        if (u.index + 1 < u.questions.size) { _ui.value = u.copy(index = u.index + 1, last = null); shownAt = c.clock.elapsedMs(); return }
        val mean = u.times.average().toLong()
        val next = DrillRules.nextLevel(u.level, u.correct, u.questions.size, mean, 15_000)
        _ui.value = u.copy(done = true, nextLevel = next)
        viewModelScope.launch { c.savePhoneDrill(DrillKind.SCALE_RECOGNITION, u.level, u.questions.size, u.correct, mean, startedAt, "next=$next") }
    }
}

/** Scale recognition (visual): name from a key signature, name from the notes, or build from a root. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleRecognitionScreen(onBack: () -> Unit) {
    val vm = appViewModel { ScaleRecognitionViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val naming = LocalSettings.current.noteNaming
    ScreenScaffold(stringResource(R.string.ex_scale_recognition), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (ui.loading) return@ScrollColumn
            if (ui.done) { DoneForToday(ui.correct, ui.questions.size, ui.times.average().toLong(), ui.level, ui.nextLevel, onBack); return@ScrollColumn }
            val q = ui.questions[ui.index]
            Text(stringResource(R.string.question_of, ui.index + 1, ui.questions.size) + " · " + stringResource(R.string.level_n, ui.level), style = MaterialTheme.typography.labelLarge)
            val typeName = scaleTypeLabel(q.scale.type)
            when (q.kind) {
                Kind.NAME_FROM_SIGNATURE -> {
                    Text(stringResource(R.string.sr_from_signature, typeName), style = MaterialTheme.typography.titleMedium)
                    StaffView(Score(q.signature.fifths, 4, 4, 60, listOf(Measure(emptyList()))), staffSpace = 12.dp, showTime = false)
                }
                Kind.NAME_FROM_NOTES -> {
                    Text(stringResource(R.string.sr_from_notes), style = MaterialTheme.typography.titleMedium)
                    val notes = q.notes.map { ScoreNote(it, Fraction(1, 4)) }
                    StaffView(Score(0, 4, 4, 60, notes.chunked(4).map { Measure(it) }), staffSpace = 10.dp, showTime = false)
                }
                Kind.BUILD -> Text(stringResource(R.string.sr_build, naming.format(q.scale.tonic), typeName, q.scale.type.stepPattern ?: ""),
                    style = MaterialTheme.typography.titleMedium)
            }
            val enabled = ui.last == null
            if (q.kind == Kind.BUILD) {
                AnswerInput(app.supersax.domain.drills.QuestionType.NOTES, "sr-${ui.index}", enabled) { vm.answerBuild(it as Answer.Notes) }
            } else {
                var acc by remember(ui.index) { mutableIntStateOf(0) }
                var type by remember(ui.index) { mutableStateOf(if (q.kind == Kind.NAME_FROM_SIGNATURE) q.scale.type else ScaleType.MAJOR) }
                if (q.kind == Kind.NAME_FROM_NOTES) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScaleRecognition.types(ui.level).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(scaleTypeLabel(t)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text(stringResource(R.string.sr_tap_tonic), style = MaterialTheme.typography.bodyMedium)
                NoteKeypad(naming, acc, enabled, { acc = it }) { l -> vm.answerName(PitchClass(l, acc), type) }
            }
            ui.last?.let { ok ->
                Text(
                    if (ok) stringResource(R.string.correct)
                    else stringResource(R.string.it_was, stringResource(R.string.scale_name_format, naming.format(q.scale.tonic), typeName) +
                        if (q.kind == Kind.BUILD) ": " + q.notes.joinToString(" ") { naming.format(it.pitchClass) } else ""),
                    color = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge,
                )
                Button(onClick = vm::next, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.next)) }
            }
        }
    }
}
