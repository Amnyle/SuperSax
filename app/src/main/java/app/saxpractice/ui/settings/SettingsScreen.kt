package app.saxpractice.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.saxpractice.R
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.components.Stepper
import app.saxpractice.ui.nav.appViewModel
import app.saxpractice.ui.onboarding.LanguageNamingTheme
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit, onRerunOnboarding: () -> Unit, onPrograms: () -> Unit, onImported: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val backup by vm.backup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var includeRecordings by remember { mutableStateOf(true) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(context.contentResolver, uri, includeRecordings)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.stageImport(context.contentResolver, uri)
    }

    ScreenScaffold(stringResource(R.string.settings), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            SectionCard(stringResource(R.string.settings_display)) {
                LanguageNamingTheme(s.language, s.noteNaming, s.theme, vm::update)
            }
            SectionCard(stringResource(R.string.settings_instrument)) {
                Text(stringResource(R.string.instrument_alto))
                Text(stringResource(R.string.instrument_fixed_hint), style = MaterialTheme.typography.bodySmall)
                Stepper(stringResource(R.string.reference_pitch), s.a4Hz.toInt(), { v -> vm.update { it.copy(a4Hz = v.toDouble()) } }, 430..450) {
                    stringResource(R.string.hz_value, it)
                }
                Text(stringResource(R.string.synth_timbre))
                Row {
                    app.saxpractice.domain.backup.SynthTimbre.entries.forEach { t ->
                        androidx.compose.material3.FilterChip(selected = s.synthTimbre == t, onClick = { vm.update { it.copy(synthTimbre = t) } },
                            label = { Text(stringResource(if (t == app.saxpractice.domain.backup.SynthTimbre.SINE) R.string.timbre_sine else R.string.timbre_soft)) },
                            modifier = Modifier.heightIn(min = 48.dp).padding(end = 8.dp))
                    }
                }
            }
            SectionCard(stringResource(R.string.settings_sessions)) {
                Stepper(stringResource(R.string.rest_length), s.restSec, { v -> vm.update { it.copy(restSec = v) } }, 15..300, 15) {
                    stringResource(R.string.seconds_short, it)
                }
                ToggleRow(stringResource(R.string.silence_notifications), stringResource(R.string.silence_notifications_hint), s.silenceNotifications) { on ->
                    vm.update { it.copy(silenceNotifications = on) }
                    if (on && !vm.hasQuietAccess()) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                }
                if (s.silenceNotifications && !vm.hasQuietAccess()) {
                    Text(stringResource(R.string.silence_needs_access), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                RemindersSection(s, vm::update)
                TextButton(onClick = onPrograms, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.programs)) }
                TextButton(onClick = onRerunOnboarding, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.rerun_onboarding)) }
            }
            SectionCard(stringResource(R.string.settings_experimental)) {
                ToggleRow(stringResource(R.string.experimental_features), stringResource(R.string.experimental_hint), s.experimental) { on ->
                    vm.update { it.copy(experimental = on) }
                }
            }
            SectionCard(stringResource(R.string.backup)) {
                Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall)
                ToggleRow(stringResource(R.string.backup_include_recordings), null, includeRecordings) { includeRecordings = it }
                Row {
                    Button(onClick = { exporter.launch("sax-practice-${LocalDate.now()}.zip") }, enabled = !backup.busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !backup.busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_import)) }
                }
                backup.message?.let { msg ->
                    Text(stringResource(msg, backup.messageArg ?: ""), color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    backup.pending?.let { data ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body, data.sessions.size, data.programs.size, data.recordings.size)) },
            confirmButton = { Button(onClick = { vm.confirmImport(onImported) }) { Text(stringResource(R.string.backup_replace)) } },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun ToggleRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
