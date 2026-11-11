package app.supersax.ui.settings

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.supersax.R
import app.supersax.domain.backup.SettingsRecord
import app.supersax.ui.theme.Sax
import app.supersax.work.AutoBackup
import kotlinx.coroutines.launch

/** Automatic backups to Google Drive or a folder, every day or week (like SuperHealth). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoBackupSection(s: SettingsRecord, update: ((SettingsRecord) -> SettingsRecord) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val chooseDrive = stringResource(R.string.auto_backup_choose_drive)
    val sent = stringResource(R.string.auto_backup_done)

    fun backupNow() = scope.launch {
        busy = true
        runCatching { AutoBackup.now(context) }
            .onSuccess { Toast.makeText(context, sent, Toast.LENGTH_SHORT).show() }
        busy = false
    }

    val pickDrive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Only a file in the Google Drive app: Drive uploads it each time the app replaces it.
        val provider = uri.authority?.let { context.packageManager.resolveContentProvider(it, 0) }
        if (provider?.packageName != AutoBackup.DRIVE_PACKAGE) {
            Toast.makeText(context, chooseDrive, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        update { it.copy(autoBackupDest = "DRIVE", autoBackupDocument = uri.toString(), lastAutoBackupMs = 0, lastAutoBackupError = null) }
        backupNow()
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        update { it.copy(autoBackupDest = "FOLDER", autoBackupFolder = uri.toString(), lastAutoBackupMs = 0, lastAutoBackupError = null) }
        backupNow()
    }

    Text(stringResource(R.string.auto_backup), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = s.autoBackupDest == "NONE", onClick = { update { it.copy(autoBackupDest = "NONE") } },
            label = { Text(stringResource(R.string.auto_backup_off)) }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = s.autoBackupDest == "DRIVE", onClick = {
            if (s.autoBackupDocument == null) pickDrive.launch("supersax-backup.zip") else update { it.copy(autoBackupDest = "DRIVE") }
        }, label = { Text(stringResource(R.string.auto_backup_drive)) }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = s.autoBackupDest == "FOLDER", onClick = {
            if (s.autoBackupFolder == null) pickFolder.launch(null) else update { it.copy(autoBackupDest = "FOLDER") }
        }, label = { Text(stringResource(R.string.auto_backup_folder)) }, modifier = Modifier.heightIn(min = 48.dp))
    }
    when (s.autoBackupDest) {
        "DRIVE" -> {
            Text(stringResource(R.string.auto_backup_drive_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            OutlinedButton(onClick = { pickDrive.launch("supersax-backup.zip") }) { Text(stringResource(R.string.auto_backup_change_file)) }
        }
        "FOLDER" -> {
            Text(stringResource(R.string.auto_backup_folder_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(stringResource(R.string.auto_backup_change_folder)) }
        }
        else -> Text(stringResource(R.string.auto_backup_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
    }
    if (s.autoBackupDest == "NONE") return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to R.string.auto_backup_daily, 7 to R.string.auto_backup_weekly).forEach { (d, label) ->
            FilterChip(selected = s.autoBackupEveryDays == d, onClick = { update { it.copy(autoBackupEveryDays = d) } },
                label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    ToggleRow(stringResource(R.string.auto_backup_recordings), stringResource(R.string.auto_backup_recordings_hint), s.autoBackupRecordings) { on ->
        update { it.copy(autoBackupRecordings = on) }
    }
    Text(
        when {
            busy -> stringResource(R.string.auto_backup_running)
            s.lastAutoBackupError != null -> stringResource(R.string.auto_backup_failed, s.lastAutoBackupError)
            s.lastAutoBackupMs > 0 -> stringResource(R.string.auto_backup_last,
                DateUtils.getRelativeDateTimeString(context, s.lastAutoBackupMs, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString())
            else -> stringResource(R.string.auto_backup_never)
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (s.lastAutoBackupError != null && !busy) MaterialTheme.colorScheme.error else Sax.colors.textSecondary,
    )
    Button(onClick = { backupNow() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
        Text(stringResource(R.string.auto_backup_now))
    }
}
