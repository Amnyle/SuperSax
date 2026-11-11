package app.saxpractice.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.saxpractice.LocaleHelper
import app.saxpractice.R
import app.saxpractice.domain.library.Piece
import app.saxpractice.ui.nav.LocalContainer

/** Picks a library piece (all pieces, including metadata-only ones when [withNotesOnly] is false). */
@Composable
fun PiecePickerDialog(onPick: (Piece) -> Unit, onDismiss: () -> Unit, withNotesOnly: Boolean = true) {
    val c = LocalContainer.current
    val pieces by c.libraryService.pieces.collectAsStateWithLifecycle()
    val language = LocaleHelper.effective(LocalContext.current)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.pick_piece)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(pieces.filter { !withNotesOnly || it.hasNotes }.sortedBy { it.difficulty }) { p ->
                    Text(
                        p.file.title.get(language) + if (p.difficulty > 0) " · " + stringResource(R.string.difficulty_short, p.difficulty) else "",
                        Modifier.fillMaxWidth().clickable { onPick(p) }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                    )
                }
            }
        },
    )
}
