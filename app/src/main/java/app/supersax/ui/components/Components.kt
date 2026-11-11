package app.supersax.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.supersax.R

/** Standard screen frame with a title, an optional back arrow and actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = { actions() },
            )
        },
        content = content,
    )
}

/** A scrolling column with standard padding. */
@Composable
fun ScrollColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Large primary button (at least 64 dp tall), for the main action of a screen. */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

/** A calm empty state. */
@Composable
fun EmptyState(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** A visible, recoverable error message with an optional retry action. */
@Composable
fun ErrorBanner(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** A labelled numeric stepper with large touch targets. */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    format: @Composable (Int) -> String = { it.toString() },
) {
    val shown = format(value)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onChange((value - step).coerceIn(range)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease, label))
        }
        Box(Modifier.widthIn(min = 72.dp).semantics { contentDescription = "$label $shown" }, contentAlignment = Alignment.Center) {
            Text(shown, style = MaterialTheme.typography.titleLarge)
        }
        FilledTonalIconButton(onClick = { onChange((value + step).coerceIn(range)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase, label))
        }
    }
}

/** Formats seconds as m:ss. */
fun formatClock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
