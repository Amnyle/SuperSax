package app.saxpractice.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.saxpractice.R
import app.saxpractice.domain.generator.Generator
import app.saxpractice.domain.planner.StepKind
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.ui.components.BigButton
import app.saxpractice.ui.components.EmptyState
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.components.blockKindLabel
import app.saxpractice.ui.components.dayLabel
import app.saxpractice.ui.components.exerciseTypeLabel
import app.saxpractice.ui.components.formatClock
import app.saxpractice.ui.components.scaleLabel
import app.saxpractice.ui.nav.appViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    onStartSession: () -> Unit,
    onSettings: () -> Unit,
    onPrograms: () -> Unit,
    onPhoneDrill: (ExerciseType) -> Unit,
) {
    val vm = appViewModel { TodayViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    ScreenScaffold(
        title = stringResource(R.string.today),
        actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) } },
    ) { padding ->
        ScrollColumn(padding) {
            val plan = ui.plan
            when {
                ui.loading -> Unit
                ui.running -> BigButton(stringResource(R.string.resume_session), { vm.start(onStartSession) })
                plan?.program == null -> EmptyState(stringResource(R.string.today_no_program), stringResource(R.string.programs), onPrograms)
                plan.session == null || plan.session.steps.isEmpty() ->
                    EmptyState(stringResource(R.string.today_program_empty), stringResource(R.string.edit_program), onPrograms)
                else -> {
                    val session = plan.session
                    val day = plan.day!!
                    if (plan.offDay) {
                        Text(stringResource(R.string.today_off_day, dayLabel(day.dayOfWeek)), style = MaterialTheme.typography.bodyLarge)
                    }
                    BigButton(stringResource(R.string.start_session_minutes, session.playingSec / 60), { vm.start(onStartSession) })
                    Text(stringResource(R.string.today_total_with_rests, formatClock(session.totalSec.toLong())), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.short_on_time), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null, 5, 10, 15, 20).filter { it == null || it < day.minutes }.forEach { m ->
                            FilterChip(
                                selected = ui.availableMinutes == m,
                                onClick = { vm.setAvailable(m) },
                                label = { Text(if (m == null) stringResource(R.string.full_session) else stringResource(R.string.minutes_short, m)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    SectionCard(stringResource(R.string.today_plan)) {
                        session.steps.filter { it.kind == StepKind.PLAY && it.chunk == 1 }.forEach { st ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val detail = when {
                                    st.target.scaleKey != null -> " · ${scaleLabel(st.target.scaleKey)} · ${stringResource(R.string.bpm_value, st.target.bpm ?: 0)}"
                                    st.target.holdSec != null -> " · ${stringResource(R.string.hold_target, st.target.holdSec)}"
                                    else -> ""
                                }
                                Text("${blockKindLabel(st.blockKind)} – ${exerciseTypeLabel(st.exerciseType)}$detail", Modifier.weight(1f))
                                Text(formatClock((st.durationSec * st.chunks).toLong()))
                            }
                        }
                    }
                    plan.program.notes.firstOrNull { it.startsWith(Generator.NOTE_CONSISTENCY_PREFIX) }?.let {
                        Text(stringResource(R.string.consistency_suggestion, it.removePrefix(Generator.NOTE_CONSISTENCY_PREFIX).toInt()),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (plan?.phoneDrills?.isNotEmpty() == true) SectionCard(stringResource(R.string.phone_sessions)) {
                plan.phoneDrills.forEach { d ->
                    OutlinedButton(onClick = { onPhoneDrill(d.type) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("${exerciseTypeLabel(d.type)} · ${stringResource(R.string.minutes_short, d.minutes)}")
                    }
                }
            }
        }
    }
}
