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
