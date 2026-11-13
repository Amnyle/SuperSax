package app.supersax.ui.songs

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.common.AudioRefRecord
import app.supersax.domain.song.SongKeys
import app.supersax.domain.song.SongSetup
import app.supersax.ui.components.EmptyState
import app.supersax.ui.components.ErrorBanner
import app.supersax.ui.components.IconTile
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.formatClock
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.Sax
import app.supersax.youtube.YouTubeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A song in the list with what is known about it. */
data class SongRow(val ref: AudioRefRecord, val setup: SongSetup)

@OptIn(ExperimentalCoroutinesApi::class)
class SongsViewModel(private val c: AppContainer) : ViewModel() {
    val songs = c.media.audioRefs.flatMapLatest { refs ->
        if (refs.isEmpty()) flowOf(emptyList()) else combine(refs.map { r -> c.songs.observe(r.id) }) { setups ->
            refs.mapIndexed { i, r -> SongRow(r, setups[i]) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Versions made before songs were linked: match "Title (no …)" / "Title (sans …)" to the song "Title".
        viewModelScope.launch {
            val refs = c.media.audioRefs.first()
            for (r in refs) {
                val s = c.songs.get(r.id)
                if (s.parentId != null || !r.title.endsWith(")")) continue
                val open = r.title.lastIndexOf(" (")
                if (open <= 0) continue
                val inner = r.title.substring(open + 2, r.title.length - 1).lowercase()
                if (!inner.startsWith("no ") && !inner.startsWith("sans ")) continue
                val parent = refs.firstOrNull { it.id != r.id && it.title == r.title.substring(0, open) } ?: continue
                val stem = when {
                    "sax" in inner || "wind" in inner || "vent" in inner -> "WIND"
                    "lead" in inner || "voice" in inner || "voix" in inner || "mélodie" in inner || "vocal" in inner -> "LEAD"
                    "piano" in inner -> "PIANO"
                    "guit" in inner -> "GUITAR"
                    "drum" in inner || "batterie" in inner -> "DRUMS"
                    "bass" in inner -> "BASS"
                    else -> null
                }
                c.songs.save(r.id, s.copy(parentId = parent.id, removed = stem))
            }
        }
    }

    /** Keeps a reference to the user's file (it stays where it is). */
    fun import(uri: Uri, onAdded: (String) -> Unit) = viewModelScope.launch {
        runCatching { c.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = c.context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "audio"
        val rec = AudioRefRecord(UUID.randomUUID().toString(), uri.toString(), name.substringBeforeLast('.'), c.clock.nowMs())
        c.media.saveAudioRef(rec)
        onAdded(rec.id)
    }

    /** Adds a song found on the phone (it stays where it is) and opens it. */
    fun addPhoneTrack(t: app.supersax.playback.PhoneTrack, onAdded: (String) -> Unit) = viewModelScope.launch {
        val existing = c.media.audioRefs.first().firstOrNull { it.uri == t.uri }
        if (existing != null) { onAdded(existing.id); return@launch }
        val rec = AudioRefRecord(UUID.randomUUID().toString(), t.uri, t.title, c.clock.nowMs())
        c.media.saveAudioRef(rec)
        onAdded(rec.id)
    }

    /** Removes the song; a downloaded file is deleted too, an imported file stays where it is. */
    fun remove(r: AudioRefRecord) = viewModelScope.launch {
        c.media.deleteAudioRef(r.id)
        c.playlists.forget(r.id)
        c.songs.delete(r.id)
        withContext(Dispatchers.IO) { File(File(c.context.cacheDir, "songs"), "${r.id}.pcm").delete() }
        val path = Uri.parse(r.uri).path
        if (r.uri.startsWith("file:") && path != null && path.startsWith(c.songsDir.absolutePath)) withContext(Dispatchers.IO) { File(path).delete() }
    }
}

/** Key and tempo of an analysed song, e.g. "B♭ major · 112 BPM". */
@Composable
fun songSummary(s: SongSetup): String? {
    if (!s.analyzed) return null
    val naming = LocalSettings.current.noteNaming
    val key = if (s.keyTonic >= 0) stringResource(if (s.keyMinor) R.string.key_minor else R.string.key_major, naming.format(SongKeys.tonicName(s.keyTonic, s.keyMinor))) else null
    return listOfNotNull(key, stringResource(R.string.bpm_value, Math.round(s.bpm).toInt())).joinToString(" · ")
}

/** The song player's list: downloaded and imported songs, YouTube search, file import. */
@Composable
fun SongsScreen(onBack: () -> Unit, onSong: (String) -> Unit, onSearch: () -> Unit, onQueue: () -> Unit = {}, onPlaylist: (String) -> Unit = {}) {
    val vm = appViewModel { SongsViewModel(it) }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri, onSong) }
    ScreenScaffold(stringResource(R.string.slow_down), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.songs_hint), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSearch, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                    Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.yt_search))
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_audio))
                }
            }
            // Search your songs and all the music on the phone.
            var query by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text(stringResource(R.string.search_music)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) } },
            )
            if (query.isNotBlank()) {
                MusicSearchResults(query, songs ?: emptyList(), onSong) { t, open -> vm.addPhoneTrack(t, open) }
                return@ScrollColumn
            }
            // The AI queue: how many separations are waiting or running.
            val c = app.supersax.ui.nav.LocalContainer.current
            val queue by c.separationQueue.jobs.collectAsStateWithLifecycle(emptyList())
            if (queue.isNotEmpty()) {
                val running = queue.count { it.active }
                OutlinedButton(onClick = onQueue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp))
                    Text(if (running > 0) pluralStringResource(R.plurals.queue_running, running, running) else stringResource(R.string.queue_title))
                }
            }
            val playlists by c.playlists.all.collectAsStateWithLifecycle(emptyList())
            PlaylistsSection(playlists, onPlaylist)
            val list = songs ?: return@ScrollColumn
            var addTo by remember { mutableStateOf<String?>(null) }
            addTo?.let { id -> AddToPlaylistDialog(id) { addTo = null } }
            val originals = list.filter { it.setup.parentId == null || it.setup.parentId !in list.map { r -> r.ref.id }.toSet() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                app.supersax.ui.components.Overline(stringResource(R.string.songs_all), Modifier.weight(1f).padding(start = 4.dp))
                if (originals.isNotEmpty()) TextButton(onClick = { c.playback.playSongs(originals.map { it.ref }) }) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.songs_play_all))
                }
            }
            if (list.isEmpty()) EmptyState(stringResource(R.string.songs_empty))
            // AI versions ("no sax"…) are listed under their original song.
            val ids = list.map { it.ref.id }.toSet()
            val versions = list.filter { it.setup.parentId != null && it.setup.parentId in ids }.groupBy { it.setup.parentId!! }
            list.filter { it.setup.parentId == null || it.setup.parentId !in ids }.forEach { row ->
                SectionCard(modifier = Modifier.clickable { onSong(row.ref.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.MusicNote)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.ref.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(songSummary(row.setup) ?: stringResource(R.string.song_not_analysed), style = MaterialTheme.typography.bodySmall,
                                color = Sax.colors.textSecondary)
                        }
                        IconButton(onClick = { c.playback.playSongs(listOf(row.ref)) }) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.play), tint = Sax.colors.accentInk) }
                        IconButton(onClick = { addTo = row.ref.id }) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.pl_add), tint = Sax.colors.accentInk)
                        }
                        IconButton(onClick = { vm.remove(row.ref); versions[row.ref.id]?.forEach { v -> vm.remove(v.ref) } }) {
                            Icon(Icons.Rounded.Delete, stringResource(R.string.remove), tint = Sax.colors.textTertiary)
                        }
                    }
                    versions[row.ref.id]?.let { vs -> VersionChips(vs.map { it.ref.id to it.setup.removed }, null) { onSong(it) } }
                }
            }
        }
    }
}

