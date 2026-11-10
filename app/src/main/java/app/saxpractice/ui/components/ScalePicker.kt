package app.saxpractice.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saxpractice.R
import app.saxpractice.domain.scale.ScaleId
import app.saxpractice.domain.scale.ScaleType

/** Picks a scale (any type, 12 keys each), shown with written names; returns its concert key. */
@Composable
fun ScalePickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit, types: List<ScaleType> = ScaleType.entries) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.pick_scale)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                types.forEach { type ->
                    item { Text(scaleTypeLabel(type), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
                    items(ScaleId.all(type).sortedBy { it.writtenKeySignature().fifths }) { id ->
                        Text(
                            scaleLabel(id.key),
                            Modifier.fillMaxWidth().clickable { onPick(id.key) }.heightIn(min = 48.dp).padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
    )
}
