package app.supersax.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.supersax.ui.components.NavRow
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.theme.CardRadius
import app.supersax.ui.theme.Sax

/** One entry of a hub screen. [tint] colours its icon tile (amber when null). */
data class HubEntry(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color? = null, val onClick: () -> Unit)

/** A titled group of entries; [hint] is a short line under the title. */
data class HubSection(val title: String?, val entries: List<HubEntry>, val hint: String? = null)

/** Grouped lists of large entries (Training, Progress), one card per section. */
@Composable
fun HubScreen(title: String, sections: List<HubSection>, actions: @Composable () -> Unit = {}) {
    ScreenScaffold(title, actions = actions) { padding ->
        ScrollColumn(padding) {
            sections.filter { it.entries.isNotEmpty() }.forEach { s ->
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (s.title != null) Text(s.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                    if (s.hint != null) Text(s.hint, style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        s.entries.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 58.dp), color = Sax.colors.divider)
                            if (e.tint != null) NavRow(e.title, e.subtitle, e.icon, e.onClick, tint = e.tint, background = e.tint.copy(alpha = 0.14f))
                            else NavRow(e.title, e.subtitle, e.icon, e.onClick)
                        }
                    }
                }
            }
        }
    }
}
