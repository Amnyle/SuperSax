package app.supersax.ui.songs

import androidx.compose.material.icons.rounded.Download

import androidx.compose.material.icons.rounded.Share

import kotlinx.coroutines.launch

import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.song.SongBars
import app.supersax.domain.song.SongKeys
import app.supersax.domain.song.SongSetup
import app.supersax.ui.components.ErrorBanner
import app.supersax.ui.components.Overline
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.Stepper
import app.supersax.ui.components.formatClock
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.Sax
import kotlin.math.roundToInt

@Composable
private fun keyName(tonic: Int, minor: Boolean): String {
    val naming = LocalSettings.current.noteNaming
    return stringResource(if (minor) R.string.key_minor else R.string.key_major, naming.format(SongKeys.tonicName(tonic, minor)))
}

/** The song player: tempo and key changes, bars, a part to loop or stop at, checkpoints, bar corrections. */
@Composable
fun SongPlayerScreen(songId: String, onBack: () -> Unit, onOpenSong: (String) -> Unit = {}, onQueue: () -> Unit = {}) {
    val vm = appViewModel(key = "song-$songId") { SongPlayerViewModel(it, songId) }
    val ref by vm.ref.collectAsStateWithLifecycle()
    val s by vm.setup.collectAsStateWithLifecycle()
    val ui by vm.player.ui.collectAsStateWithLifecycle()
    val scopeDel = androidx.compose.runtime.rememberCoroutineScope()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val part by vm.part.collectAsStateWithLifecycle()
    val mix by vm.mix.collectAsStateWithLifecycle()
    var addTo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var deleting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (deleting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.version_delete_q, ref?.title ?: "")) },
        text = { Text(stringResource(R.string.version_delete_body)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            deleting = false
            scopeDel.launch { vm.deleteVersion()?.let { onOpenSong(it) } }
        }) { Text(stringResource(R.string.version_delete)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (addTo) AddToPlaylistDialog(songId) { addTo = false }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val savedFmt = stringResource(R.string.export_saved)
    val failedFmt = stringResource(R.string.export_failed)
    fun toast(s: String) = android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_LONG).show()
    // Older Android: the user picks where to save.
    val saveAs = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("audio/*")) { target ->
        val r = ref
        if (target != null && r != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.supersax.playback.SongExport.copy(ctx, android.net.Uri.parse(r.uri), target) } }
            toast(if (ok.isSuccess) String.format(savedFmt, r.title) else String.format(failedFmt, ok.exceptionOrNull()?.message ?: ""))
        }
    }
    ScreenScaffold(ref?.title ?: "", onBack = onBack, actions = {
        androidx.compose.material3.IconButton(onClick = {
            val r = ref ?: return@IconButton
            scope.launch {
                val i = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.supersax.playback.SongExport.shareIntent(ctx, r) } }
                i.onSuccess { ctx.startActivity(it) }.onFailure { toast(String.format(failedFmt, it.message ?: "")) }
            }
        }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Share, stringResource(R.string.export_share)) }
        androidx.compose.material3.IconButton(onClick = {
            val r = ref ?: return@IconButton
            if (android.os.Build.VERSION.SDK_INT < 29) {
                saveAs.launch(app.supersax.playback.SongExport.fileName(r.title, app.supersax.playback.SongExport.extension(ctx, android.net.Uri.parse(r.uri))))
                return@IconButton
            }
            scope.launch {
                val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.supersax.playback.SongExport.saveToDownloads(ctx, r) } }
                toast(res.fold({ String.format(savedFmt, it) }, { String.format(failedFmt, it.message ?: "") }))
            }
        }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Download, stringResource(R.string.export_save)) }
        val activeId by vm.active.collectAsStateWithLifecycle()
        val activeIsVersion = vm.versions.collectAsStateWithLifecycle().value.firstOrNull { it.first == activeId }?.second != null || (activeId == songId && s.parentId != null)
        if (activeIsVersion) androidx.compose.material3.IconButton(onClick = { deleting = true }) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Delete, stringResource(R.string.version_delete))
        }
        androidx.compose.material3.IconButton(onClick = { addTo = true }) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.pl_add))
        }
    }) { padding ->
        ScrollColumn(padding) {
            // The song and its AI versions: switching keeps playing, at the same place, without a gap.
            val versions by vm.versions.collectAsStateWithLifecycle()
            val active by vm.active.collectAsStateWithLifecycle()
            if (versions.size > 1) {
                val root = versions.firstOrNull { it.second == null }?.first ?: songId
                VersionChips(versions.filter { it.first != root }, active, original = root) { id -> if (!vm.selectVersion(id)) { vm.handoffTo(id); onOpenSong(id) } }
            }
            ui.error?.let { ErrorBanner(stringResource(R.string.audio_open_failed, it)) }
            analysis.progress?.let { p ->
                SectionCard {
                    Text(stringResource(if (analysis.preparing) R.string.song_preparing else R.string.song_analysing), style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = Sax.colors.accent, trackColor = Sax.colors.elevated)
                }
            }
            if (analysis.failed) ErrorBanner(stringResource(R.string.song_analysis_failed), stringResource(R.string.retry)) { vm.analyse() }
            InfoRow(s, ui.semitones)
            KeySignatureCard(s, ui.semitones)
            Transport(vm, s, ui.playing, ui.positionMs, ui.durationMs)
            if (s.analyzed) MixCard(vm, mix)
            if (s.analyzed) {
                BarStrip(vm, s, ui.positionMs, ui.durationMs, part)
                PartCard(vm, s, ui.durationMs, part)
            }
            CheckpointsCard(vm, s)
            TempoCard(vm, s, ui.speed)
            KeyCard(vm, s, ui.semitones)
            ref?.let { SeparationCard(it, onOpenSong, onQueue) }
            if (s.analyzed) AdjustCard(vm, s)
            Text(stringResource(R.string.song_detect_note), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoRow(s: SongSetup, semitones: Int) {
    if (!s.analyzed) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.keyTonic >= 0) {
            val tonic = SongKeys.transpose(s.keyTonic, semitones)
            Pill(stringResource(R.string.song_key_concert, keyName(tonic, s.keyMinor)), strong = true)
            Pill(stringResource(R.string.song_key_written, keyName(SongKeys.altoWritten(tonic), s.keyMinor)))
        }
        Pill(stringResource(R.string.bpm_value, s.bpm.roundToInt()))
        Pill(stringResource(R.string.song_time_sig, s.beatsPerBar))
    }
}

@Composable
private fun Pill(text: String, strong: Boolean = false) {
    Box(Modifier.clip(CircleShape).background(if (strong) Sax.colors.accent else MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (strong) Sax.colors.onAccent else Sax.colors.text)
    }
}

@Composable
private fun Transport(vm: SongPlayerViewModel, s: SongSetup, playing: Boolean, pos: Long, dur: Long) {
    val bar = if (s.analyzed) SongBars.barAt(pos, s.bpm, s.firstBeatMs, s.beatsPerBar) else 0
    val bars = if (s.analyzed) SongBars.barCount(dur, s.bpm, s.firstBeatMs, s.beatsPerBar) else 0
    SectionCard {
        Row(verticalAlignment = Alignment.Bottom) {
            if (s.analyzed) {
                Column(Modifier.weight(1f)) {
                    Overline(stringResource(R.string.song_bar))
                    Text(if (bar == 0) stringResource(R.string.song_intro) else "$bar / $bars", style = MaterialTheme.typography.displayMedium)
                }
            } else Spacer(Modifier.weight(1f))
            Text("${formatClock(pos / 1000)} / ${formatClock(dur / 1000)}", style = MaterialTheme.typography.titleSmall, color = Sax.colors.textSecondary)
        }
        Slider(
            value = pos.toFloat(), onValueChange = { vm.player.seekTo(it.toLong()) }, valueRange = 0f..dur.coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(thumbColor = Sax.colors.accentInk, activeTrackColor = Sax.colors.accent, inactiveTrackColor = Sax.colors.elevated),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (s.analyzed) vm.seekToBar((bar - 1).coerceAtLeast(1)) }, enabled = s.analyzed) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.song_prev_bar), modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { vm.player.seekTo((pos - 5000).coerceAtLeast(0)) }) { Icon(Icons.Rounded.Replay5, stringResource(R.string.song_back5), modifier = Modifier.size(30.dp)) }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Sax.colors.accent).clickable { vm.player.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play),
                    tint = Sax.colors.onAccent, modifier = Modifier.size(44.dp))
            }
            IconButton(onClick = { vm.player.seekTo((pos + 5000).coerceAtMost(dur)) }) { Icon(Icons.Rounded.Forward5, stringResource(R.string.song_fwd5), modifier = Modifier.size(30.dp)) }
            IconButton(onClick = { if (s.analyzed) vm.seekToBar((bar + 1).coerceAtMost(bars)) }, enabled = s.analyzed) {
                Icon(Icons.Rounded.SkipNext, stringResource(R.string.song_next_bar), modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** Every bar as a number; the current one in amber, the part being practised tinted. Tap to jump. */
@Composable
private fun BarStrip(vm: SongPlayerViewModel, s: SongSetup, pos: Long, dur: Long, part: Part?) {
    val bars = SongBars.barCount(dur, s.bpm, s.firstBeatMs, s.beatsPerBar)
    if (bars <= 0) return
    val current = SongBars.barAt(pos, s.bpm, s.firstBeatMs, s.beatsPerBar)
    val state = rememberLazyListState()
    LaunchedEffect(current) { if (current > 0) state.animateScrollToItem((current - 3).coerceAtLeast(0)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(bars) { i ->
            val n = i + 1
            val start = vm.barStart(n, s)
            val inPart = part != null && start >= part.fromMs && start < part.toMs
            val bg = when {
                n == current -> Sax.colors.accent
                inPart -> Sax.colors.accentSoft
                else -> MaterialTheme.colorScheme.surface
            }
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable { vm.seekToBar(n) }, contentAlignment = Alignment.Center) {
                Text("$n", style = MaterialTheme.typography.titleSmall, color = if (n == current) Sax.colors.onAccent else Sax.colors.text)
            }
        }
    }
}

@Composable
private fun PartCard(vm: SongPlayerViewModel, s: SongSetup, dur: Long, part: Part?) {
    val bars = SongBars.barCount(dur, s.bpm, s.firstBeatMs, s.beatsPerBar).coerceAtLeast(1)
    var from by rememberSaveable { mutableIntStateOf(1) }
    var to by rememberSaveable { mutableIntStateOf(4) }
    var stop by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.song_part)) {
        Stepper(stringResource(R.string.song_from_bar), from, { from = it; if (to < it) to = it }, 1..bars)
        Stepper(stringResource(R.string.song_to_bar), to, { to = it.coerceAtLeast(from) }, 1..bars)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !stop, onClick = { stop = false }, label = { Text(stringResource(R.string.song_loop)) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = stop, onClick = { stop = true }, label = { Text(stringResource(R.string.song_stop_end)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.playBars(from, to, stop) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                Text(stringResource(R.string.song_play_part))
            }
            if (part != null) OutlinedButton(onClick = vm::clearPart, modifier = Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.clear)) }
        }
        if (part != null) Text(
            stringResource(if (part.stop) R.string.song_part_stop else R.string.song_part_loop, formatClock(part.fromMs / 1000), formatClock(part.toMs / 1000)),
            style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary,
        )
    }
}

