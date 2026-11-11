package app.supersax.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.program.Block
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Exercise
import app.supersax.domain.program.ExerciseType
import app.supersax.domain.program.ProgramDay
import app.supersax.ui.components.ScalePickerDialog
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.Stepper
import app.supersax.ui.components.blockKindLabel
import app.supersax.ui.components.dayLabel
import app.supersax.ui.components.exerciseTypeLabel
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.nav.appViewModel
import androidx.compose.ui.platform.LocalContext

/** The one editor for every program: days > blocks > exercises > targets, plus phone sessions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramEditorScreen(programId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "edit-$programId") { ProgramEditorViewModel(it, programId) }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val p = draft
    ScreenScaffold(
        title = stringResource(R.string.edit_program),
        onBack = onBack,
        actions = { TextButton(onClick = { vm.save(onBack) }, enabled = dirty) { Text(stringResource(R.string.save)) } },
    ) { padding ->
        if (p == null) return@ScreenScaffold
        ScrollColumn(padding) {
            OutlinedTextField(p.name, vm::rename, label = { Text(stringResource(R.string.program_name)) }, singleLine = true)
            Stepper(stringResource(R.string.endurance_cap), p.enduranceCapSec / 60, { vm.setEndurance(it * 60) }, 1..30) {
                stringResource(R.string.minutes_short, it)
            }
            Stepper(stringResource(R.string.rest_length), p.restSec, vm::setRest, 15..300, 15) { stringResource(R.string.seconds_short, it) }

            p.days.forEach { day -> DayCard(day, vm) }

            Text(stringResource(R.string.add_day), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).filter { p.dayFor(it) == null }.forEach { d ->
                    AssistChip(onClick = { vm.addDay(d) }, label = { Text(dayLabel(d, short = true)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            PhoneDrillsCard(p.phoneDrills, p.weeklySaxMinutes, vm)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(day: ProgramDay, vm: ProgramEditorViewModel) {
    var copyOpen by remember { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.day_minutes, dayLabel(day.dayOfWeek), day.minutes), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { copyOpen = true }) { Text(stringResource(R.string.copy_day)) }
            IconButton(onClick = { vm.removeDay(day.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.remove_day)) }
        }
        day.blocks.forEach { b -> BlockEditor(day.id, b, vm) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlockKind.entries.filter { k -> day.blocks.none { it.kind == k } }.forEach { k ->
                AssistChip(onClick = { vm.addBlock(day.id, k) }, label = { Text("+ " + blockKindLabel(k)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    if (copyOpen) AlertDialog(
        onDismissRequest = { copyOpen = false },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { copyOpen = false }) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.copy_day_to)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).filter { it != day.dayOfWeek }.forEach { d ->
                    AssistChip(onClick = { vm.copyDay(day.id, d); copyOpen = false }, label = { Text(dayLabel(d)) })
                }
            }
        },
    )
}

@Composable
private fun BlockEditor(dayId: String, b: Block, vm: ProgramEditorViewModel) {
    var addOpen by remember { mutableStateOf(false) }
    val language = LocaleHelper.effective(LocalContext.current)
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(blockKindLabel(b.kind), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { vm.removeBlock(dayId, b.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.remove_block)) }
    }
    Stepper(stringResource(R.string.minutes), b.minutes, { vm.setBlockMinutes(dayId, b.id, it) }, 1..60)
    b.exercises.forEach { e -> ExerciseEditor(dayId, b.id, e, vm, language) }
    TextButton(onClick = { addOpen = true }) { Text(stringResource(R.string.add_exercise)) }
    if (addOpen) AlertDialog(
        onDismissRequest = { addOpen = false },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { addOpen = false }) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.add_exercise)) },
        text = {
            Column {
                vm.catalogFor(b.kind).forEach { info ->
                    TextButton(onClick = { vm.addExercise(dayId, b.id, info.id); addOpen = false }) { Text(info.title.get(language)) }
                }
            }
        },
    )
}

@Composable
private fun ExerciseEditor(dayId: String, blockId: String, e: Exercise, vm: ProgramEditorViewModel, language: String) {
    var pickScale by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(exerciseTypeLabel(e.type), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { vm.removeExercise(dayId, blockId, e.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.remove_exercise)) }
        }
        val t = e.target
        when (e.type) {
            ExerciseType.SCALE -> {
                TextButton(onClick = { pickScale = true }) {
                    Text(if (t.scaleKey == null) stringResource(R.string.scale_auto) else scaleLabel(t.scaleKey))
                }
                if (t.scaleKey != null) TextButton(onClick = { vm.setTarget(dayId, blockId, e.id) { it.copy(scaleKey = null) } }) {
                    Text(stringResource(R.string.scale_use_auto))
                }
                PinRow(t.pinned) { pin -> vm.setTarget(dayId, blockId, e.id) { it.copy(pinned = pin, bpm = it.bpm ?: 60) } }
                if (t.pinned) Stepper(stringResource(R.string.bpm), t.bpm ?: 60, { v -> vm.setTarget(dayId, blockId, e.id) { it.copy(bpm = v) } }, 30..240, 5)
            }
            ExerciseType.LONG_TONE, ExerciseType.RANGE_EXTENSION -> {
                PinRow(t.pinned) { pin -> vm.setTarget(dayId, blockId, e.id) { it.copy(pinned = pin, holdSec = it.holdSec ?: 8) } }
                if (t.pinned) Stepper(stringResource(R.string.hold_seconds), t.holdSec ?: 8, { v -> vm.setTarget(dayId, blockId, e.id) { it.copy(holdSec = v) } }, 2..60)
            }
            ExerciseType.NEW_MATERIAL, ExerciseType.PLAY_FOR_FUN -> PiecePickerSlot(t.pieceId) { id ->
                vm.setTarget(dayId, blockId, e.id) { it.copy(pieceId = id) }
            }
            else -> Unit
        }
    }
    if (pickScale) ScalePickerDialog(
        onPick = { key -> vm.setTarget(dayId, blockId, e.id) { it.copy(scaleKey = key) }; pickScale = false },
        onDismiss = { pickScale = false },
    )
}

@Composable
private fun PinRow(pinned: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pin_target))
            Text(stringResource(R.string.pin_target_hint), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = pinned, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneDrillsCard(slots: List<app.supersax.domain.program.PhoneDrillSlot>, weeklySax: Int, vm: ProgramEditorViewModel) {
    var day by remember { mutableStateOf(1) }
    SectionCard(stringResource(R.string.phone_sessions)) {
        val total = slots.sumOf { it.minutes }
        val cap = (weeklySax * vm.maxPhoneShare).toInt()
        Text(stringResource(R.string.phone_cap_status, total, cap), style = MaterialTheme.typography.bodyMedium,
            color = if (total > cap) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        slots.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${dayLabel(s.dayOfWeek)} · ${exerciseTypeLabel(s.type)} · ${stringResource(R.string.minutes_short, s.minutes)}", Modifier.weight(1f))
                IconButton(onClick = { vm.removePhoneDrill(i) }) { Icon(Icons.Default.Delete, stringResource(R.string.remove)) }
            }
        }
        if (vm.availablePhoneDrills.isEmpty()) {
            Text(stringResource(R.string.phone_none_available), style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { d ->
                    androidx.compose.material3.FilterChip(selected = day == d, onClick = { day = d }, label = { Text(dayLabel(d, short = true)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.availablePhoneDrills.forEach { t ->
                    AssistChip(onClick = { vm.addPhoneDrill(day, t, 5) }, label = { Text("+ " + exerciseTypeLabel(t)) }, enabled = total + 5 <= cap,
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
}
