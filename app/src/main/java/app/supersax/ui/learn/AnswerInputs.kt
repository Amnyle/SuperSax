package app.supersax.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.supersax.R
import app.supersax.domain.drills.Answer
import app.supersax.domain.drills.QuestionType
import app.supersax.domain.pitch.PitchClass
import app.supersax.notation.StaffGeometry
import app.supersax.notation.StaffInput
import app.supersax.ui.drills.NoteKeypad
import app.supersax.ui.nav.LocalSettings

/**
 * Free-form answer input for a question type; calls [onAnswer] with the built answer when the player
 * presses Check. No multiple choice anywhere.
 */
@Composable
fun AnswerInput(type: QuestionType, key: Any, enabled: Boolean, onAnswer: (Answer) -> Unit) {
    val naming = LocalSettings.current.noteNaming
    when (type) {
        QuestionType.TEXT, QuestionType.NUMBER -> {
            var text by remember(key) { mutableStateOf("") }
            OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.your_answer)) }, singleLine = true, enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = if (type == QuestionType.NUMBER) KeyboardType.Number else KeyboardType.Text))
            CheckButton(enabled && text.isNotBlank()) {
                onAnswer(if (type == QuestionType.NUMBER) Answer.Number(text.trim().toIntOrNull() ?: Int.MIN_VALUE) else Answer.Text(text))
            }
        }
        QuestionType.NOTE -> {
            var acc by remember(key) { mutableIntStateOf(0) }
            NoteKeypad(naming, acc, enabled, { acc = it }) { l -> onAnswer(Answer.Note(PitchClass(l, acc))) }
        }
        QuestionType.NOTES -> {
            var acc by remember(key) { mutableIntStateOf(0) }
            val notes = remember(key) { mutableStateListOf<PitchClass>() }
            Text(if (notes.isEmpty()) stringResource(R.string.tap_notes_in_order) else notes.joinToString("  ") { naming.format(it) },
                style = MaterialTheme.typography.titleLarge)
            NoteKeypad(naming, acc, enabled, { acc = it }) { l -> notes += PitchClass(l, acc); acc = 0 }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { if (notes.isNotEmpty()) notes.removeAt(notes.lastIndex) }, enabled = enabled) { Text(stringResource(R.string.undo)) }
                TextButton(onClick = { notes.clear() }, enabled = enabled) { Text(stringResource(R.string.clear)) }
            }
            CheckButton(enabled && notes.isNotEmpty()) { onAnswer(Answer.Notes(notes.toList())) }
        }
        QuestionType.STAFF_NOTE -> {
            var step by remember(key) { mutableStateOf<Int?>(null) }
            var alter by remember(key) { mutableIntStateOf(0) }
            StaffInput(step, alter, stringResource(R.string.tap_the_staff)) { if (enabled) step = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to "♭", 0 to "♮", 1 to "♯").forEach { (a, sym) ->
                    FilterChip(selected = alter == a, onClick = { alter = a }, label = { Text(sym, style = MaterialTheme.typography.titleLarge) },
                        modifier = Modifier.heightIn(min = 56.dp))
                }
            }
            step?.let { Text(naming.format(StaffGeometry.pitchAt(it, alter)), style = MaterialTheme.typography.titleLarge) }
            CheckButton(enabled && step != null) { onAnswer(Answer.StaffNote(StaffGeometry.pitchAt(step!!, alter))) }
        }
    }
}

@Composable
private fun CheckButton(enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Button(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.check)) }
}