@Composable
private fun CheckpointsCard(vm: SongPlayerViewModel, s: SongSetup) {
    var name by rememberSaveable { mutableStateOf("") }
    SectionCard(stringResource(R.string.song_checkpoints)) {
        if (s.checkpoints.isEmpty()) Text(stringResource(R.string.song_checkpoints_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        s.checkpoints.forEach { cp ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, null, tint = Sax.colors.accentInk)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { vm.player.seekTo(cp.ms) }) {
                    Text(cp.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val bar = if (s.analyzed) SongBars.barAt(cp.ms, s.bpm, s.firstBeatMs, s.beatsPerBar) else 0
                    Text((if (bar > 0) stringResource(R.string.song_bar_n, bar) + " · " else "") + formatClock(cp.ms / 1000),
                        style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
                }
                TextButton(onClick = { vm.playFromCheckpoint(cp, stop = false) }) { Text(stringResource(R.string.song_loop)) }
                TextButton(onClick = { vm.playFromCheckpoint(cp, stop = true) }) { Text(stringResource(R.string.song_to_next)) }
                IconButton(onClick = { vm.deleteCheckpoint(cp.id) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete), tint = Sax.colors.textTertiary) }
            }
        }
        val here = stringResource(R.string.song_checkpoint_default)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.song_checkpoint_name)) })
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { vm.addCheckpoint(name.ifBlank { here }); name = "" }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.Flag, stringResource(R.string.song_add_checkpoint))
            }
        }
        Text(stringResource(R.string.song_add_checkpoint_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
    }
}

