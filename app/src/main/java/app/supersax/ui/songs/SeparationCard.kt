package app.supersax.ui.songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.common.AudioRefRecord
import app.supersax.separation.Lalal
import app.supersax.separation.SeparationJob
import app.supersax.separation.SeparationKey
import app.supersax.ui.components.SectionCard
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.launch

@Composable
fun stemName(s: Lalal.Stem): String = stringResource(when (s) {
    Lalal.Stem.WIND -> R.string.stem_wind
    Lalal.Stem.LEAD -> R.string.stem_lead
    Lalal.Stem.PIANO -> R.string.stem_piano
    Lalal.Stem.GUITAR -> R.string.stem_guitar
    Lalal.Stem.DRUMS -> R.string.stem_drums
    Lalal.Stem.BASS -> R.string.stem_bass
})

/** Short name of what is removed, for titles and buttons ("sax", "lead"). */
@Composable
fun stemShort(s: Lalal.Stem): String = stringResource(when (s) {
    Lalal.Stem.WIND -> R.string.stem_short_wind
    Lalal.Stem.LEAD -> R.string.stem_short_lead
    Lalal.Stem.PIANO -> R.string.stem_short_piano
    Lalal.Stem.GUITAR -> R.string.stem_short_guitar
    Lalal.Stem.DRUMS -> R.string.stem_short_drums
    Lalal.Stem.BASS -> R.string.stem_short_bass
})

/**
 * Remove an instrument from this song with the AI separation service: adds a job to the queue, which runs in the
 * background (the app can be closed). The new version appears under the song, with the same key, tempo and bars.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeparationCard(ref: AudioRefRecord, onOpen: (String) -> Unit, onQueue: () -> Unit) {
    val c = LocalContainer.current
    val jobs by c.separationQueue.jobs.collectAsStateWithLifecycle(emptyList())
    val mine = jobs.filter { it.songId == ref.id }
    var hasKey by remember { mutableStateOf(SeparationKey.get(c) != null) }
    var stem by rememberSaveable { mutableStateOf(Lalal.Stem.WIND) }
    var keyText by rememberSaveable { mutableStateOf("") }
    val uri = LocalUriHandler.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val askNotify = app.supersax.ui.audio.rememberNotificationRequest()
    SectionCard(stringResource(R.string.sep_title)) {
        Text(stringResource(R.string.sep_body), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
        if (!hasKey) {
            Text(stringResource(R.string.sep_key_needed), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { uri.openUri("https://www.lalal.ai/api/") }) { Text(stringResource(R.string.sep_get_key)) }
            OutlinedTextField(keyText, { keyText = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.sep_key)) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(onClick = { SeparationKey.set(c, keyText); hasKey = SeparationKey.get(c) != null; keyText = "" }, enabled = keyText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.sep_save_key)) }
            return@SectionCard
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Lalal.Stem.entries.forEach { s -> FilterChip(selected = stem == s, onClick = { stem = s }, label = { Text(stemName(s)) }, modifier = Modifier.heightIn(min = 44.dp)) }
        }
        val suffix = stringResource(R.string.sep_suffix, stemShort(stem))
        Button(onClick = { askNotify(); scope.launch { c.separationQueue.add(ref, stem, suffix) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
            Text(stringResource(R.string.queue_add, stemShort(stem)))
        }
        Text(stringResource(R.string.queue_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        mine.sortedByDescending { it.addedAtMs }.take(4).forEach { j -> QueueRow(j, onOpen) }
        Row {
            TextButton(onClick = onQueue) { Text(stringResource(R.string.queue_open)) }
            TextButton(onClick = { SeparationKey.set(c, null); hasKey = false }) { Text(stringResource(R.string.sep_forget_key)) }
        }
    }
}

/** One job: what it removes, where it is, and its result. */
@Composable
fun QueueRow(j: SeparationJob, onOpen: (String) -> Unit, showTitle: Boolean = false, onRetry: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showTitle) Text(j.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        val what = stemShort(j.stemValue)
        Text(stringResource(when (j.step) {
            SeparationJob.Step.QUEUED -> R.string.queue_waiting
            SeparationJob.Step.UPLOADING -> R.string.queue_uploading
            SeparationJob.Step.SEPARATING -> R.string.queue_separating
            SeparationJob.Step.DOWNLOADING -> R.string.queue_downloading
            SeparationJob.Step.DONE -> R.string.queue_done
            SeparationJob.Step.FAILED -> R.string.queue_failed
        }, what), style = MaterialTheme.typography.bodyMedium,
            color = when (j.step) { SeparationJob.Step.DONE -> Sax.colors.good; SeparationJob.Step.FAILED -> MaterialTheme.colorScheme.error; else -> Sax.colors.text })
        if (j.step == SeparationJob.Step.FAILED) Text(if (j.error == app.supersax.separation.OFFLINE) stringResource(R.string.sep_offline) else j.error ?: "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (j.active && j.step != SeparationJob.Step.QUEUED) {
            if (j.progress > 0) LinearProgressIndicator(progress = { j.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Sax.colors.accent)
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Sax.colors.accent)
        }
        Row {
            if (j.step == SeparationJob.Step.DONE && j.resultSongId != null) TextButton(onClick = { onOpen(j.resultSongId) }) { Text(stringResource(R.string.sep_open)) }
            if (j.step == SeparationJob.Step.FAILED && onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            if (!j.active || j.step == SeparationJob.Step.QUEUED) onRemove?.let { TextButton(onClick = it) { Text(stringResource(R.string.remove)) } }
        }
    }
}

/** All separations: waiting, running, finished; they run in the background in this order. */
@Composable
fun SeparationQueueScreen(onBack: () -> Unit, onSong: (String) -> Unit) {
    val c = LocalContainer.current
    val jobs by c.separationQueue.jobs.collectAsStateWithLifecycle(emptyList())
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) { if (jobs.any { it.active } || c.separationQueue.all().any { it.active }) app.supersax.separation.SeparationQueue.kick(c.context) }
    app.supersax.ui.components.ScreenScaffold(stringResource(R.string.queue_title), onBack = onBack) { padding ->
        app.supersax.ui.components.ScrollColumn(padding) {
            Text(stringResource(R.string.queue_screen_hint), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
            if (jobs.isEmpty()) app.supersax.ui.components.EmptyState(stringResource(R.string.queue_empty))
            val (active, finished) = jobs.partition { it.active }
            active.sortedBy { it.addedAtMs }.forEach { j ->
                SectionCard { QueueRow(j, onSong, showTitle = true, onRemove = { scope.launch { c.separationQueue.remove(j.id) } }) }
            }
            if (finished.isNotEmpty()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    app.supersax.ui.components.Overline(stringResource(R.string.queue_finished), Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { c.separationQueue.clearFinished() } }) { Text(stringResource(R.string.queue_clear)) }
                }
                finished.sortedByDescending { it.addedAtMs }.forEach { j ->
                    SectionCard { QueueRow(j, onSong, showTitle = true, onRetry = { scope.launch { c.separationQueue.retry(j.id) } },
                        onRemove = { scope.launch { c.separationQueue.remove(j.id) } }) }
                }
            }
        }
    }
}
