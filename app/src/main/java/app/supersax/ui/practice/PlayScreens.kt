package app.supersax.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.common.DrillResultRecord
import app.supersax.domain.drills.DailyMix
import app.supersax.domain.drills.DrillRules
import app.supersax.domain.drills.FingeringDrill
import app.supersax.domain.drills.Levels
import app.supersax.domain.drills.ReadingDrill
import app.supersax.domain.drills.ScaleRecognition
import app.supersax.ui.drills.savePhoneDrill
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Questions of one level of a drill. */
fun levelItems(c: AppContainer, kind: DrillKind, level: Int, count: Int?, seed: Long, sax: Boolean = false): List<DrillItem> = when (kind) {
    DrillKind.EAR -> app.supersax.domain.drills.EarDrill.round(level, count ?: app.supersax.domain.drills.EarDrill.ROUND, seed).map { DrillItem.Ear(it, sax) }
    DrillKind.RHYTHM -> app.supersax.domain.drills.RhythmDrill.round(level, count ?: app.supersax.domain.drills.RhythmDrill.ROUND, seed).map { DrillItem.Rhythm(it, level) }
    DrillKind.READING -> ReadingDrill.questions(level, count ?: DrillRules.READING_ROUND, seed).map { DrillItem.Reading(it.pitch, it.fifths) }
    DrillKind.SCALE_RECOGNITION -> ScaleRecognition.round(level, count ?: DrillRules.SCALE_ROUND, seed).map { DrillItem.Scale(it, level) }
    DrillKind.FINGERING -> FingeringDrill.round(c.content.fingerings, level, count ?: DrillRules.FINGERING_ROUND, seed).map { DrillItem.Fingering(it.first, it.second) }
    else -> emptyList()
}

data class LevelOutcome(val stars: Int, val unlockedNext: Boolean, val nextOpen: Boolean)

class PlayLevelViewModel(c: AppContainer, val kind: DrillKind, val level: Int, val sax: Boolean = false) : RoundViewModel(c) {
    private val _outcome = MutableStateFlow<LevelOutcome?>(null)
    val outcome = _outcome.asStateFlow()
    val max = Levels.count(kind, 0)

    init { begin(levelItems(c, kind, level, null, c.clock.nowMs(), sax)) }

    override suspend fun onFinished(u: RoundUi) {
        val before = c.progress.drills.first()
        val openBefore = Levels.unlocked(before, kind, max)
        c.savePhoneDrill(kind, level, u.items.size, u.correct, u.meanMs, startedAt)
        val stars = Levels.stars(kind, u.correct, u.items.size, u.meanMs)
        val after = before + DrillResultRecord("new", c.clock.nowMs(), kind, level, u.items.size, u.correct, u.meanMs)
        val openAfter = Levels.unlocked(after, kind, max)
        _outcome.value = LevelOutcome(stars, openAfter > openBefore, level + 1 <= openAfter)
    }
}