@Composable
private fun TempoCard(vm: SongPlayerViewModel, s: SongSetup, speed: Float) {
    SectionCard(stringResource(R.string.song_tempo)) {
        val percent = (speed * 100).roundToInt()
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.stepTempo(-1) }, enabled = percent > 50, modifier = Modifier.heightIn(min = 56.dp)) { Text("−10 %") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.analyzed && s.bpm > 0) {
                    Text(stringResource(R.string.bpm_value, (s.bpm * speed).roundToInt()), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.song_tempo_percent, percent, s.bpm.roundToInt()), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
                } else Text("$percent %", style = MaterialTheme.typography.headlineMedium)
            }
            OutlinedButton(onClick = { vm.stepTempo(1) }, enabled = percent < 150, modifier = Modifier.heightIn(min = 56.dp)) { Text("+10 %") }
        }
        TextButton(onClick = { vm.player.setSpeed(1f) }) { Text(stringResource(R.string.song_original_tempo)) }
    }
}

@Composable
private fun KeyCard(vm: SongPlayerViewModel, s: SongSetup, semitones: Int) {
    SectionCard(stringResource(R.string.song_key)) {
        if (s.keyTonic >= 0) Text(
            stringResource(R.string.song_key_line, keyName(s.keyTonic, s.keyMinor), keyName(SongKeys.transpose(s.keyTonic, semitones), s.keyMinor)),
            style = MaterialTheme.typography.bodyLarge,
        )
        Stepper(stringResource(R.string.song_semitones), semitones, { vm.setSemitones(it) }, -6..6) { if (it > 0) "+$it" else "$it" }
        TextButton(onClick = { vm.setSemitones(0) }) { Text(stringResource(R.string.song_original_key)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdjustCard(vm: SongPlayerViewModel, s: SongSetup) {
    SectionCard(stringResource(R.string.song_adjust)) {
        Text(stringResource(R.string.song_adjust_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        Button(onClick = vm::barOneHere, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.song_bar_one_here)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.bpm_value_decimal, s.bpm), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { vm.nudgeBpm(-0.5) }) { Text("−0.5") }
            OutlinedButton(onClick = { vm.nudgeBpm(0.5) }) { Text("+0.5") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.scaleBpm(0.5) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.song_half_bpm)) }
            OutlinedButton(onClick = { vm.scaleBpm(2.0) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.song_double_bpm)) }
        }
        Text(stringResource(R.string.song_beats_per_bar), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 3, 4, 6).forEach { n ->
                FilterChip(selected = s.beatsPerBar == n, onClick = { vm.setBeatsPerBar(n) }, label = { Text(stringResource(R.string.song_time_sig, n)) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::resetBars) { Text(stringResource(R.string.song_reset_tempo)) }
            TextButton(onClick = vm::analyse) { Text(stringResource(R.string.song_analyse_again)) }
        }
    }
}

/** The key signature on a treble staff: written for alto (default) or concert; follows the transposition. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeySignatureCard(s: SongSetup, semitones: Int) {
    if (!s.analyzed || s.keyTonic < 0) return
    var shown by rememberSaveable { mutableStateOf(false) }
    var concert by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { shown = !shown }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(if (shown) R.string.song_hide_signature else R.string.song_show_signature))
    }
    if (!shown) return
    val tonic = SongKeys.transpose(s.keyTonic, semitones)
    val shownTonic = if (concert) tonic else SongKeys.altoWritten(tonic)
    val fifths = SongKeys.fifths(shownTonic, s.keyMinor)
    SectionCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !concert, onClick = { concert = false }, label = { Text(stringResource(R.string.song_written_alto)) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = concert, onClick = { concert = true }, label = { Text(stringResource(R.string.song_concert)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        Text(keyName(shownTonic, s.keyMinor), style = MaterialTheme.typography.headlineSmall)
        app.supersax.notation.StaffView(
            app.supersax.domain.library.Score(fifths, 4, 4, 60, listOf(app.supersax.domain.library.Measure(emptyList()))),
            staffSpace = 16.dp, showTime = false,
            description = stringResource(R.string.song_signature_desc, keyName(shownTonic, s.keyMinor)),
        )
        Text(
            if (fifths == 0) stringResource(R.string.song_no_accidentals)
            else androidx.compose.ui.res.pluralStringResource(if (fifths > 0) R.plurals.sharps_n else R.plurals.flats_n, kotlin.math.abs(fifths), kotlin.math.abs(fifths)),
            style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary,
        )
    }
}

/** Metronome clicks over the song, and the volumes of the music and of the clicks. */
@Composable
private fun MixCard(vm: SongPlayerViewModel, mix: MixUi) {
    SectionCard(stringResource(R.string.song_metronome)) {
        Row(
            Modifier.fillMaxWidth().then(Modifier.toggleable(mix.clicks, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = vm::setClicks)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.song_clicks), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.song_clicks_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
            androidx.compose.material3.Switch(checked = mix.clicks, onCheckedChange = null)
        }
        VolumeRow(stringResource(R.string.song_music_volume), mix.musicVolume, vm::setMusicVolume)
        if (mix.clicks) VolumeRow(stringResource(R.string.song_click_volume), mix.clickVolume, vm::setClickVolume)
    }
}

@Composable
private fun VolumeRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value, onValueChange = onChange, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Sax.colors.accentInk, activeTrackColor = Sax.colors.accent, inactiveTrackColor = Sax.colors.elevated),
        )
        Text("${(value * 100).roundToInt()} %", Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Switch between a song and its AI versions (shown only when there are versions). */
@Composable
fun SongVersionsRow(songId: String, setup: SongSetup, onOpen: (String) -> Unit) {
    val c = app.supersax.ui.nav.LocalContainer.current
    val originalId = setup.parentId ?: songId
    val versions by androidx.compose.runtime.produceState<List<Pair<String, String?>>>(emptyList(), originalId) {
        c.media.audioRefs.collect { refs ->
            value = refs.mapNotNull { r -> c.songs.get(r.id).takeIf { it.parentId == originalId }?.let { r.id to it.removed } }
        }
    }
    if (versions.isEmpty()) return
    VersionChips(versions, songId, original = originalId) { onOpen(it) }
}
