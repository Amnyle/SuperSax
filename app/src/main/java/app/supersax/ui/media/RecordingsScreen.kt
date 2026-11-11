package app.supersax.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.ui.audio.rememberMicRequest
import app.supersax.ui.components.BigButton
import app.supersax.ui.components.EmptyState
import app.supersax.ui.components.ErrorBanner
import app.supersax.ui.components.ScalePickerDialog
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.formatClock
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.settings.ToggleRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Recordings: record, tag (exercise or piece, reed, mouthpiece), benchmark, compare two takes A/B. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingsScreen(pieceId: String?, onBack: () -> Unit) {
    val vm = appViewModel { RecordingsViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val list by vm.recordings.collectAsStateWithLifecycle()
    val mouthpieces by vm.mouthpieces.collectAsStateWithLifecycle()
    val pa by vm.playerA.ui.collectAsStateWithLifecycle()
    val language = LocaleHelper.effective(LocalContext.current)
    var scaleKey by rememberSaveable { mutableStateOf<String?>(null) }
    var benchmark by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var mouthpiece by rememberSaveable { mutableStateOf<String?>(null) }
    var pickScale by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val request = rememberMicRequest { granted -> if (granted) vm.start() }
    val fmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    val piece = vm.piece(pieceId)

    ScreenScaffold(stringResource(R.string.recordings), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (ui.error) ErrorBanner(stringResource(R.string.record_failed))
            SectionCard(stringResource(R.string.new_take)) {
                piece?.let { Text(stringResource(R.string.take_for_piece, it.file.title.get(language))) }
                if (piece == null) TextButton(onClick = { pickScale = true }) {
                    Text(scaleKey?.let { stringResource(R.string.take_for_scale, scaleLabel(it)) } ?: stringResource(R.string.tag_scale))
                }
                if (mouthpieces.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    mouthpieces.forEach { m -> FilterChip(selected = mouthpiece == m.id, onClick = { mouthpiece = if (mouthpiece == m.id) null else m.id }, label = { Text(m.name) }) }
                }
                ToggleRow(stringResource(R.string.benchmark), stringResource(R.string.benchmark_hint), benchmark) { benchmark = it }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note_optional)) }, singleLine = true)
                if (ui.recording) {
                    Text(formatClock(ui.elapsedMs / 1000), style = MaterialTheme.typography.displaySmall)
                    BigButton(stringResource(R.string.stop_recording), { vm.stop(scaleKey, pieceId, mouthpiece, benchmark, note); note = "" })
                } else BigButton(stringResource(R.string.start_recording), request)
            }
            ui.compare?.let { (a, b) ->
                SectionCard(stringResource(R.string.compare_takes)) {
                    Text(stringResource(if (ui.aActive) R.string.hearing_a else R.string.hearing_b), style = MaterialTheme.typography.titleMedium)
                    Text(formatClock(pa.positionMs / 1000) + " / " + formatClock(pa.durationMs / 1000))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::comparePlayPause, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.play_pause)) }
                        Button(onClick = vm::toggleAB, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.ab_toggle)) }
                        TextButton(onClick = vm::closeCompare) { Text(stringResource(R.string.close)) }
                    }
                }
            }
            if (selected.size == 2) Button(onClick = {
                val a = list.first { it.id == selected[0] }; val b = list.first { it.id == selected[1] }
                vm.compare(a, b)
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.compare_selected)) }
            val shown = if (pieceId != null) list.filter { it.pieceId == pieceId } else list
            if (shown.isEmpty()) EmptyState(stringResource(R.string.recordings_empty))
            shown.forEach { r ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Instant.ofEpochMilli(r.atMs).atZone(ZoneId.systemDefault()).format(fmt), Modifier.weight(1f))
                        Text(formatClock(r.durationMs / 1000))
                    }
                    val tags = listOfNotNull(
                        r.pieceId?.let { vm.piece(it)?.file?.title?.get(language) },
                        r.exerciseKey?.let { scaleLabel(it) },
                        if (r.benchmark) stringResource(R.string.benchmark) else null,
                        r.note.takeIf { it.isNotBlank() },
                    )
                    if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { if (ui.playingId == r.id) vm.stopAll() else vm.play(r) }) {
                            Text(stringResource(if (ui.playingId == r.id) R.string.stop else R.string.play))
                        }
                        FilterChip(selected = r.id in selected, onClick = { if (r.id in selected) selected.remove(r.id) else { if (selected.size == 2) selected.removeAt(0); selected.add(r.id) } },
                            label = { Text(stringResource(R.string.select_for_compare)) })
                        TextButton(onClick = { vm.setBenchmark(r, !r.benchmark) }) { Text(stringResource(if (r.benchmark) R.string.unmark_benchmark else R.string.mark_benchmark)) }
                        if (r.pieceId != null || r.exerciseKey != null) TextButton(onClick = { vm.attachEvidence(r) }) { Text(stringResource(R.string.use_as_evidence)) }
                        TextButton(onClick = { vm.delete(r) }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }
    if (pickScale) ScalePickerDialog(onPick = { scaleKey = it; pickScale = false }, onDismiss = { pickScale = false })
}
