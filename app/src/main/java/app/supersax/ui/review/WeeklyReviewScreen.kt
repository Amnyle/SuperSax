package app.supersax.ui.review

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.goals.GoalEvaluator
import app.supersax.domain.review.WeeklyReviewBuilder
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.supersax.domain.review.ProgramChange
import app.supersax.domain.review.WeeklyCoach
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.ZoneId

/** Week (epoch day of its Monday) whose review was last opened, so the home card shows once a week. */
fun reviewSeenWeek(context: android.content.Context): Long =
    context.getSharedPreferences("review", android.content.Context.MODE_PRIVATE).getLong("seen_week", -1)

class WeeklyReviewViewModel(private val c: AppContainer) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    /** Early in the week (Monday to Wednesday) the review opens on the week just finished. */
    val weekOffset = MutableStateFlow(if (LocalDate.now(zone).dayOfWeek.value <= 3) 1 else 0)

    init {
        c.context.getSharedPreferences("review", android.content.Context.MODE_PRIVATE).edit()
            .putLong("seen_week", GoalEvaluator.weekStart(LocalDate.now(zone)).toEpochDay()).apply()
    }

    val review = combine(weekOffset, c.practice.activeProgram, c.practice.sessions, c.progress.holds, c.progress.snapshots) { off, p, s, h, snaps ->
        val today = LocalDate.now(zone)
        val start = GoalEvaluator.weekStart(today).minusWeeks(off.toLong())
        WeeklyReviewBuilder.build(start, today, zone, p, s, h, snaps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What improved (against the week before) and what to aim at next. */
    val coach = combine(weekOffset, c.practice.activeProgram, c.practice.sessions, c.progress.holds, c.progress.snapshots) { off, p, s, h, snaps ->
        val today = LocalDate.now(zone)
        val start = GoalEvaluator.weekStart(today).minusWeeks(off.toLong())
        val week = WeeklyReviewBuilder.build(start, today, zone, p, s, h, snaps)
        val prev = WeeklyReviewBuilder.build(start.minusWeeks(1), today, zone, p, s, h, snaps)
        val settings = c.settings.current()
        val player = c.practice.playerNow()
        val limiter = runCatching { c.currentLimiter() }.getOrNull()
        val instrument = settings.instrument
        val plan = h.filter { kotlin.math.abs(it.meanCents) <= 50 && it.stableSec >= 1 }
            .map { app.supersax.domain.longtone.LongTonePlan.Hold(instrument.concertToWrittenMidi(it.refMidi), it.atMs, it.stableSec, it.inTuneShare) }
        val weak = plan.map { it.writtenMidi }.distinct().map { it to app.supersax.domain.longtone.LongTonePlan.struggle(it, plan) }
            .filter { it.second >= 0.35 }.sortedByDescending { it.second }.map { it.first }
        WeeklyCoach.improvements(week, prev) to WeeklyCoach.next(week, player, limiter, c.content.generatorConfig.adaptation.unlockOrder,
            settings.scaleBpmStep, weak)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A fresh version of the active program for the player as they are now, and what it changes; null when none is proposed. */
    private val _update = MutableStateFlow<Pair<app.supersax.domain.program.Program, List<ProgramChange>>?>(null)
    val update = _update.asStateFlow()
    private val _updated = MutableStateFlow(false)
    val updated = _updated.asStateFlow()

    fun proposeUpdate() = viewModelScope.launch {
        val p = c.practice.activeProgram.first() ?: return@launch
        val player = c.practice.playerNow()
        val days = p.days.map { it.dayOfWeek }.toSet().ifEmpty { setOf(1) }
        val minutes = (p.days.sumOf { it.minutes }.toDouble() / maxOf(1, p.days.size)).roundToInt().coerceAtLeast(10)
        val fresh = app.supersax.domain.generator.Generator.generate(
            app.supersax.domain.generator.Generator.Input(p.id, p.name, days, minutes, player.enduranceCapSec, player.focus,
                runCatching { c.currentLimiter() }.getOrNull(), c.availablePhoneDrills, c.settings.current().restSec, c.clock.nowMs()),
            c.content.generatorConfig,
        ).copy(id = p.id, name = p.name)
        _update.value = fresh to WeeklyCoach.changes(p, fresh)
    }

    fun applyUpdate() = viewModelScope.launch {
        val (p, _) = _update.value ?: return@launch
        c.practice.saveProgram(p, makeActive = true, nowMs = c.clock.nowMs())
        _update.value = null
        _updated.value = true
    }

    fun cancelUpdate() { _update.value = null }
}

/** Weekly review: plain facts (planned vs actual), copy or share as text. */
@Composable
fun WeeklyReviewScreen(onBack: () -> Unit) {
    val vm = appViewModel { WeeklyReviewViewModel(it) }
    val review by vm.review.collectAsStateWithLifecycle()
    val offset by vm.weekOffset.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val naming = app.supersax.ui.nav.LocalSettings.current.noteNaming
    var copied by remember { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    ScreenScaffold(stringResource(R.string.weekly_review), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.weekOffset.value = offset + 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.previous_week)) }
                if (offset > 0) TextButton(onClick = { vm.weekOffset.value = offset - 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.next_week)) }
            }
            val r = review ?: return@ScrollColumn
            val text = WeeklyReviewBuilder.text(r, LocaleHelper.effective(context), naming)
            val coach by vm.coach.collectAsStateWithLifecycle()
            val updated by vm.updated.collectAsStateWithLifecycle()
            coach?.let { (better, next) -> CoachCards(better, next) }
            SectionCard(stringResource(R.string.wr_program)) {
                Text(stringResource(if (updated) R.string.wr_program_done else R.string.wr_program_hint), style = MaterialTheme.typography.bodyMedium)
                if (!updated) Button(onClick = { vm.proposeUpdate() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.wr_update_program)) }
            }
            vm.update.collectAsStateWithLifecycle().value?.let { (fresh, changes) -> UpdateDialog(fresh, changes, vm::applyUpdate, vm::cancelUpdate) }
            SectionCard(stringResource(R.string.wr_facts)) {
                text.lines().forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(appName, text))
                    copied = true
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.copy_text)) }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.share)) }
            }
            if (copied) Text(stringResource(R.string.copied))
            Text(stringResource(R.string.review_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "What you improved" and "Next week" cards. */
@androidx.compose.runtime.Composable
private fun CoachCards(better: List<app.supersax.domain.review.Improvement>, next: List<app.supersax.domain.review.NextStep>) {
    val instrument = app.supersax.ui.nav.LocalSettings.current.instrument
    SectionCard(stringResource(R.string.wr_improved)) {
        if (better.isEmpty()) Text(stringResource(R.string.wr_improved_none), style = MaterialTheme.typography.bodyMedium)
        better.forEach { i ->
            val line = when (i) {
                is app.supersax.domain.review.Improvement.RankUp ->
                    stringResource(R.string.wr_rank_up, app.supersax.ui.rank.rankName(i.tierBefore, i.divisionBefore), app.supersax.ui.rank.rankName(i.tier, i.division))
                is app.supersax.domain.review.Improvement.ScaleFaster -> stringResource(R.string.wr_scale_faster, app.supersax.ui.components.scaleLabel(i.scaleKey), i.before, i.now)
                is app.supersax.domain.review.Improvement.FirstClean -> stringResource(R.string.wr_first_clean, app.supersax.ui.components.scaleLabel(i.scaleKey), i.bpm)
                is app.supersax.domain.review.Improvement.InTune -> stringResource(R.string.wr_in_tune, i.before, i.now)
                is app.supersax.domain.review.Improvement.MoreMinutes -> stringResource(R.string.wr_more_minutes, i.before, i.now)
                is app.supersax.domain.review.Improvement.AllSessions -> stringResource(R.string.wr_all_sessions, i.done)
            }
            Text("✓  $line", style = MaterialTheme.typography.bodyLarge)
        }
    }
    SectionCard(stringResource(R.string.wr_next)) {
        next.forEach { n ->
            val line = when (n) {
                is app.supersax.domain.review.NextStep.Weakest -> stringResource(R.string.wr_weakest, app.supersax.ui.components.pillarLabel(n.pillar))
                is app.supersax.domain.review.NextStep.PushScale -> stringResource(R.string.wr_push_scale, app.supersax.ui.components.scaleLabel(n.scaleKey), n.bpm, n.target)
                is app.supersax.domain.review.NextStep.StillScale -> stringResource(R.string.wr_still_scale, app.supersax.ui.components.scaleLabel(n.scaleKey))
                is app.supersax.domain.review.NextStep.Unlock -> stringResource(R.string.wr_unlock, app.supersax.ui.components.scaleLabel(n.scaleKey))
                is app.supersax.domain.review.NextStep.LongTones ->
                    stringResource(R.string.wr_long_tones, n.writtenMidis.map { app.supersax.ui.components.writtenNoteLabel(instrument.writtenToConcertMidi(it)) }.joinToString(", "))
                is app.supersax.domain.review.NextStep.MissedDays -> stringResource(R.string.wr_missed, n.count)
            }
            Text("→  $line", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Shows what the program update changes (weekly minutes per block) before saving it. */
@androidx.compose.runtime.Composable
private fun UpdateDialog(fresh: app.supersax.domain.program.Program, changes: List<ProgramChange>, onApply: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.wr_update_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                if (changes.isEmpty()) Text(stringResource(R.string.wr_update_same))
                changes.forEach { ch ->
                    Text(stringResource(R.string.wr_update_line, app.supersax.ui.components.blockKindLabel(ch.kind), ch.before, ch.after))
                }
                Text(stringResource(R.string.wr_update_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = onApply) { Text(stringResource(R.string.wr_update_apply)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
