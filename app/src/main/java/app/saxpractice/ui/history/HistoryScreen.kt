package app.saxpractice.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.R
import app.saxpractice.domain.common.SessionRecord
import app.saxpractice.domain.goals.GoalEvaluator
import app.saxpractice.ui.components.EmptyState
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.components.blockKindLabel
import app.saxpractice.ui.components.exerciseTypeLabel
import app.saxpractice.ui.components.formatClock
import app.saxpractice.ui.components.scaleLabel
import app.saxpractice.ui.nav.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val sessions = c.practice.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: String) = viewModelScope.launch { c.practice.deleteSession(id) }
}

/** Session log, newest first, grouped by week. Tap a session for details. */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val dateFmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    val weekFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    ScreenScaffold(stringResource(R.string.history), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (sessions.isEmpty()) EmptyState(stringResource(R.string.history_empty))
            sessions.groupBy { GoalEvaluator.weekStart(Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate()) }.forEach { (week, list) ->
                Text(stringResource(R.string.week_of, week.format(weekFmt), list.sumOf { it.playedSec } / 60), style = MaterialTheme.typography.titleSmall)
                list.forEach { s ->
                    SectionCard(modifier = Modifier.clickable { open = if (open == s.id) null else s.id }) {
                        Row {
                            Text(Instant.ofEpochMilli(s.startedAtMs).atZone(zone).format(dateFmt), Modifier.weight(1f))
                            Text("${formatClock(s.playedSec.toLong())} / ${formatClock(s.plannedSec.toLong())}")
                        }
                        if (s.phone) Text(stringResource(R.string.phone_session), style = MaterialTheme.typography.bodySmall)
                        if (open == s.id) Details(s) { confirmDelete = s.id }
                    }
                }
            }
        }
    }
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_session_q)) },
            confirmButton = { TextButton(onClick = { vm.delete(id); confirmDelete = null; open = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Details(s: SessionRecord, onDelete: () -> Unit) {
    s.blocks.forEach { b ->
        val extra = buildString {
            if (b.scaleKey != null) append(" · ").append(scaleLabel(b.scaleKey))
            if (b.bpm != null) append(" · ").append(b.bpm).append(" BPM")
        }
        Text("${blockKindLabel(b.blockKind)} – ${exerciseTypeLabel(b.exerciseType)}$extra: ${formatClock(b.actualSec.toLong())} / ${formatClock(b.plannedSec.toLong())}" +
            if (b.lipsTired) " · " + stringResource(R.string.lips_tired) else "", style = MaterialTheme.typography.bodySmall)
    }
    if (s.runs.isNotEmpty()) Text(stringResource(R.string.runs_logged, s.runs.size, s.runs.count { it.clean }), style = MaterialTheme.typography.bodySmall)
    s.tiredness?.let { Text(stringResource(R.string.tiredness_value, it), style = MaterialTheme.typography.bodySmall) }
    if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete)) }
}
