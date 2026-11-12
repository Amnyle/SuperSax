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

@Composable
fun stemName(s: Lalal.Stem): String = stringResource(when (s) {
    Lalal.Stem.WIND -> R.string.stem_wind
    Lalal.Stem.VOCALS -> R.string.stem_vocals
    Lalal.Stem.PIANO -> R.string.stem_piano
    Lalal.Stem.GUITAR -> R.string.stem_guitar
    Lalal.Stem.DRUMS -> R.string.stem_drums
    Lalal.Stem.BASS -> R.string.stem_bass
})

/**
 * Remove an instrument from this song with the AI separation service, to play that part yourself: makes a new
 * song in the list ("Title (no sax)") with the same key, tempo and bars.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeparationCard(ref: AudioRefRecord, onOpen: (String) -> Unit) {
    val c = LocalContainer.current
    val jobs by c.separations.jobs.collectAsStateWithLifecycle()
    val job = jobs[ref.id]
    var hasKey by remember { mutableStateOf(SeparationKey.get(c) != null) }
    var stem by rememberSaveable { mutableStateOf(Lalal.Stem.WIND) }
    var keyText by rememberSaveable { mutableStateOf("") }
    val uri = LocalUriHandler.current
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
        val running = job != null && job.step != SeparationJob.Step.DONE && job.step != SeparationJob.Step.FAILED
        if (!running) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Lalal.Stem.entries.forEach { s -> FilterChip(selected = stem == s, onClick = { stem = s }, label = { Text(stemName(s)) }, modifier = Modifier.heightIn(min = 44.dp)) }
        }
        val suffix = stringResource(R.string.sep_suffix, stemName(stem).lowercase())
        when {
            running -> {
                Text(stringResource(when (job!!.step) {
                    SeparationJob.Step.UPLOADING -> R.string.sep_uploading
                    SeparationJob.Step.SEPARATING -> R.string.sep_separating
                    else -> R.string.sep_downloading
                }, stemName(job.stem)), style = MaterialTheme.typography.titleSmall)
                if (job.progress > 0) LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Sax.colors.accent)
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Sax.colors.accent)
                Text(stringResource(R.string.sep_wait), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
            else -> {
                if (job?.step == SeparationJob.Step.FAILED) Text(stringResource(R.string.sep_failed, job.error ?: ""), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (job?.step == SeparationJob.Step.DONE && job.resultSongId != null) {
                    Text(stringResource(R.string.sep_done, stemName(job.stem)), style = MaterialTheme.typography.titleSmall, color = Sax.colors.good)
                    Button(onClick = { onOpen(job.resultSongId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) { Text(stringResource(R.string.sep_open)) }
                }
                Button(onClick = { c.separations.start(ref, stem, suffix) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                    Text(stringResource(R.string.sep_start, stemName(stem).lowercase()))
                }
                Row { TextButton(onClick = { SeparationKey.set(c, null); hasKey = false }) { Text(stringResource(R.string.sep_forget_key)) } }
            }
        }
    }
}
