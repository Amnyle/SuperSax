package app.supersax.ui.program

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.program.ProgramOrigin
import app.supersax.ui.components.EmptyState
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.dayLabel
import app.supersax.ui.nav.appViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgramsScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val vm = appViewModel { ProgramsViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    var exportId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportId
        if (uri != null && id != null) vm.export(resolver, uri, id)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(resolver, uri) }
    val manualName = stringResource(R.string.program_manual_name)
    val generatedName = stringResource(R.string.program_default_name)

    ScreenScaffold(stringResource(R.string.programs), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary); TextButton(onClick = vm::clearMessage) { Text(stringResource(R.string.ok)) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.newManual(manualName, onEdit) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.new_manual_program)) }
                OutlinedButton(onClick = { vm.regenerate(generatedName, onEdit) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.generate_program)) }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.import_program))
                }
            }
            if (ui.programs.isEmpty()) EmptyState(stringResource(R.string.no_programs))
            ui.programs.forEach { p ->
                val active = p.id == ui.activeId
                SectionCard(p.name + if (active) " · " + stringResource(R.string.active) else "") {
                    Text(stringResource(when (p.origin) { ProgramOrigin.GENERATED -> R.string.origin_generated; ProgramOrigin.MANUAL -> R.string.origin_manual; ProgramOrigin.BUNDLED -> R.string.origin_bundled }),
                        style = MaterialTheme.typography.bodySmall)
                    val dayNames = (1..7).associateWith { dayLabel(it, true) }
                    val noDays = stringResource(R.string.no_days)
                    Text(p.days.joinToString(" · ") { "${dayNames[it.dayOfWeek]} ${it.minutes}′" }.ifEmpty { noDays })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(p.id) }) { Text(stringResource(R.string.edit)) }
                        if (!active) TextButton(onClick = { vm.activate(p.id) }) { Text(stringResource(R.string.make_active)) }
                        TextButton(onClick = { exportId = p.id; exporter.launch("${p.name}.json") }) { Text(stringResource(R.string.export)) }
                        if (!active) TextButton(onClick = { deleteId = p.id }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.delete_program_q)) },
            confirmButton = { TextButton(onClick = { vm.delete(id); deleteId = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
