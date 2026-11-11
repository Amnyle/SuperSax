package app.supersax.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.common.TunerHoldRecord
import app.supersax.ui.components.EmptyState
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.writtenNoteLabel
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.InTuneGreen
import app.supersax.ui.theme.OffPitchAmber
import kotlin.math.abs

/** Per-note tendencies over weeks: average offset and drift, recent four weeks against earlier. */
@Composable
fun TunerHistoryScreen(onBack: () -> Unit) {
    val vm = appViewModel { TunerViewModel(it) }
    val holds by vm.holds.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val fourWeeks = 28L * 24 * 3600 * 1000
    ScreenScaffold(stringResource(R.string.per_note_history), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (holds.isEmpty()) EmptyState(stringResource(R.string.tuner_history_empty))
            Text(stringResource(R.string.tuner_history_hint), style = MaterialTheme.typography.bodySmall)
            holds.groupBy { it.refMidi }.toSortedMap().forEach { (midi, list) ->
                val recent = list.filter { now - it.atMs <= fourWeeks }
                val earlier = list - recent.toSet()
                SectionCard(writtenNoteLabel(midi)) {
                    NoteRow(stringResource(R.string.last_four_weeks), recent)
                    if (earlier.isNotEmpty()) NoteRow(stringResource(R.string.earlier), earlier)
                }
            }
        }
    }
}

@Composable
private fun NoteRow(label: String, holds: List<TunerHoldRecord>) {
    if (holds.isEmpty()) { Text("$label: –"); return }
    val mean = holds.map { it.meanCents }.average()
    val drift = holds.map { it.driftCents }.average()
    val share = holds.map { it.inTuneShare }.average()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.note_tendency, label, holds.size, signed(mean), signed(drift), (share * 100).toInt()),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        val axis = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(Modifier.width(80.dp).height(16.dp)) {
            drawLine(axis, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f)
            val x = size.width / 2 + (mean.coerceIn(-40.0, 40.0) / 40.0 * size.width / 2).toFloat()
            drawLine(if (abs(mean) <= 10) InTuneGreen else OffPitchAmber, Offset(size.width / 2, size.height / 2), Offset(x, size.height / 2), 10f)
        }
    }
}
