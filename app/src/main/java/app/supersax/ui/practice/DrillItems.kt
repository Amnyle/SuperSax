package app.supersax.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.Answer
import app.supersax.domain.drills.FingeringEntry
import app.supersax.domain.drills.QuestionType
import app.supersax.domain.drills.Quiz
import app.supersax.domain.drills.QuizQuestion
import app.supersax.domain.drills.ReadingDrill
import app.supersax.domain.drills.SaxKey
import app.supersax.domain.drills.ScaleRecognition
import app.supersax.domain.drills.ScaleRecognition.Kind
import app.supersax.domain.library.Fraction
import app.supersax.domain.library.Measure
import app.supersax.domain.library.Score
import app.supersax.domain.library.ScoreNote
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.scale.ScaleType
import app.supersax.notation.StaffLayout
import app.supersax.notation.StaffView
import app.supersax.ui.components.scaleTypeLabel
import app.supersax.ui.drills.NoteKeypad
import app.supersax.ui.learn.AnswerInput
import app.supersax.ui.learn.FingeringDiagram
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.Sax

/** One question of a phone drill, whatever the drill. Level rounds and the daily mix are lists of these. */
sealed interface DrillItem {
    val kind: DrillKind

    data class Reading(val pitch: SpelledPitch) : DrillItem { override val kind get() = DrillKind.READING }
    data class Scale(val q: ScaleRecognition.Question, val level: Int) : DrillItem { override val kind get() = DrillKind.SCALE_RECOGNITION }
    data class Fingering(val entry: FingeringEntry, val tapKeys: Boolean) : DrillItem { override val kind get() = DrillKind.FINGERING }
    data class Theory(val lessonId: String, val q: QuizQuestion) : DrillItem { override val kind get() = DrillKind.THEORY_QUIZ }
}

/** The right answer, as shown after a wrong one. */
@Composable
fun expectedText(item: DrillItem): String {
    val naming = LocalSettings.current.noteNaming
    return when (item) {
        is DrillItem.Reading -> naming.format(item.pitch.pitchClass)
        is DrillItem.Scale -> stringResource(R.string.scale_name_format, naming.format(item.q.scale.tonic), scaleTypeLabel(item.q.scale.type)) +
            if (item.q.kind == Kind.BUILD) ": " + item.q.notes.joinToString(" ") { naming.format(it.pitchClass) } else ""
        is DrillItem.Fingering -> naming.format(SpelledPitch.parse(item.entry.note))
        is DrillItem.Theory -> Quiz.expectedText(item.q, LocaleHelper.effective(LocalContext.current), naming)
    }
}

/** A short explanation under the feedback (theory only). */
@Composable
fun explanationText(item: DrillItem): String? =
    (item as? DrillItem.Theory)?.q?.explanation?.get(LocaleHelper.effective(LocalContext.current))

/** Extra help in the feedback after a wrong answer: the right fingering for a tapped-keys question. */
@Composable
fun FeedbackExtra(item: DrillItem) {
    if (item is DrillItem.Fingering && item.tapKeys) FingeringDiagram(item.entry.keys, stringResource(R.string.fing_diagram_desc))
}

/**
 * The question and its answer input. [key] changes with each question so inputs reset. [onAnswer] gets
 * whether the answer was right; it is called once (the caller disables input afterwards with [enabled]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrillItemView(item: DrillItem, key: Any, enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val naming = LocalSettings.current.noteNaming
    val language = LocaleHelper.effective(LocalContext.current)
    val prompt = MaterialTheme.typography.titleLarge
    when (item) {
        is DrillItem.Reading -> {
            Text(stringResource(R.string.q_reading), style = prompt)
            StaffView(StaffLayout.singleNote(item.pitch), staffSpace = 14.dp, showTime = false, description = stringResource(R.string.staff_note_description))
            var acc by remember(key) { mutableIntStateOf(0) }
            NoteKeypad(naming, acc, enabled, { acc = it }) { l -> onAnswer(ReadingDrill.isCorrect(item.pitch, PitchClass(l, acc))) }
        }
        is DrillItem.Scale -> {
            val q = item.q
            val typeName = scaleTypeLabel(q.scale.type)
            when (q.kind) {
                Kind.NAME_FROM_SIGNATURE -> {
                    Text(stringResource(R.string.sr_from_signature, typeName), style = prompt)
                    StaffView(Score(q.signature.fifths, 4, 4, 60, listOf(Measure(emptyList()))), staffSpace = 12.dp, showTime = false)
                }
                Kind.NAME_FROM_NOTES -> {
                    Text(stringResource(R.string.sr_from_notes), style = prompt)
                    val notes = q.notes.map { ScoreNote(it, Fraction(1, 4)) }
                    StaffView(Score(0, 4, 4, 60, notes.chunked(4).map { Measure(it) }), staffSpace = 10.dp, showTime = false)
                }
                Kind.BUILD -> Text(stringResource(R.string.sr_build, naming.format(q.scale.tonic), typeName, q.scale.type.stepPattern ?: ""), style = prompt)
            }
            if (q.kind == Kind.BUILD) {
                AnswerInput(QuestionType.NOTES, "sr-$key", enabled) { a -> onAnswer(ScaleRecognition.checkBuild(q, (a as Answer.Notes).values)) }
            } else {
                var acc by remember(key) { mutableIntStateOf(0) }
                var type by remember(key) { mutableStateOf(if (q.kind == Kind.NAME_FROM_SIGNATURE) q.scale.type else ScaleType.MAJOR) }
                if (q.kind == Kind.NAME_FROM_NOTES) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScaleRecognition.types(item.level).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(scaleTypeLabel(t)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text(stringResource(R.string.sr_tap_tonic), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
                NoteKeypad(naming, acc, enabled, { acc = it }) { l -> onAnswer(ScaleRecognition.checkName(q, PitchClass(l, acc), type)) }
            }
        }
        is DrillItem.Fingering -> {
            val pitch = SpelledPitch.parse(item.entry.note)
            if (item.tapKeys) {
                Text(stringResource(R.string.fing_tap_keys, naming.format(pitch)), style = prompt)
                StaffView(StaffLayout.singleNote(pitch), staffSpace = 10.dp, showTime = false)
                var keys by remember(key) { mutableStateOf(emptySet<SaxKey>()) }
                FingeringDiagram(keys, stringResource(R.string.fing_diagram_desc)) { k -> if (enabled) keys = if (k in keys) keys - k else keys + k }
                if (enabled) Button(
                    onClick = { onAnswer(app.supersax.domain.drills.FingeringDrill.checkKeys(item.entry, keys)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent),
                ) { Text(stringResource(R.string.check), style = MaterialTheme.typography.titleMedium) }
            } else {
                Text(stringResource(R.string.fing_name_note), style = prompt)
                FingeringDiagram(item.entry.keys, stringResource(R.string.fing_diagram_desc))
                AnswerInput(QuestionType.STAFF_NOTE, "fing-$key", enabled) { a -> onAnswer((a as Answer.StaffNote).value.midi == pitch.midi) }
            }
        }
        is DrillItem.Theory -> {
            Text(item.q.prompt.get(language), style = prompt)
            item.q.staff?.let { StaffView(it.toScore(), showTime = !it.free) }
            AnswerInput(item.q.type, "${item.lessonId}/${item.q.id}/$key", enabled) { a -> onAnswer(Quiz.check(item.q, a, language)) }
        }
    }
}
