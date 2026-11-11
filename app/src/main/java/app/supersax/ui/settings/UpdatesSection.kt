package app.supersax.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.backup.SettingsRecord
import app.supersax.update.Updater
import kotlinx.coroutines.launch

/** In-app updates from GitHub releases, like SuperHealth: check, download, install. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdatesSection(s: SettingsRecord, update: ((SettingsRecord) -> SettingsRecord) -> Unit) {
    val context = LocalContext.current
    val state by Updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (state is Updater.State.Idle) Updater.refresh(context) }
    Text(stringResource(R.string.update_installed, Updater.installedVersion(context)))
    ToggleRow(stringResource(R.string.update_daily), stringResource(R.string.update_daily_hint), s.updateCheck) { on ->
        update { it.copy(updateCheck = on) }
        Updater.schedule(context, on)
    }
    when (val st = state) {
        Updater.State.Idle, Updater.State.Checking -> Text(stringResource(R.string.update_checking))
        is Updater.State.UpToDate -> Text(stringResource(R.string.update_up_to_date))
        is Updater.State.Available -> {
            Text(stringResource(R.string.update_available_title, st.update.version), style = MaterialTheme.typography.titleMedium)
            if (st.update.notes.isNotBlank()) Text(st.update.notes.take(600), style = MaterialTheme.typography.bodySmall)
            if (!Updater.canInstall(context)) {
                Text(stringResource(R.string.update_allow_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.update_allow)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { Updater.downloadAndInstall(context.applicationContext, st.update) } },
                    enabled = Updater.canInstall(context), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.update_install, st.update.version))
                }
                TextButton(onClick = { update { it.copy(updateSkip = st.update.version) } }) { Text(stringResource(R.string.update_skip)) }
            }
        }
        is Updater.State.Downloading -> {
            Text(stringResource(R.string.update_downloading, (st.fraction * 100).toInt()))
            LinearProgressIndicator(progress = { st.fraction }, modifier = Modifier.fillMaxWidth())
        }
        is Updater.State.Installing -> Text(stringResource(R.string.update_installing, st.version))
        is Updater.State.Failed -> Text(stringResource(failureText(st.reason)), color = MaterialTheme.colorScheme.error)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { scope.launch { Updater.refresh(context) } }) { Text(stringResource(R.string.update_check_now)) }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.RELEASES_PAGE))) }) {
            Text(stringResource(R.string.update_all_releases))
        }
    }
}

private fun failureText(r: Updater.Reason) = when (r) {
    Updater.Reason.NETWORK -> R.string.update_fail_network
    Updater.Reason.NO_RELEASE -> R.string.update_fail_no_release
    Updater.Reason.INCOMPLETE -> R.string.update_fail_incomplete
    Updater.Reason.NOT_THIS_APP -> R.string.update_fail_not_this_app
    Updater.Reason.SIGNATURE -> R.string.update_fail_signature
    Updater.Reason.NOT_NEWER -> R.string.update_fail_not_newer
    Updater.Reason.INSTALL -> R.string.update_fail_install
    Updater.Reason.CANCELLED -> R.string.update_fail_cancelled
}