// --- YouTube search -------------------------------------------------------------------------

data class SearchUi(
    val searching: Boolean = false,
    val results: List<YouTubeSource.Result> = emptyList(),
    val error: Boolean = false,
    /** url → download progress 0..1 */
    val downloading: Map<String, Float> = emptyMap(),
    val failed: String? = null,
)

class YouTubeSearchViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUi())
    val ui = _ui.asStateFlow()

    fun search(query: String, language: String) {
        if (query.isBlank()) return
        _ui.value = _ui.value.copy(searching = true, error = false)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { YouTubeSource.search(query.trim(), language) } }
            _ui.value = _ui.value.copy(searching = false, results = r.getOrDefault(emptyList()), error = r.isFailure)
        }
    }

    fun download(item: YouTubeSource.Result, language: String, onDone: (String) -> Unit) {
        if (item.url in _ui.value.downloading) return
        _ui.value = _ui.value.copy(downloading = _ui.value.downloading + (item.url to 0f), failed = null)
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    YouTubeSource.download(item.url, c.songsDir, language) { p ->
                        _ui.value = _ui.value.copy(downloading = _ui.value.downloading + (item.url to p.toFloat()))
                    }
                }
            }
            _ui.value = _ui.value.copy(downloading = _ui.value.downloading - item.url, failed = if (r.isFailure) item.url else null)
            val d = r.getOrNull() ?: return@launch
            val rec = AudioRefRecord(UUID.randomUUID().toString(), Uri.fromFile(d.file).toString(), d.title, c.clock.nowMs())
            c.media.saveAudioRef(rec)
            c.songs.save(rec.id, SongSetup(source = item.url))
            onDone(rec.id)
        }
    }
}