@Composable
fun PlayLevelScreen(kind: DrillKind, level: Int, onClose: () -> Unit, onLevel: (Int) -> Unit, sax: Boolean = false) {
    val vm = appViewModel(key = "play-$kind-$level-$sax") { PlayLevelViewModel(it, kind, level, sax) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val outcome by vm.outcome.collectAsStateWithLifecycle()
    RoundScreen(vm, ui, onClose) {
        val o = outcome ?: return@RoundScreen
        LevelResult(
            "${drillTitle(kind)} · ${stringResource(R.string.level_n, level)}", o.stars, ui.correct, ui.items.size,
            o.unlockedNext, level >= vm.max, o.nextOpen, onNext = { onLevel(level + 1) }, onRetry = { onLevel(level) }, onLevels = onClose,
            detail = speedDetail(kind, ui.meanMs),
        )
    }
}

// --- Daily exercise -------------------------------------------------------------------------

/** Days (epoch days in the device zone) with a finished daily exercise. */
fun dailyDays(results: List<DrillResultRecord>, zone: ZoneId = ZoneId.systemDefault()): Set<Long> =
    results.filter { it.kind == DrillKind.DAILY }.map { Instant.ofEpochMilli(it.atMs).atZone(zone).toLocalDate().toEpochDay() }.toSet()

class DailyViewModel(c: AppContainer) : RoundViewModel(c) {
    private val _streak = MutableStateFlow<Int?>(null)
    val streak = _streak.asStateFlow()
    private val prefs = c.context.getSharedPreferences("daily", android.content.Context.MODE_PRIVATE)
    /** The length picked last time (offered first). */
    val lastMinutes: Int get() = prefs.getInt("minutes", 5)
    /** Minutes chosen for this session; null until the player picks. */
    private val _minutes = MutableStateFlow<Int?>(null)
    val minutes = _minutes.asStateFlow()

    fun start(minutes: Int) {
        if (_minutes.value != null) return
        _minutes.value = minutes
        prefs.edit().putInt("minutes", minutes).apply()
        viewModelScope.launch {
            val results = c.progress.drills.first()
            val seed = c.clock.nowMs()
            val lessons = c.content.lessons
            // Theory questions come from lessons already passed (at least 1 star).
            val theory = lessons.filter { Levels.best(results, DrillKind.THEORY_QUIZ, it.order) >= 1 }
                .flatMap { l -> l.quiz.map { DrillItem.Theory(l.id, it) } }.shuffled(Random(seed))
            val accuracy = DailyMix.KINDS.mapNotNull { k -> Levels.recentAccuracy(results, k, 5)?.let { k to it } }.toMap()
            val counts = DailyMix.counts(theory.isNotEmpty(), accuracy, minutes)
            val pools = counts.mapValues { (k, n) ->
                if (k == DrillKind.THEORY_QUIZ) theory.take(n)
                else levelItems(c, k, Levels.unlocked(results, k, Levels.count(k, 0)), n, seed + k.ordinal)
            }.mapValues { it.value.iterator() }
            val items = DailyMix.order(counts, seed).mapNotNull { k -> pools[k]?.takeIf { it.hasNext() }?.next() }
            begin(items)
        }
    }

    override suspend fun onFinished(u: RoundUi) {
        c.savePhoneDrill(DrillKind.DAILY, 0, u.items.size, u.correct, u.meanMs, startedAt)
        val today = Instant.ofEpochMilli(c.clock.nowMs()).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        _streak.value = DailyMix.streak(dailyDays(c.progress.drills.first()) + today, today)
    }
}

@Composable
fun DailyScreen(onClose: () -> Unit) {
    val vm = appViewModel(key = "daily") { DailyViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val minutes by vm.minutes.collectAsStateWithLifecycle()
    if (minutes == null) { DailyLengthPicker(vm.lastMinutes, onClose, vm::start); return }
    RoundScreen(vm, ui, onClose) {
        val s = streak ?: return@RoundScreen
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = Sax.colors.warn, modifier = Modifier.size(96.dp))
            Text(pluralStringResource(R.plurals.streak_days, s, s), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.drill_score, ui.correct, ui.items.size), style = MaterialTheme.typography.titleLarge, color = Sax.colors.textSecondary)
            Text(stringResource(R.string.daily_done_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                Text(stringResource(R.string.daily_finish), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "How much time do you have?" before the daily exercise: 5, 10 or 15 minutes, last choice highlighted. */
@Composable
private fun DailyLengthPicker(last: Int, onClose: () -> Unit, onPick: (Int) -> Unit) {
    app.supersax.ui.components.ScreenScaffold(stringResource(R.string.daily_title), onBack = onClose) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = Sax.colors.warn, modifier = Modifier.size(72.dp))
            Text(stringResource(R.string.daily_how_long), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            DailyMix.MINUTES.forEach { m ->
                val label = stringResource(R.string.daily_minutes, m)
                if (m == last) Button(onClick = { onPick(m) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                    Text(label, style = MaterialTheme.typography.titleLarge)
                }
                else androidx.compose.material3.OutlinedButton(onClick = { onPick(m) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text(label, style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(stringResource(R.string.daily_length_hint), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** "Your average … · 2 stars … · 3 stars …" for drills where speed counts. */
@Composable
fun speedDetail(kind: DrillKind, meanMs: Long): String? {
    val lim = Levels.speedLimits(kind) ?: return null
    return if (kind == DrillKind.RHYTHM) stringResource(R.string.level_detail_rhythm, meanMs, lim[0], lim[1])
    else stringResource(R.string.level_detail_speed, meanMs / 1000.0, lim[0] / 1000.0, lim[1] / 1000.0)
}
