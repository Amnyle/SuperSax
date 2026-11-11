package app.saxpractice.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.saxpractice.LocaleHelper
import app.saxpractice.R
import app.saxpractice.domain.common.PieceProgressRecord
import app.saxpractice.domain.common.PieceStage
import app.saxpractice.domain.library.Missing
import app.saxpractice.domain.library.PieceMatch
import app.saxpractice.domain.pitch.KeySignature
import app.saxpractice.domain.pitch.SpelledPitch
import app.saxpractice.notation.StaffView
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.components.Stepper
import app.saxpractice.ui.components.formatClock
import app.saxpractice.ui.components.scaleLabel
import app.saxpractice.ui.goals.stageLabel
import app.saxpractice.ui.nav.LocalSettings
import app.saxpractice.ui.nav.appViewModel

/** One piece: staff (written alto by default, concert on request), Listen, sections and stages. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PieceScreen(pieceId: String, onBack: () -> Unit, onPdf: (String) -> Unit, onRecord: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it) }
    val matches by vm.matches.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val files by vm.userFiles.collectAsStateWithLifecycle()
    val out by vm.output.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = LocaleHelper.effective(context)
    val settings = LocalSettings.current
    var concert by rememberSaveable { mutableStateOf(false) }
    val attach = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(context.contentResolver, uri, attachTo = pieceId)
    }
    val m: PieceMatch = matches.firstOrNull { it.piece.id == pieceId } ?: run {
        ScreenScaffold(stringResource(R.string.library), onBack = onBack) {}
        return
    }
    val p = m.piece
    var tempo by rememberSaveable { mutableIntStateOf(p.file.tempo ?: 80) }

    ScreenScaffold(p.file.title.get(language), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Text("${p.file.composer} · ${p.file.source}", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.licence_line, p.file.licence, p.file.credit), style = MaterialTheme.typography.bodySmall)
            if (!p.file.reviewed) Text(stringResource(R.string.not_reviewed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(stateLabel(m.state), style = MaterialTheme.typography.titleMedium)
            m.missing.forEach { Text("• " + missingText(it), style = MaterialTheme.typography.bodyMedium) }

            val score = p.score
            if (score == null) {
                SectionCard(stringResource(R.string.metadata_only)) {
                    Text(stringResource(R.string.metadata_only_hint))
                    OutlinedButton(onClick = { attach.launch(arrayOf("application/pdf", "application/xml", "text/xml", "*/*")) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.attach_file))
                    }
                    files.filter { it.attachedToPieceId == pieceId }.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.title, Modifier.weight(1f))
                            if (f.kind == "pdf") TextButton(onClick = { onPdf(f.fileName) }) { Text(stringResource(R.string.open)) }
                            else Text(stringResource(R.string.in_library))
                        }
                    }
                }
            } else {
                val writtenScore = score.toWritten(settings.instrument)
                val shown = if (concert) score else writtenScore
                Text(
                    stringResource(R.string.piece_facts, p.difficulty, scaleLabel(p.scaleKey), p.file.time ?: "", p.file.tempo ?: 0, formatClock(p.durationSec.toLong()),
                        settings.noteNaming.format(SpelledPitch.fromMidi(settings.instrument.concertToWrittenMidi(p.lowMidi!!))),
                        settings.noteNaming.format(SpelledPitch.fromMidi(settings.instrument.concertToWrittenMidi(p.highMidi!!)))),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (concert) R.string.showing_concert else R.string.showing_written), Modifier.weight(1f))
                    Switch(checked = concert, onCheckedChange = { concert = it })
                }
                StaffView(shown, highlightIndex = if (out.melodyPlaying) vm.highlighted(out.melodyIndex) else -1,
                    description = stringResource(R.string.staff_piece_description, KeySignature(shown.keyFifths).fifths))
                Stepper(stringResource(R.string.listen_tempo), tempo, { tempo = it }, 30..240, 4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.listen(score, tempo) }, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text(stringResource(if (out.melodyPlaying) R.string.stop else R.string.listen))
                    }
                    OutlinedButton(onClick = { onRecord(pieceId) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.record_take)) }
                }
                StageRow(stringResource(R.string.whole_piece), stageOf(progress, pieceId, "")) { vm.setStage(pieceId, "", it) }
                p.file.sections.forEach { s ->
                    StageRow("${s.title.get(language)} (${s.from}–${s.to})", stageOf(progress, pieceId, s.id)) { vm.setStage(pieceId, s.id, it) }
                }
            }
        }
    }
}

private fun stageOf(progress: List<PieceProgressRecord>, pieceId: String, sectionId: String) =
    progress.firstOrNull { it.pieceId == pieceId && it.sectionId == sectionId }?.stage ?: PieceStage.NOT_STARTED

/** Stage buttons for a piece or a section. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageRow(label: String, current: PieceStage, onStage: (PieceStage) -> Unit) {
    SectionCard(label) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PieceStage.entries.forEach { s ->
                FilterChip(selected = current == s, onClick = { onStage(s) }, label = { Text(stageLabel(s)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
}

@Composable
fun missingText(m: Missing): String {
    val naming = LocalSettings.current.noteNaming
    return when (m) {
        is Missing.RangeLow -> stringResource(R.string.missing_low, naming.format(SpelledPitch.fromMidi(m.writtenMidi)), m.semitones)
        is Missing.RangeHigh -> stringResource(R.string.missing_high, naming.format(SpelledPitch.fromMidi(m.writtenMidi)), m.semitones)
        is Missing.Scale -> stringResource(if (m.isNextUnlock) R.string.missing_scale_next else R.string.missing_scale, scaleLabel(m.scaleKey))
        Missing.NoNotes -> stringResource(R.string.missing_no_notes)
    }
}
