package app.supersax.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.stats.Badges
import app.supersax.domain.stats.Stats
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeMap

data class StatsUi(
    val heatmap: List<List<Stats.Day>>,
    val weeks: List<IntArray>,
    val streak: Int,
    val bestStreak: Int,
    val totalMinutes: Int,
    val tempo: Map<String, TreeMap<LocalDate, Int>>,
    val inTune: List<Double?>,
    val badges: List<Badges.Badge>,
)

class StatsViewModel(c: AppContainer) : ViewModel() {
    val ui = combine(c.practice.sessions, c.progress.drills, c.progress.holds, c.media.recordings, c.practice.player) { s, d, h, r, p ->
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(c.clock.nowMs()).atZone(zone).toLocalDate()
        val daily = Stats.dailyMinutes(s, zone)
        val st = Stats.streaks(Stats.saxDays(s, zone), today)
        StatsUi(
            Stats.heatmap(daily, today, 17), Stats.weeklyMinutes(daily, today, 8), st[0], st[1], Stats.totalSaxMinutes(s),
            Stats.cleanTempo(s, zone), Stats.weeklyInTune(h, zone, today, 8),
            Badges.compute(s, d, h, r, p, zone, today, c.content.lessons.size),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val vm = appViewModel(key = "stats") { StatsViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.stats_title), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            val u = ui ?: return@ScrollColumn
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(pluralStringResource(R.plurals.stats_days, u.streak, u.streak), stringResource(R.string.stats_streak), Modifier.weight(1f))
                Tile(pluralStringResource(R.plurals.stats_days, u.bestStreak, u.bestStreak), stringResource(R.string.stats_best_streak), Modifier.weight(1f))
                Tile(stringResource(R.string.stats_hours, u.totalMinutes / 60, u.totalMinutes % 60), stringResource(R.string.stats_total), Modifier.weight(1f))
            }
            SectionCard(stringResource(R.string.stats_calendar)) { Heatmap(u.heatmap) }
            SectionCard(stringResource(R.string.stats_weekly)) { WeekBars(u.weeks) }
            SectionCard(stringResource(R.string.stats_tempo)) { TempoCard(u.tempo) }
            SectionCard(stringResource(R.string.stats_in_tune)) { InTuneLine(u.inTune) }
            BadgesSection(u.badges)
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Sax.colors.accent, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
    }
}

