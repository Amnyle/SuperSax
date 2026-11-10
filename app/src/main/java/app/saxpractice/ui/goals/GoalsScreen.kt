package app.saxpractice.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.saxpractice.domain.common.GoalRecord
import app.saxpractice.domain.common.GoalType
import app.saxpractice.domain.common.PieceStage
import app.saxpractice.domain.goals.GoalEvaluator
import app.saxpractice.domain.goals.GoalInputs
import app.saxpractice.domain.goals.GoalProgress
import app.saxpractice.ui.components.EmptyState
import app.saxpractice.ui.components.ScalePickerDialog
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.components.Stepper
import app.saxpractice.ui.components.scaleLabel
import app.saxpractice.ui.nav.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class GoalsViewModel(private val c: AppContainer) : ViewModel() {
    val goals = combine(c.practice.goals, c.practice.sessions, c.practice.player, c.library.progress, c.media.recordings) { g, s, p, pp, r ->
        val inputs = GoalInputs(s, p, pp, r)
        g.filter { !it.archived }.map { it to GoalEvaluator.progress(it, inputs, c.clock.nowMs(), ZoneId.systemDefault()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(g: GoalRecord) = viewModelScope.launch { c.practice.saveGoal(g.copy(id = UUID.randomUUID().toString(), createdAtMs = c.clock.nowMs())) }
    fun archive(g: GoalRecord) = viewModelScope.launch { c.practice.saveGoal(g.copy(archived = true)) }
    fun delete(id: String) = viewModelScope.launch { c.practice.deleteGoal(id) }
    fun update(g: GoalRecord) = viewModelScope.launch { c.practice.saveGoal(g) }
}

/** Goals of four types; progress is computed from the logs. Completion is shown calmly. */
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val vm = appViewModel { GoalsViewModel(it) }
    val goals by vm.goals.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.goals), onBack = onBack, actions = {
        TextButton(onClick = { adding = true }) { Text(stringResource(R.string.add)) }
    }) { padding ->
        ScrollColumn(padding) {
            if (goals.isEmpty()) EmptyState(stringResource(R.string.goals_empty), stringResource(R.string.add_goal)) { adding = true }
            goals.forEach { (g, p) -> GoalCard(g, p, vm) }
        }
    }
    if (adding) AddGoalDialog(onAdd = { vm.add(it); adding = false }, onDismiss = { adding = false })
}

@Composable
private fun GoalCard(g: GoalRecord, p: GoalProgress, vm: GoalsViewModel) {
    SectionCard(g.title) {
        Text(goalDetail(g, p), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { p.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
        if (p.done) Text(stringResource(R.string.goal_reached), color = MaterialTheme.colorScheme.tertiary)
        g.deadlineEpochDay?.let { Text(stringResource(R.string.goal_by, LocalDate.ofEpochDay(it).toString()), style = MaterialTheme.typography.bodySmall) }
        GoalExtraSlot(g, vm::update)
        Row {
            TextButton(onClick = { vm.archive(g) }) { Text(stringResource(R.string.archive)) }
            TextButton(onClick = { vm.delete(g.id) }) { Text(stringResource(R.string.delete)) }
        }
    }
}

@Composable
private fun goalDetail(g: GoalRecord, p: GoalProgress): String = when (g.type) {
    GoalType.TIME -> stringResource(R.string.goal_time_detail, p.current, p.target)
    GoalType.TEMPO -> stringResource(R.string.goal_tempo_detail, scaleLabel(g.scaleKey), p.current, p.target)
    GoalType.SONG_MILESTONE -> stringResource(R.string.goal_song_detail, stageLabel(PieceStage.entries[p.current.coerceIn(0, 3)]), stageLabel(PieceStage.entries[p.target.coerceIn(0, 3)]))
    GoalType.RECORDING -> stringResource(if (p.done) R.string.goal_recording_done else R.string.goal_recording_todo, g.benchmarkKey ?: "")
}

@Composable
fun stageLabel(s: PieceStage): String = stringResource(
    when (s) {
        PieceStage.NOT_STARTED -> R.string.stage_not_started
        PieceStage.LEARNING -> R.string.stage_learning
        PieceStage.PLAYABLE -> R.string.stage_playable
        PieceStage.CLEAN_AT_TEMPO -> R.string.stage_clean
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGoalDialog(onAdd: (GoalRecord) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(GoalType.TIME) }
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(90) }
    var scaleKey by remember { mutableStateOf<String?>(null) }
    var bpm by remember { mutableStateOf(80) }
    var weeks by remember { mutableStateOf(0) }
    var benchmark by remember { mutableStateOf("") }
    var pickScale by remember { mutableStateOf(false) }
    val typeLabels = mapOf(
        GoalType.TIME to R.string.goal_type_time, GoalType.TEMPO to R.string.goal_type_tempo,
        GoalType.SONG_MILESTONE to R.string.goal_type_song, GoalType.RECORDING to R.string.goal_type_recording,
    )
    val defaultTitle = when (type) {
        GoalType.TIME -> stringResource(R.string.goal_time_title, minutes)
        GoalType.TEMPO -> stringResource(R.string.goal_tempo_title, scaleLabel(scaleKey), bpm)
        else -> title
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_goal)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(typeLabels.getValue(t))) })
                    }
                }
                when (type) {
                    GoalType.TIME -> Stepper(stringResource(R.string.weekly_minutes), minutes, { minutes = it }, 10..600, 10)
                    GoalType.TEMPO -> {
                        TextButton(onClick = { pickScale = true }) { Text(scaleKey?.let { scaleLabel(it) } ?: stringResource(R.string.pick_scale)) }
                        Stepper(stringResource(R.string.bpm), bpm, { bpm = it }, 40..240, 5)
                    }
                    GoalType.SONG_MILESTONE -> OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.onb_song_title)) }, singleLine = true)
                    GoalType.RECORDING -> {
                        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.goal_title)) }, singleLine = true)
                        OutlinedTextField(benchmark, { benchmark = it }, label = { Text(stringResource(R.string.benchmark_key)) }, singleLine = true)
                    }
                }
                if (type != GoalType.TIME) Stepper(stringResource(R.string.deadline_weeks), weeks, { weeks = it }, 0..52) {
                    if (it == 0) "–" else it.toString()
                }
            }
        },
        confirmButton = {
            val valid = when (type) {
                GoalType.TIME -> true
                GoalType.TEMPO -> scaleKey != null
                GoalType.SONG_MILESTONE -> title.isNotBlank()
                GoalType.RECORDING -> title.isNotBlank() && benchmark.isNotBlank()
            }
            Button(enabled = valid, onClick = {
                onAdd(GoalRecord(
                    id = "", type = type, title = defaultTitle.ifBlank { title }, createdAtMs = 0,
                    deadlineEpochDay = if (weeks > 0) LocalDate.now().plusWeeks(weeks.toLong()).toEpochDay() else null,
                    weeklyMinutes = if (type == GoalType.TIME) minutes else null,
                    scaleKey = if (type == GoalType.TEMPO) scaleKey else null,
                    bpm = if (type == GoalType.TEMPO) bpm else null,
                    stage = if (type == GoalType.SONG_MILESTONE) PieceStage.CLEAN_AT_TEMPO.name else null,
                    benchmarkKey = if (type == GoalType.RECORDING) benchmark.trim() else null,
                ))
            }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickScale) ScalePickerDialog(onPick = { scaleKey = it; pickScale = false }, onDismiss = { pickScale = false })
}