@Composable
fun YouTubeSearchScreen(onBack: () -> Unit, onSong: (String) -> Unit) {
    val vm = appViewModel { YouTubeSearchViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val language = LocaleHelper.effective(LocalContext.current)
    var query by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(stringResource(R.string.yt_search), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text(stringResource(R.string.yt_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query, language) }),
            )
            Button(onClick = { vm.search(query, language) }, enabled = !ui.searching && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) { Text(stringResource(R.string.yt_search_go)) }
            if (ui.searching) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Sax.colors.accentInk) }
            if (ui.error) ErrorBanner(stringResource(R.string.yt_search_failed))
            if (ui.failed != null) ErrorBanner(stringResource(R.string.yt_download_failed))
            ui.results.forEach { r ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOf(r.uploader, if (r.durationSec > 0) formatClock(r.durationSec) else "").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
                        }
                        val p = ui.downloading[r.url]
                        if (p != null) CircularProgressIndicator(progress = { p }, modifier = Modifier.size(36.dp), color = Sax.colors.accentInk)
                        else IconButton(onClick = { vm.download(r, language, onSong) }) {
                            Icon(Icons.Rounded.Download, stringResource(R.string.yt_download), tint = Sax.colors.accentInk)
                        }
                    }
                    ui.downloading[r.url]?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Sax.colors.accent) }
                }
            }
        }
    }
}

/** Chips to open the AI versions of a song ("no sax", "no lead"); [current] is highlighted, null id = the original. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VersionChips(versions: List<Pair<String, String?>>, current: String?, original: String? = null, onOpen: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (original != null) androidx.compose.material3.FilterChip(selected = current == original, onClick = { if (current != original) onOpen(original) },
            label = { Text(stringResource(R.string.version_original)) }, modifier = Modifier.heightIn(min = 40.dp))
        versions.forEach { (id, removed) ->
            val stem = app.supersax.separation.Removal.of(removed)
            androidx.compose.material3.FilterChip(selected = current == id, onClick = { if (current != id) onOpen(id) },
                label = { Text(if (stem != null) stringResource(R.string.version_without, stemShort(stem)) else stringResource(R.string.version_ai)) },
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.heightIn(min = 40.dp))
        }
    }
}
