package app.supersax.ui.whatsnew

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.supersax.CrashLog
import app.supersax.R

/** After a crash: shows what went wrong, with a button to copy it (to send to the developer). */
@Composable
fun CrashReportDialog() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.pending(ctx)) }
    val r = report ?: return
    fun close() { CrashLog.clear(ctx); report = null }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.crash_body), style = MaterialTheme.typography.bodyMedium)
                Text(r, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Super Sax crash", r))
                close()
            }) { Text(stringResource(R.string.crash_copy)) }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text(stringResource(R.string.close)) } },
    )
}
