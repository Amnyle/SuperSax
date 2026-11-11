package app.supersax.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.supersax.domain.library.PieceState
import app.supersax.ui.components.EmptyState
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.nav.appViewModel

@Composable
fun stateLabel(s: PieceState): String = stringResource(
    when (s) {
        PieceState.LOCKED -> R.string.state_locked
        PieceState.STRETCH -> R.string.state_stretch
        PieceState.AVAILABLE -> R.string.state_available
        PieceState.LEARNING -> R.string.stage_learning
        PieceState.PLAYABLE -> R.string.stage_playable
        PieceState.CLEAN_AT_TEMPO -> R.string.stage_clean
    },
)

/** Library: bundled and imported pieces with their state for this player; imports of MusicXML and PDF. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(onPiece: (String) -> Unit, onPdf: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it) }
    val matches by vm.matches.collectAsStateWithLifecycle()
    val files by vm.userFiles.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<PieceState?>(null) }
    val context = LocalContext.current
    val language = LocaleHelper.effective(context)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(context.contentResolver, uri) }
    ScreenScaffold(stringResource(R.string.library), actions = {
        TextButton(onClick = { importer.launch(arrayOf("application/pdf", "application/vnd.recordare.musicxml+xml", "application/xml", "text/xml", "*/*")) }) {
            Text(stringResource(R.string.import_file))
        }
    }) { padding ->
        ScrollColumn(padding) {
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary); TextButton(onClick = vm::clearMessage) { Text(stringResource(R.string.ok)) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.all)) }, modifier = Modifier.heightIn(min = 48.dp))
                PieceState.entries.forEach { s ->
                    FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(stateLabel(s)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            val shown = matches.filter { filter == null || it.state == filter }.sortedWith(compareBy({ it.piece.difficulty == 0 }, { it.piece.difficulty }, { it.piece.file.title.get(language) }))
            if (shown.isEmpty()) EmptyState(stringResource(R.string.library_empty))
            shown.forEach { m ->
                val p = m.piece
                SectionCard(modifier = Modifier.clickable { onPiece(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(p.file.title.get(language), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (p.difficulty > 0) Text(stringResource(R.string.difficulty_short, p.difficulty))
                    }
                    Text("${stateLabel(m.state)} · ${p.file.composer}", style = MaterialTheme.typography.bodySmall)
                }
            }
            val pdfs = files.filter { it.kind == "pdf" }
            if (pdfs.isNotEmpty()) {
                Text(stringResource(R.string.pdf_scores), style = MaterialTheme.typography.titleMedium)
                pdfs.forEach { f ->
                    SectionCard(modifier = Modifier.clickable { onPdf(f.fileName) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.title, Modifier.weight(1f))
                            TextButton(onClick = { vm.deleteUserFile(f) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
}
