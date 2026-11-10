package app.saxpractice.ui.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.saxpractice.R
import app.saxpractice.audio.MicState
import app.saxpractice.ui.components.ErrorBanner

fun hasMicPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Asks for the microphone at first use, after a clear explanation. Returns a function that starts the request. */
@Composable
fun rememberMicRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
    if (explain) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text(stringResource(R.string.mic_why_title)) },
        text = { Text(stringResource(R.string.mic_why_body)) },
        confirmButton = { Button(onClick = { explain = false; launcher.launch(Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.continue_)) } },
        dismissButton = { TextButton(onClick = { explain = false; onResult(false) }) { Text(stringResource(R.string.not_now)) } },
    )
    return { if (hasMicPermission(context)) onResult(true) else explain = true }
}

/** Shows a visible, recoverable message for microphone failures. */
@Composable
fun MicErrorBanner(state: MicState, onRetry: () -> Unit) {
    val context = LocalContext.current
    if (state !is MicState.Failed) return
    when (state.reason) {
        MicState.Reason.NO_PERMISSION -> ErrorBanner(stringResource(R.string.mic_denied), stringResource(R.string.open_settings)) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
        MicState.Reason.BUSY -> ErrorBanner(stringResource(R.string.mic_busy), stringResource(R.string.retry), onRetry)
        MicState.Reason.ERROR -> ErrorBanner(stringResource(R.string.mic_error), stringResource(R.string.retry), onRetry)
    }
}
