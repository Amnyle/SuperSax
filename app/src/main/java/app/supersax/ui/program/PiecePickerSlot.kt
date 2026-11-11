package app.supersax.ui.program

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.ui.library.PiecePickerDialog
import app.supersax.ui.nav.LocalContainer

/** Piece target of a new-material or play-for-fun exercise: a fixed piece, or chosen by the library each session. */
@Composable
fun PiecePickerSlot(pieceId: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = LocalContainer.current
    val language = LocaleHelper.effective(LocalContext.current)
    val title = c.libraryService.piece(pieceId)?.file?.title?.get(language)
    TextButton(onClick = { open = true }) { Text(title?.let { stringResource(R.string.piece_fixed, it) } ?: stringResource(R.string.piece_auto)) }
    if (pieceId != null) TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.piece_use_auto)) }
    if (open) PiecePickerDialog(onPick = { onPick(it.id); open = false }, onDismiss = { open = false })
}