/** GitHub-style calendar: one column per week, Monday at the top, colour by minutes. */
@Composable
private fun Heatmap(weeks: List<List<Stats.Day>>) {
    val accent = Sax.colors.accent
    val empty = Sax.colors.elevated
    val days = weeks.flatten().count { it.minutes > 0 }
    val desc = pluralStringResource(R.plurals.stats_days_played, days, days)
    Canvas(Modifier.fillMaxWidth().aspectRatio(weeks.size / 7f).semantics { contentDescription = desc }) {
        val cell = size.width / weeks.size
        val gap = cell * 0.14f
        weeks.forEachIndexed { w, col ->
            col.forEachIndexed { d, day ->
                if (day.minutes < 0) return@forEachIndexed
                val color = when {
                    day.minutes == 0 -> empty
                    day.minutes < 10 -> accent.copy(alpha = 0.3f)
                    day.minutes < 20 -> accent.copy(alpha = 0.55f)
                    day.minutes < 40 -> accent.copy(alpha = 0.8f)
                    else -> accent
                }
                drawRoundRect(color, Offset(w * cell + gap / 2, d * cell + gap / 2), Size(cell - gap, cell - gap),
                    androidx.compose.ui.geometry.CornerRadius(cell * 0.2f))
            }
        }
    }
    Text(desc, style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
}

/** Minutes per week: sax (amber) stacked under phone (blue). */
@Composable
private fun WeekBars(weeks: List<IntArray>) {
    val accent = Sax.colors.accent
    val phone = app.supersax.ui.theme.PhoneBlue
    val max = (weeks.maxOfOrNull { it[0] + it[1] } ?: 0).coerceAtLeast(30)
    val last = weeks.last()
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / weeks.size
        weeks.forEachIndexed { i, w ->
            val x = i * slot + slot * 0.2f
            val bw = slot * 0.6f
            val hSax = size.height * w[0] / max
            val hPhone = size.height * w[1] / max
            drawRect(accent, Offset(x, size.height - hSax), Size(bw, hSax))
            drawRect(phone, Offset(x, size.height - hSax - hPhone), Size(bw, hPhone))
        }
    }
    Text(stringResource(R.string.stats_this_week, last[0], last[1]), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
}

/** Best clean tempo per day for one scale, as a line. */
@Composable
private fun TempoCard(tempo: Map<String, TreeMap<LocalDate, Int>>) {
    if (tempo.isEmpty()) { Text(stringResource(R.string.stats_tempo_empty), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary); return }
    val keys = tempo.entries.sortedByDescending { it.value.lastKey() }.map { it.key }
    var selected by rememberSaveable { mutableStateOf(keys.first()) }
    if (selected !in tempo) selected = keys.first()
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(keys.size) { i -> FilterChip(selected = keys[i] == selected, onClick = { selected = keys[i] }, label = { Text(scaleLabel(keys[i])) }) }
    }
    val points = tempo[selected]!!.values.toList()
    LineChart(points.map { it.toDouble() }, Modifier.fillMaxWidth().height(110.dp))
    Text(stringResource(R.string.stats_tempo_range, points.first(), points.last(), points.max()), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
}

@Composable
private fun InTuneLine(weeks: List<Double?>) {
    val known = weeks.filterNotNull()
    if (known.isEmpty()) { Text(stringResource(R.string.stats_in_tune_empty), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary); return }
    LineChart(weeks.map { it?.times(100) }, Modifier.fillMaxWidth().height(90.dp), min = 0.0, max = 100.0)
    Text(stringResource(R.string.stats_in_tune_last, (known.last() * 100).toInt()), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
}

@Composable
private fun LineChart(values: List<Double?>, modifier: Modifier, min: Double? = null, max: Double? = null) {
    val accent = Sax.colors.accent
    val grid = Sax.colors.elevated
    val known = values.filterNotNull()
    val lo = min ?: (known.min() - 5)
    val hi = (max ?: (known.max() + 5)).coerceAtLeast(lo + 1)
    Canvas(modifier) {
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        val step = if (values.size > 1) size.width / (values.size - 1) else 0f
        val pts = values.mapIndexedNotNull { i, v -> v?.let { Offset(if (values.size > 1) i * step else size.width / 2, (size.height * (1 - (it - lo) / (hi - lo))).toFloat()) } }
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, accent, style = Stroke(5f))
        pts.forEach { drawCircle(accent, 7f, it) }
    }
}

@Composable
private fun BadgesSection(badges: List<Badges.Badge>) {
    val earned = badges.count { it.earned }
    SectionCard(stringResource(R.string.badges_title, earned, badges.size)) {
        val ordered = badges.sortedWith(compareByDescending<Badges.Badge> { it.earned }.thenByDescending { it.progress })
        ordered.forEach { BadgeRow(it) }
    }
}

@Composable
private fun BadgeRow(b: Badges.Badge) {
    val (title, body) = badgeText(b.id)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (b.earned) Sax.colors.accent else Sax.colors.elevated), contentAlignment = Alignment.Center) {
            Icon(if (b.earned) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, null,
                tint = if (b.earned) Sax.colors.onAccent else Sax.colors.textTertiary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (b.earned) Sax.colors.text else Sax.colors.textSecondary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            if (!b.earned && b.target > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { b.progress }, modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                    color = Sax.colors.accent, trackColor = Sax.colors.elevated, drawStopIndicator = {})
                Spacer(Modifier.width(8.dp))
                Text("${b.current}/${b.target}", style = MaterialTheme.typography.labelSmall, color = Sax.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun badgeText(id: Badges.Id): Pair<String, String> = when (id) {
    Badges.Id.FIRST_SESSION -> R.string.bd_first_session to R.string.bd_first_session_d
    Badges.Id.SESSIONS_10 -> R.string.bd_sessions_10 to R.string.bd_sessions_10_d
    Badges.Id.SESSIONS_50 -> R.string.bd_sessions_50 to R.string.bd_sessions_50_d
    Badges.Id.SESSIONS_100 -> R.string.bd_sessions_100 to R.string.bd_sessions_100_d
    Badges.Id.STREAK_3 -> R.string.bd_streak_3 to R.string.bd_streak_3_d
    Badges.Id.STREAK_7 -> R.string.bd_streak_7 to R.string.bd_streak_7_d
    Badges.Id.STREAK_30 -> R.string.bd_streak_30 to R.string.bd_streak_30_d
    Badges.Id.HOURS_5 -> R.string.bd_hours_5 to R.string.bd_hours_5_d
    Badges.Id.HOURS_20 -> R.string.bd_hours_20 to R.string.bd_hours_20_d
    Badges.Id.SCALES_5 -> R.string.bd_scales_5 to R.string.bd_scales_5_d
    Badges.Id.MAJOR_12 -> R.string.bd_major_12 to R.string.bd_major_12_d
    Badges.Id.BPM_100 -> R.string.bd_bpm_100 to R.string.bd_bpm_100_d
    Badges.Id.HOLD_20 -> R.string.bd_hold_20 to R.string.bd_hold_20_d
    Badges.Id.IN_TUNE_90 -> R.string.bd_in_tune to R.string.bd_in_tune_d
    Badges.Id.THREE_STARS -> R.string.bd_three_stars to R.string.bd_three_stars_d
    Badges.Id.MASTER_DRILL -> R.string.bd_master to R.string.bd_master_d
    Badges.Id.DAILY_7 -> R.string.bd_daily_7 to R.string.bd_daily_7_d
    Badges.Id.SPEED_30 -> R.string.bd_speed_30 to R.string.bd_speed_30_d
    Badges.Id.PERFECT_PLAY_ALONG -> R.string.bd_play_along to R.string.bd_play_along_d
    Badges.Id.FIRST_TAKE -> R.string.bd_first_take to R.string.bd_first_take_d
}.let { stringResource(it.first) to stringResource(it.second) }
