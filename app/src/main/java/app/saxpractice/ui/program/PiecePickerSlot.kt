package app.saxpractice.ui.program

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.saxpractice.R

/** Piece target of a new-material or play-for-fun exercise (library picker added with the library). */
@Composable
fun PiecePickerSlot(pieceId: String?, onPick: (String?) -> Unit) {
    Text(stringResource(R.string.piece_auto), style = MaterialTheme.typography.bodySmall)
}
