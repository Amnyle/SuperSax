package app.supersax.ui.songs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.supersax.R
import app.supersax.domain.common.AudioRefRecord
import app.supersax.playback.PhoneMusic
import app.supersax.playback.PhoneTrack
import app.supersax.ui.components.IconTile
import app.supersax.ui.components.Overline
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.formatClock
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Does [title] contain every word of [query] (case and accents ignored)? */
fun matches(title: String, query: String): Boolean {
    fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    val t = norm(title)
    return query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.all { norm(it) in t }
}

/**
 * Search results: your songs whose title matches, then every audio file on the phone that matches (all folders
 * and subfolders), to play straight away or add to your songs.
 */
@Composable
fun MusicSearchResults(query: String, songs: List<SongRow>, onSong: (String) -> Unit, onAdd: (PhoneTrack, (String) -> Unit) -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val mine = songs.filter { matches(it.ref.title, query) }
    Overline(stringResource(R.string.search_your_songs), Modifier.padding(start = 4.dp))
    if (mine.isEmpty()) Text(stringResource(R.string.search_none), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary, modifier = Modifier.padding(start = 4.dp))
    mine.forEach { row ->
        SectionCard(modifier = Modifier.clickable { onSong(row.ref.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.MusicNote)
                Spacer(Modifier.width(12.dp))
                Text(row.ref.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { c.playback.playSongs(listOf(row.ref)) }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Sax.colors.accentInk) }
            }
        }
    }
    Overline(stringResource(R.string.search_phone), Modifier.padding(start = 4.dp, top = 8.dp))
    var allowed by remember { mutableStateOf(PhoneMusic.allowed(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    if (!allowed) {
        SectionCard {
            Text(stringResource(R.string.search_phone_why), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { ask.launch(PhoneMusic.permission) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) { Text(stringResource(R.string.search_phone_allow)) }
        }
        return
    }
    var found by remember { mutableStateOf<List<PhoneTrack>?>(null) }
    LaunchedEffect(query, allowed) {
        found = null
        delay(250) // wait for typing to pause
        found = withContext(Dispatchers.IO) { runCatching { PhoneMusic.search(context, query) }.getOrDefault(emptyList()) }
    }
    val list = found
    if (list == null) { Row(Modifier.fillMaxWidth().padding(8.dp)) { CircularProgressIndicator(Modifier.padding(start = 8.dp), color = Sax.colors.accentInk) }; return }
    if (list.isEmpty()) Text(stringResource(R.string.search_none_phone), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary, modifier = Modifier.padding(start = 4.dp))
    val known = songs.associateBy { it.ref.uri }
    list.forEach { t ->
        val already = known[t.uri]
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(t.artist, t.folder, if (t.durationMs > 0) formatClock(t.durationMs / 1000) else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { c.playback.playSongs(listOf(AudioRefRecord("phone:${t.id}", t.uri, t.title, 0))) }) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Sax.colors.accentInk)
                }
                if (already != null) IconButton(onClick = { onSong(already.ref.id) }) {
                    Icon(Icons.Rounded.CheckCircle, stringResource(R.string.search_in_songs), tint = Sax.colors.good)
                } else IconButton(onClick = { onAdd(t, onSong) }) { Icon(Icons.Rounded.Add, stringResource(R.string.search_add), tint = Sax.colors.accentInk) }
            }
        }
    }
}
