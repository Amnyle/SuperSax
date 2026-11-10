package app.saxpractice.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.saxpractice.R
import app.saxpractice.domain.planner.StepKind
import app.saxpractice.ui.components.BigButton
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.components.blockKindLabel
import app.saxpractice.ui.components.formatClock
import app.saxpractice.ui.nav.appViewModel

/** End of session: planned vs actual per block, a 1–5 tiredness rating and an optional note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionSummaryScreen(onSaved: () -> Unit) {
    val vm = appViewModel { SessionViewModel(it) }
    val run by vm.run.collectAsStateWithLifecycle()
    var tiredness by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val r = run
    if (r == null) { LaunchedEffect(Unit) { onSaved() }; return }
    ScreenScaffold(stringResource(R.string.session_done)) { padding ->
        ScrollColumn(padding) {
            val byBlock = r.logs.filter { r.steps.getOrNull(it.stepIndex)?.kind == StepKind.PLAY }
                .groupBy { r.steps[it.stepIndex].blockKind }
            val planned = byBlock.values.flatten().sumOf { it.plannedSec }
            val actual = byBlock.values.flatten().sumOf { it.actualSec }
            Text(stringResource(R.string.planned_vs_actual, formatClock(planned.toLong()), formatClock(actual.toLong())),
                style = MaterialTheme.typography.titleLarge)
            SectionCard(stringResource(R.string.per_block)) {
                byBlock.forEach { (kind, logs) ->
                    Row {
                        Text(blockKindLabel(kind), Modifier.weight(1f))
                        Text("${formatClock(logs.sumOf { it.actualSec }.toLong())} / ${formatClock(logs.sumOf { it.plannedSec }.toLong())}")
                    }
                    if (logs.any { it.lipsTired }) Text(stringResource(R.string.lips_tired_logged), style = MaterialTheme.typography.bodySmall)
                }
                val runs = r.runs
                if (runs.isNotEmpty()) Text(stringResource(R.string.runs_logged, runs.size, runs.count { it.clean }))
            }
            Text(stringResource(R.string.tiredness_q), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { v ->
                    FilterChip(selected = tiredness == v, onClick = { tiredness = v }, label = { Text("$v") },
                        modifier = Modifier.heightIn(min = 48.dp).size(width = 56.dp, height = 48.dp))
                }
            }
            Text(stringResource(R.string.tiredness_scale), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note_optional)) }, minLines = 2,
                modifier = Modifier.heightIn(min = 96.dp))
            BigButton(stringResource(R.string.save_session), { vm.complete(tiredness, note, onSaved) })
            TextButton(onClick = { vm.discard(onSaved) }) { Text(stringResource(R.string.discard_session)) }
        }
    }
}
