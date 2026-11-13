package app.supersax.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.common.AudioRefRecord
import app.supersax.playback.Playlist
import app.supersax.ui.components.EmptyState
import app.supersax.ui.components.IconTile
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What plays in the background (songs, playlists or the practised song): title, position, previous / play-pause / next. */
@Composable
fun NowPlayingBar(onOpen: (String) -> Unit) {
    val c = LocalContainer.current
    val now by c.playback.now.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { while (true) { c.playback.tick(); delay(1000) } }
    val n = now ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Sax.colors.onAccent).clickable { onOpen(n.songId) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, color = androidx.compose.ui.graphics.Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (n.practice) R.string.np_practice else R.string.np_listening), style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
            }
            if (!n.practice) IconButton(onClick = { c.playback.previous() }, enabled = n.hasPrevious) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.np_previous), tint = androidx.compose.ui.graphics.Color.White)
            }
            IconButton(onClick = { c.playback.toggle() }) {
                Icon(if (n.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (n.playing) R.string.pause else R.string.play),
                    tint = Sax.colors.accent, modifier = Modifier.size(32.dp))
            }
            if (!n.practice) IconButton(onClick = { c.playback.next() }, enabled = n.hasNext) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.np_next), tint = androidx.compose.ui.graphics.Color.White)
            }
            IconButton(onClick = { c.playback.stop() }) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)) }
        }
        if (n.durationMs > 0) LinearProgressIndicator(progress = { (n.positionMs.toFloat() / n.durationMs).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp), color = Sax.colors.accent, trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f), drawStopIndicator = {})
    }
}

/** The playlists as cards, and a button to make a new one. */
@Composable
fun PlaylistsSection(lists: List<Playlist>, onOpen: (String) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    app.supersax.ui.components.Overline(stringResource(R.string.pl_title), Modifier.padding(start = 4.dp, top = 4.dp))
    lists.forEach { p ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable { onOpen(p.id) }
                .heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Sax.colors.accentInk)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.pl_songs, p.songIds.size, p.songIds.size), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
            if (p.songIds.isNotEmpty()) IconButton(onClick = {
                scope.launch {
                    val refs = c.media.audioRefs.value().associateBy { it.id }
                    c.playback.playSongs(p.songIds.mapNotNull { refs[it] })
                }
            }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.pl_play), tint = Sax.colors.accentInk) }
        }
    }
    TextButton(onClick = { creating = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pl_new)) }
    if (creating) NameDialog(stringResource(R.string.pl_new), "", onDismiss = { creating = false }) { name -> scope.launch { c.playlists.create(name) }; creating = false }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.value(): T = first()

/** Ask for a name (new or renamed playlist). */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.pl_name)) }) },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Tick the playlists a song belongs to; make a new one on the way. */
@Composable
fun AddToPlaylistDialog(songId: String, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val lists by c.playlists.all.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pl_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lists.forEach { p ->
                    val member = songId in p.songIds
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { c.playlists.setMember(p.id, songId, !member) } }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = member, onCheckedChange = { scope.launch { c.playlists.setMember(p.id, songId, it) } })
                        Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.pl_new)) })
                    IconButton(onClick = { val n = newName; newName = ""; scope.launch { val id = c.playlists.create(n); c.playlists.setMember(id, songId, true) } },
                        enabled = newName.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.pl_new), tint = Sax.colors.accentInk) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/** One playlist: play it (in order or shuffled) in the background, open or remove its songs, rename or delete it. */
@Composable
fun PlaylistScreen(id: String, onBack: () -> Unit, onSong: (String) -> Unit) {
    val c = LocalContainer.current
    val lists by c.playlists.all.collectAsStateWithLifecycle(null)
    val refs by c.media.audioRefs.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val p = lists?.firstOrNull { it.id == id }
    ScreenScaffold(p?.name ?: "", onBack = onBack, actions = {
        IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.pl_rename)) }
        IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.pl_delete)) }
    }) { padding ->
        ScrollColumn(padding) {
            if (p == null) return@ScrollColumn
            NowPlayingBar(onSong)
            val byId = refs.associateBy { it.id }
            val songs: List<AudioRefRecord> = p.songIds.mapNotNull { byId[it] }
            if (songs.isEmpty()) { EmptyState(stringResource(R.string.pl_empty)); return@ScrollColumn }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { c.playback.playSongs(songs) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pl_play))
                }
                OutlinedButton(onClick = { c.playback.playSongs(songs, shuffle = true) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pl_shuffle))
                }
            }
            songs.forEachIndexed { i, s ->
                SectionCard(modifier = Modifier.clickable { onSong(s.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.AutoMirrored.Rounded.QueueMusic)
                        Spacer(Modifier.width(12.dp))
                        Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { c.playback.playSongs(songs, start = i) }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Sax.colors.accentInk) }
                        IconButton(onClick = { scope.launch { c.playlists.setMember(id, s.id, false) } }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.pl_remove_song), tint = Sax.colors.textTertiary)
                        }
                    }
                }
            }
        }
    }
    if (renaming && p != null) NameDialog(stringResource(R.string.pl_rename), p.name, onDismiss = { renaming = false }) { n -> scope.launch { c.playlists.rename(id, n) }; renaming = false }
    if (deleting && p != null) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.pl_delete_q, p.name)) },
        text = { Text(stringResource(R.string.pl_delete_body)) },
        confirmButton = { TextButton(onClick = { deleting = false; scope.launch { c.playlists.delete(id) }; onBack() }) { Text(stringResource(R.string.pl_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
