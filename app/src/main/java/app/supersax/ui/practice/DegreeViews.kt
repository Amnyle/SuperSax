package app.supersax.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.supersax.R
import app.supersax.domain.drills.ScaleDegrees
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.scale.ScaleType
import app.supersax.ui.components.scaleTypeLabel
import app.supersax.ui.drills.NoteKeypad
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.Sax

/** Name of a degree: tonic, supertonic… (the 7th is the leading tone in major, the subtonic in natural minor). */
@Composable
fun degreeName(degree: Int, type: ScaleType): String = stringResource(
    when (degree) {
        1 -> R.string.deg_name_1
        2 -> R.string.deg_name_2
        3 -> R.string.deg_name_3
        4 -> R.string.deg_name_4
        5 -> R.string.deg_name_5
        6 -> R.string.deg_name_6
        else -> if (type == ScaleType.MAJOR) R.string.deg_name_7 else R.string.deg_name_7_minor
    },
)

@Composable
private fun scaleName(q: ScaleDegrees.Question, naming: NoteNaming): String =
    stringResource(R.string.scale_name_format, naming.format(q.scale.tonic), scaleTypeLabel(q.scale.type))

/** The right answer after a wrong one, with the whole scale to see why. */
@Composable
fun degreeAnswerText(q: ScaleDegrees.Question, naming: NoteNaming): String {
    val answer = if (q.kind == ScaleDegrees.Kind.NOTE_OF_DEGREE) naming.format(q.note)
    else stringResource(R.string.deg_answer_degree, q.degree, degreeName(q.degree, q.scale.type))
    return stringResource(R.string.deg_answer, answer, scaleName(q, naming), q.notes.joinToString(" ") { naming.format(it.pitchClass) })
}

/** "In D major, which note is degree 5 (dominant)?" with the note keypad, or "which degree is A?" with degree buttons. */
@Composable
fun DegreeView(q: ScaleDegrees.Question, key: Any, enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val naming = LocalSettings.current.noteNaming
    val prompt = MaterialTheme.typography.titleLarge
    val scale = scaleName(q, naming)
    if (q.kind == ScaleDegrees.Kind.NOTE_OF_DEGREE) {
        Text(stringResource(R.string.deg_q_note, scale, q.degree, degreeName(q.degree, q.scale.type)), style = prompt)
        Text(stringResource(R.string.deg_tap_note), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
        var acc by remember(key) { mutableIntStateOf(0) }
        NoteKeypad(naming, acc, enabled, { acc = it }) { l -> onAnswer(ScaleDegrees.checkNote(q, PitchClass(l, acc))) }
    } else {
        Text(stringResource(R.string.deg_q_degree, scale, naming.format(q.note)), style = prompt)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1..4, 5..7).forEach { range ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    range.forEach { d ->
                        FilledTonalButton(onClick = { onAnswer(ScaleDegrees.checkDegree(q, d)) }, enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$d", style = MaterialTheme.typography.titleLarge)
                                Text(degreeName(d, q.scale.type), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
