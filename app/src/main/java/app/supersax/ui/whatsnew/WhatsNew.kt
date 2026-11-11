package app.supersax.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.common.WhatsNewEntry
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.launch

/** After an update: the notes of every entry not seen yet, shown once. */
@Composable
fun WhatsNewAfterUpdate() {
    val c = LocalContainer.current
    val settings = LocalSettings.current
    if (!settings.onboardingDone) return
    val unseen = c.content.whatsNew.filter { it.id > settings.lastWhatsNew }
    if (unseen.isEmpty()) return
    val scope = rememberCoroutineScope()
    val latest = unseen.last().id
    WhatsNewDialog(unseen) { scope.launch { c.settings.update { it.copy(lastWhatsNew = latest) } } }
}

@Composable
fun WhatsNewDialog(entries: List<WhatsNewEntry>, onDismiss: () -> Unit) {
    val language = LocaleHelper.effective(LocalContext.current)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = Sax.colors.accentInk, modifier = Modifier.size(36.dp)) },
        title = { Text(stringResource(R.string.whats_new_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.reversed().flatMap { it.lines(language) }.forEach { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Sax.colors.good, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                Text(stringResource(R.string.whats_new_ok))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
