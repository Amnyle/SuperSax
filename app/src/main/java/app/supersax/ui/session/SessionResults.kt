package app.supersax.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.supersax.R
import app.supersax.domain.program.Pillar
import app.supersax.domain.rank.RankResult
import app.supersax.ui.components.BigButton
import app.supersax.ui.components.pillarLabel
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.rank.rankName
import app.supersax.ui.rank.tierName
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** What changed with this session: rank and pillars before/after, scale records, next tempos, new scales. */
data class SessionResult(
    val rankBefore: RankResult?,
    val rankAfter: RankResult?,
    /** Fastest clean run of each scale played (scale → BPM), and the record before the session (null = none). */
    val records: List<Triple<String, Int, Int?>>,
    /** Scales whose tempo goes up next time (scale, from, to). */
    val tempoUps: List<Triple<String, Int, Int>>,
    val unlocked: List<String>,
    val cleanRuns: Int,
    val runs: Int,
) {
    val rankUp: Boolean get() {
        val b = rankBefore ?: return false
        val a = rankAfter ?: return false
        return a.overallTier > b.overallTier || (a.overallTier == b.overallTier && a.division > b.division)
    }
    /** Pillars whose tier rose (pillar, from, to). */
    val pillarUps: List<Triple<Pillar, Int, Int>> get() {
        val b = rankBefore ?: return emptyList()
        val a = rankAfter ?: return emptyList()
        return Pillar.values().mapNotNull { p ->
            val from = b.pillars[p]?.tier ?: return@mapNotNull null
            val to = a.pillars[p]?.tier ?: return@mapNotNull null
            if (to > from) Triple(p, from, to) else null
        }
    }
    val newRecords: List<Triple<String, Int, Int?>> get() = records.filter { (_, bpm, before) -> before == null || bpm > before }
    val celebrate: Boolean get() = rankUp || pillarUps.isNotEmpty() || newRecords.isNotEmpty() || unlocked.isNotEmpty()
}

private data class ResultLine(val icon: ImageVector, val text: String, val strong: Boolean = false)

/** The results after saving a session: a medal that pops in, confetti for a rank up or a record, then each change in turn. */
@Composable
fun SessionResultsView(result: SessionResult, onDone: () -> Unit) {
    val lines = buildList {
        if (result.rankUp) add(ResultLine(Icons.Rounded.WorkspacePremium,
            stringResource(R.string.res_rank_up, rankName(result.rankBefore!!.overallTier, result.rankBefore.division),
                rankName(result.rankAfter!!.overallTier, result.rankAfter.division)), strong = true))
        result.pillarUps.forEach { (p, from, to) ->
            add(ResultLine(Icons.Rounded.TrendingUp, stringResource(R.string.res_pillar_up, pillarLabel(p), tierName(from), tierName(to)), strong = true))
        }
        result.newRecords.forEach { (key, bpm, before) ->
            add(ResultLine(Icons.Rounded.Speed,
                if (before == null) stringResource(R.string.res_first_clean, scaleLabel(key), bpm)
                else stringResource(R.string.res_record, scaleLabel(key), bpm, before), strong = true))
        }
        result.tempoUps.forEach { (key, from, to) -> add(ResultLine(Icons.Rounded.TrendingUp, stringResource(R.string.res_tempo_up, scaleLabel(key), from, to))) }
        result.unlocked.forEach { add(ResultLine(Icons.Rounded.LockOpen, stringResource(R.string.res_unlocked, scaleLabel(it)), strong = true)) }
        result.records.filter { it !in result.newRecords }.forEach { (key, bpm, _) ->
            add(ResultLine(Icons.Rounded.Speed, stringResource(R.string.res_clean_at, scaleLabel(key), bpm)))
        }
        if (result.runs > 0) add(ResultLine(Icons.Rounded.EmojiEvents, stringResource(R.string.res_runs, result.cleanRuns, result.runs)))
    }
    val medal = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        medal.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        for (i in lines.indices) { delay(350); shown = i + 1 }
    }
    val accent = if (result.rankUp) Sax.colors.accent else if (result.celebrate) Sax.colors.good else Sax.colors.textTertiary
    Box(Modifier.fillMaxSize()) {
        if (result.celebrate) Confetti(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(132.dp).scale(medal.value).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (result.rankUp) Icons.Rounded.WorkspacePremium else Icons.Rounded.EmojiEvents, null, tint = accent, modifier = Modifier.size(84.dp))
            }
            Text(
                stringResource(when {
                    result.rankUp -> R.string.res_title_rank_up
                    result.celebrate -> R.string.res_title_progress
                    else -> R.string.res_title_saved
                }),
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            )
            result.rankAfter?.let { Text(rankName(it.overallTier, it.division), style = MaterialTheme.typography.titleMedium, color = Sax.colors.textSecondary) }
            lines.forEachIndexed { i, line ->
                AnimatedVisibility(i < shown, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(line.icon, null, tint = if (line.strong) accent else Sax.colors.textSecondary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(line.text, style = if (line.strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (line.strong) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            if (lines.isEmpty()) Text(stringResource(R.string.res_nothing_new), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            BigButton(stringResource(R.string.res_done), onDone)
        }
    }
}

/** Falling confetti for a few seconds. */
@Composable
private fun Confetti(modifier: Modifier) {
    val colors = listOf(Sax.colors.accent, Sax.colors.good, Color(0xFF4F8DF7), Color(0xFFE5677F), Color(0xFFB07CE8))
    val pieces = remember { List(70) { i -> floatArrayOf((i * 0.618f) % 1f, (i * 0.37f) % 1f, 0.5f + (i % 7) / 7f, (i % 5).toFloat(), i * 0.9f) } }
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) { delay(3500); fade.animateTo(0f, tween(1500)) }
    if (fade.value <= 0f) return
    Canvas(modifier) {
        pieces.forEach { p ->
            val fall = ((p[1] + t * p[2]) % 1f)
            val x = (p[0] + 0.05f * sin((t * 6.28f * 2) + p[4])) * size.width
            val y = fall * size.height
            val c = colors[p[3].toInt() % colors.size].copy(alpha = fade.value)
            val w = 14f; val h = 7f
            val a = t * 12f + p[4]
            val dx = cos(a) * w / 2; val dy = sin(a) * h
            drawLine(c, Offset(x - dx, y - dy), Offset(x + dx, y + dy), strokeWidth = 8f)
        }
    }
}
