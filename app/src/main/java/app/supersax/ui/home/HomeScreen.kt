package app.supersax.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.LocaleHelper
import app.supersax.R
import app.supersax.domain.planner.StepKind
import app.supersax.domain.program.ExerciseType
import app.supersax.ui.components.EmptyState
import app.supersax.ui.components.IconTile
import app.supersax.ui.components.NavRow
import app.supersax.ui.components.Overline
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.blockKindLabel
import app.supersax.ui.components.dayLabel
import app.supersax.ui.components.exerciseTypeLabel
import app.supersax.ui.components.formatClock
import app.supersax.ui.components.pillarLabel
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.rank.rankName
import app.supersax.ui.theme.CardRadius
import app.supersax.ui.theme.PhoneBlue
import app.supersax.ui.theme.Sax
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The dashboard (Home tab). */
@Composable
fun HomeScreen(
    onStartSession: () -> Unit,
    onSettings: () -> Unit,
    onPrograms: () -> Unit,
    onPhoneDrill: (ExerciseType) -> Unit,
    onRank: () -> Unit,
    onHistory: () -> Unit,
    onDaily: () -> Unit = {},
) {
    val vm = appViewModel { HomeViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val locale = if (LocaleHelper.effective(LocalContext.current).startsWith("fr")) Locale.FRENCH else Locale.ENGLISH
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 4.dp, top = 8.dp)) {
                Overline(ui.today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)).replaceFirstChar { it.titlecase(locale) })
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = Sax.colors.textSecondary) }
        }
        if (!ui.loading) {
            TodayHero(ui, vm, onStartSession, onPrograms)
            DailyCard(ui, onDaily)
            WeekCard(ui, locale, onHistory)
            RankCard(ui, onRank)
            UpcomingCard(ui, locale)
            val drills = ui.plan?.phoneDrills.orEmpty()
            if (drills.isNotEmpty()) SectionCard(stringResource(R.string.home_phone_today)) {
                drills.forEach { d ->
                    NavRow(exerciseTypeLabel(d.type), stringResource(R.string.minutes_short, d.minutes), Icons.Rounded.Smartphone,
                        { onPhoneDrill(d.type) }, tint = PhoneBlue, background = PhoneBlue.copy(alpha = 0.14f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The amber card: today's session and its Start button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayHero(ui: HomeUi, vm: HomeViewModel, onStart: () -> Unit, onPrograms: () -> Unit) {
    val plan = ui.plan
    val session = plan?.session
    val ink = Sax.colors.onAccent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(Sax.colors.accent).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.home_today_session), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f))
        when {
            ui.running -> {
                Text(stringResource(R.string.home_in_progress), style = MaterialTheme.typography.headlineMedium, color = ink)
                HeroButton(stringResource(R.string.resume_session)) { vm.start(onStart) }
            }
            plan?.program == null -> {
                Text(stringResource(R.string.today_no_program), style = MaterialTheme.typography.bodyLarge, color = ink)
                HeroButton(stringResource(R.string.programs), onPrograms)
            }
            session == null || session.steps.isEmpty() -> {
                Text(stringResource(R.string.today_program_empty), style = MaterialTheme.typography.bodyLarge, color = ink)
                HeroButton(stringResource(R.string.edit_program), onPrograms)
            }
            else -> {
                val day = plan.day!!
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${session.playingSec / 60}", style = MaterialTheme.typography.displayLarge, color = ink)
                    Text(" min", style = MaterialTheme.typography.titleLarge, color = ink, modifier = Modifier.padding(bottom = 8.dp))
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.today_total_with_rests, formatClock(session.totalSec.toLong())),
                        style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 10.dp))
                }
                if (plan.offDay) Text(stringResource(R.string.today_off_day, dayLabel(day.dayOfWeek)), style = MaterialTheme.typography.bodyMedium, color = ink)
                // What the session holds, block by block.
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.35f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    session.steps.filter { it.kind == StepKind.PLAY && it.chunk == 1 }.forEach { st ->
                        val detail = when {
                            st.target.scaleKey != null -> "${scaleLabel(st.target.scaleKey)} · ${stringResource(R.string.bpm_value, st.target.bpm ?: 0)}"
                            st.target.holdSec != null -> stringResource(R.string.hold_target, st.target.holdSec)
                            else -> exerciseTypeLabel(st.exerciseType)
                        }
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(blockKindLabel(st.blockKind), style = MaterialTheme.typography.titleSmall, color = ink)
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f))
                            }
                            Text(formatClock((st.durationSec * st.chunks).toLong()), style = MaterialTheme.typography.titleSmall, color = ink)
                        }
                    }
                }
                HeroButton(stringResource(R.string.start_session_minutes, session.playingSec / 60)) { vm.start(onStart) }
                Text(stringResource(R.string.short_on_time), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, 5, 10, 15, 20).filter { it == null || it < day.minutes }.forEach { m ->
                        val selected = ui.availableMinutes == m
                        Box(
                            Modifier.heightIn(min = 40.dp).clip(CircleShape)
                                .background(if (selected) ink else Color.White.copy(alpha = 0.35f))
                                .clickable { vm.setAvailable(m) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (m == null) stringResource(R.string.full_session) else stringResource(R.string.minutes_short, m),
                                style = MaterialTheme.typography.labelLarge, color = if (selected) Sax.colors.accent else ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.onAccent, contentColor = Sax.colors.accent),
    ) {
        Icon(Icons.Rounded.PlayArrow, null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Monday to Sunday: played, planned, rest. */
@Composable
private fun WeekCard(ui: HomeUi, locale: Locale, onHistory: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onHistory)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_this_week), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.home_week_minutes, ui.weekMinutes), style = MaterialTheme.typography.labelLarge, color = Sax.colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ui.week.forEach { d -> DayDot(d, locale) }
        }
        Text(
            if (ui.weekPlanned == 0) stringResource(R.string.home_week_no_plan)
            else stringResource(R.string.home_week_sessions, ui.weekDone, ui.weekPlanned),
            style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary,
        )
    }
}

