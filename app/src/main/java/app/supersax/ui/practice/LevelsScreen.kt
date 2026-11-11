package app.supersax.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.Levels
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.CardRadius
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class LevelsViewModel(c: AppContainer) : ViewModel() {
    val results = c.progress.drills.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun drillTitle(kind: DrillKind): String = stringResource(
    when (kind) {
        DrillKind.READING -> R.string.ex_reading
        DrillKind.SCALE_RECOGNITION -> R.string.ex_scale_recognition
        DrillKind.FINGERING -> R.string.ex_fingering
        else -> R.string.lessons
    },
)

/** What a level asks, in a few words. */
@Composable
fun levelDescription(kind: DrillKind, level: Int): String {
    val ids = when (kind) {
        DrillKind.READING -> listOf(R.string.lv_read_1, R.string.lv_read_2, R.string.lv_read_3, R.string.lv_read_4, R.string.lv_read_5)
        DrillKind.SCALE_RECOGNITION -> listOf(R.string.lv_scale_1, R.string.lv_scale_2, R.string.lv_scale_3, R.string.lv_scale_4,
            R.string.lv_scale_5, R.string.lv_scale_6, R.string.lv_scale_7, R.string.lv_scale_8)
        DrillKind.FINGERING -> listOf(R.string.lv_fing_1, R.string.lv_fing_2, R.string.lv_fing_3, R.string.lv_fing_4, R.string.lv_fing_5)
        else -> emptyList()
    }
    return ids.getOrNull(level - 1)?.let { stringResource(it) } ?: ""
}

/** The ladder of a drill: each level with its stars; locked levels open at 2 stars on the one before. */
@Composable
fun LevelsScreen(
    kind: DrillKind, onBack: () -> Unit, onPlay: (Int) -> Unit, actions: @Composable () -> Unit = {},
    extra: @Composable (results: List<app.supersax.domain.common.DrillResultRecord>, openLevel: Int) -> Unit = { _, _ -> },
) {
    val vm = appViewModel(key = "levels-$kind") { LevelsViewModel(it) }
    val results by vm.results.collectAsStateWithLifecycle()
    val max = Levels.count(kind, 0)
    ScreenScaffold(drillTitle(kind), onBack = onBack, actions = actions) { padding ->
        ScrollColumn(padding) {
            val r = results ?: return@ScrollColumn
            val open = Levels.unlocked(r, kind, max)
            val total = Levels.totalStars(r, kind, max)
            extra(r, open)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Rounded.Star, null, tint = Sax.colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stars_total, total, max * 3), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.levels_hint), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp))
            for (level in 1..max) LevelRow(level, levelDescription(kind, level), Levels.best(r, kind, level), level <= open) { onPlay(level) }
        }
    }
}

@Composable
fun LevelRow(level: Int, description: String, stars: Int, open: Boolean, title: String? = null, onClick: () -> Unit) {
    val name = title ?: stringResource(R.string.level_n, level)
    val lockedText = stringResource(R.string.level_locked)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(MaterialTheme.colorScheme.surface)
            .then(if (open) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { if (!open) contentDescription = "$name, $lockedText" }
            .heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(
                when {
                    !open -> Sax.colors.elevated
                    stars >= 3 -> Sax.colors.accent
                    else -> Sax.colors.accentSoft
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (open) Text("$level", style = MaterialTheme.typography.titleLarge, color = if (stars >= 3) Sax.colors.onAccent else Sax.colors.accentInk)
            else Icon(Icons.Rounded.Lock, null, tint = Sax.colors.textTertiary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = if (open) Sax.colors.text else Sax.colors.textTertiary)
            if (description.isNotEmpty()) Text(description, style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        }
        if (open) StarRow(stars, 20)
    }
}

/** "Chart" action for the fingering ladder. */
@Composable
fun ChartAction(onChart: () -> Unit) {
    TextButton(onClick = onChart) { Text(stringResource(R.string.chart)) }
}
