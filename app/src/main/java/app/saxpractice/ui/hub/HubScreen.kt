package app.saxpractice.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard

/** One entry of a hub screen. */
data class HubEntry(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)

/** A simple list of large entries (Tools, Progress). */
@Composable
fun HubScreen(title: String, entries: List<HubEntry>) {
    ScreenScaffold(title) { padding ->
        ScrollColumn(padding) {
            entries.forEach { e ->
                SectionCard(modifier = Modifier.clickable(onClick = e.onClick)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(e.icon, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium)
                            Text(e.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