@Composable
private fun DayDot(d: WeekDay, locale: Locale) {
    val s = Sax.colors
    val past = d.date.isBefore(LocalDate.now())
    val state = stringResource(
        when {
            d.done -> R.string.home_day_done
            d.planned && past -> R.string.home_day_missed
            d.planned -> R.string.home_day_planned
            else -> R.string.home_day_rest
        },
    )
    val name = d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$name: $state" }) {
        Text(name.take(1).uppercase(locale), style = MaterialTheme.typography.labelMedium,
            color = if (d.isToday) s.text else s.textSecondary, fontWeight = if (d.isToday) FontWeight.Bold else FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        val bg = when {
            d.done -> s.accent
            d.planned -> s.elevated
            else -> Color.Transparent
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(bg)
                .then(if (d.isToday) Modifier.border(2.dp, s.accentInk, CircleShape) else Modifier)
                .then(if (!d.planned && !d.done) Modifier.border(1.dp, s.divider, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (d.done) Icon(Icons.Rounded.Check, null, tint = s.onAccent, modifier = Modifier.size(20.dp))
            else Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                color = if (d.planned && past) s.warn else if (d.planned) s.text else s.textTertiary)
        }
    }
}

@Composable
private fun RankCard(ui: HomeUi, onRank: () -> Unit) {
    val r = ui.rank ?: return
    SectionCard(modifier = Modifier.clickable(onClick = onRank)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.EmojiEvents, size = 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Overline(stringResource(R.string.rank))
                Text(rankName(r.tier, r.division), style = MaterialTheme.typography.headlineSmall)
                if (r.limiter != null) Text(stringResource(R.string.home_weakest, pillarLabel(r.limiter)),
                    style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun UpcomingCard(ui: HomeUi, locale: Locale) {
    if (ui.plan?.program == null) return
    SectionCard(stringResource(R.string.home_coming_up)) {
        if (ui.upcoming.isEmpty()) EmptyState(stringResource(R.string.home_nothing_planned))
        ui.upcoming.forEachIndexed { i, u ->
            if (i > 0) HorizontalDivider(color = Sax.colors.divider)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Event, tint = Sax.colors.textSecondary, background = Sax.colors.elevated, size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val name = if (u.date == ui.today.plusDays(1)) stringResource(R.string.home_tomorrow)
                    else u.date.format(DateTimeFormatter.ofPattern("EEEE d", locale)).replaceFirstChar { it.titlecase(locale) }
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    val labels = u.day.blocks.filter { it.minutes > 0 }.map { it.kind }.distinct().map { blockKindLabel(it) }
                    Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
                }
                Text(stringResource(R.string.minutes_short, u.day.minutes), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
            }
        }
    }
}

/** The daily 5-minute phone exercise, with the streak. */
@Composable
private fun DailyCard(ui: HomeUi, onDaily: () -> Unit) {
    val s = Sax.colors
    SectionCard(modifier = Modifier.clickable(onClick = onDaily)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(if (ui.streak > 0) s.warn.copy(alpha = 0.16f) else s.elevated),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = if (ui.streak > 0) s.warn else s.textTertiary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Overline(stringResource(R.string.daily_title))
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.streak_days, ui.streak, ui.streak), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (ui.dailyDone) R.string.daily_done_today else R.string.daily_sub),
                    style = MaterialTheme.typography.bodySmall, color = s.textSecondary)
            }
            if (ui.dailyDone) Icon(Icons.Rounded.Check, stringResource(R.string.daily_done_today), tint = s.good, modifier = Modifier.size(28.dp))
            else Button(onClick = onDaily, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = s.accent, contentColor = s.onAccent)) { Text(stringResource(R.string.daily_go)) }
        }
    }
}
