package app.supersax.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.AppContainer
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.data.db.KeyValueEntity
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.DrillLesson
import app.supersax.domain.library.Score
import app.supersax.notation.StaffView
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val SEEN_KEY = "drill_lessons:seen"

/** Lessons already read, as "KIND:level". */
fun seenLessons(c: AppContainer): Flow<Set<String>> =
    c.db.kv().observe(SEEN_KEY).map { e -> e?.json?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet() }

suspend fun markLessonSeen(c: AppContainer, kind: DrillKind, level: Int) {
    val now = c.db.kv().get(SEEN_KEY)?.json?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    c.db.kv().put(KeyValueEntity(SEEN_KEY, (now + "${kind.name}:$level").joinToString(",")))
}

fun drillLesson(c: AppContainer, kind: DrillKind, level: Int): DrillLesson? = c.content.drillLessons[kind.name]?.getOrNull(level - 1)

/** The lesson of a level: what the level is about, an example on the staff, a tip; then start the level. */
@Composable
fun DrillLessonScreen(kind: DrillKind, level: Int, onBack: () -> Unit, onStart: () -> Unit) {
    val c = LocalContainer.current
    val lesson = remember(kind, level) { drillLesson(c, kind, level) }
    val language = LocaleHelper.effective(LocalContext.current)
    val naming = LocalSettings.current.noteNaming
    fun start() { c.appScope.launch { markLessonSeen(c, kind, level) }; onStart() }
    ScreenScaffold("${drillTitle(kind)} · ${stringResource(R.string.level_n, level)}", onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (lesson == null) { start(); return@ScrollColumn }
            app.supersax.ui.components.Overline(stringResource(R.string.drill_lesson))
            Text(DrillLesson.notes(lesson.title.get(language), naming), style = MaterialTheme.typography.headlineSmall)
            SectionCard {
                lesson.points.forEach { p ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("•", style = MaterialTheme.typography.bodyLarge, color = Sax.colors.accentInk, modifier = Modifier.padding(end = 10.dp))
                        Text(DrillLesson.notes(p.get(language), naming), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            lesson.example?.let { bars ->
                SectionCard(stringResource(R.string.drill_lesson_example)) {
                    StaffView(Score(lesson.fifths, 4, 4, 80, bars.map { Score.parseMeasure(it) }), staffSpace = 10.dp, showTime = kind == DrillKind.RHYTHM)
                }
            }
            lesson.tip?.let { tip ->
                SectionCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = Sax.colors.accent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(DrillLesson.notes(tip.get(language), naming), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(
                onClick = { start() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent),
            ) { Text(stringResource(R.string.drill_lesson_start, level), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun <T> remember(a: Any, b: Any, calc: () -> T): T = androidx.compose.runtime.remember(a, b) { calc() }
