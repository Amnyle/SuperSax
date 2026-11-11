package app.supersax.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import app.supersax.ui.components.IconTile
import app.supersax.ui.theme.FieldRadius
import app.supersax.ui.theme.Sax
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.backup.ThemeMode
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.program.Endurance
import app.supersax.domain.program.Focus
import app.supersax.domain.program.Registers
import app.supersax.domain.scale.ScaleId
import app.supersax.domain.scale.ScaleType
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.Stepper
import app.supersax.ui.components.blockKindLabel
import app.supersax.ui.components.dayLabel
import app.supersax.ui.components.exerciseTypeLabel
import app.supersax.ui.components.focusLabel
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.nav.appViewModel

/** Onboarding, one question per page. [onFinished] receives a program id when the user chose to build manually. */
@Composable
fun OnboardingScreen(onFinished: (manualProgramId: String?) -> Unit) {
    val vm = appViewModel { OnboardingViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val a = ui.answers
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            LinearProgressIndicator(
                progress = { (ui.page + 1f) / (OnboardingViewModel.LAST_PAGE + 1) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Sax.colors.accent, trackColor = Sax.colors.elevated, drawStopIndicator = {},
            )
            Text(
                stringResource(R.string.onb_step, ui.page + 1, OnboardingViewModel.LAST_PAGE + 1),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (ui.page) {
                    0 -> WelcomePage()
                    1 -> RadioPage(stringResource(R.string.onb_language_q), LANGUAGES.map { stringResource(it.second) },
                        LANGUAGES.indexOfFirst { it.first == ui.settings.language }.coerceAtLeast(0)) { i ->
                        vm.settings { it.copy(language = LANGUAGES[i].first) }
                    }
                    2 -> RadioPage(stringResource(R.string.onb_naming_q),
                        listOf(stringResource(R.string.onb_naming_letters), stringResource(R.string.onb_naming_solfege)),
                        if (ui.settings.noteNaming == NoteNaming.LETTERS) 0 else 1) { i ->
                        vm.settings { it.copy(noteNaming = if (i == 0) NoteNaming.LETTERS else NoteNaming.SOLFEGE) }
                    }
                    3 -> DaysPage(a.days, a.minutesPerSession, { d -> vm.answers { it.copy(days = d) } }, { m -> vm.answers { it.copy(minutesPerSession = m) } })
                    4 -> ScalesPage(a.cleanScales, a.comfortableBpm, { s -> vm.answers { it.copy(cleanScales = s) } }, { b -> vm.answers { it.copy(comfortableBpm = b) } })
                    5 -> RadioPage(stringResource(R.string.onb_reading_q), readingLevels(), a.readingLevel) { i -> vm.answers { it.copy(readingLevel = i) } }
                    6 -> RadioPage(stringResource(R.string.onb_theory_q), theoryLevels(), a.theoryLevel) { i -> vm.answers { it.copy(theoryLevel = i) } }
                    7 -> FocusPage(a.focus) { f -> vm.answers { it.copy(focus = f) } }
                    else -> ResultPage(ui)
                }
            }
            val defaultName = stringResource(R.string.program_default_name)
            val amber = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)
            if (ui.page < OnboardingViewModel.LAST_PAGE) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ui.page > 0) TextButton(onClick = vm::back, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = vm::next, enabled = ui.page != 3 || a.days.isNotEmpty(), modifier = Modifier.heightIn(min = 52.dp), colors = amber) {
                        Text(stringResource(if (ui.page == 0) R.string.onb_get_started else R.string.next), style = MaterialTheme.typography.titleSmall)
                    }
                }
            } else {
                // Last page: the two choices stacked full width (side by side they do not fit narrow screens in French).
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.finish(true, defaultName, onFinished) }, enabled = !ui.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = amber) {
                        Text(stringResource(R.string.onb_use_program), style = MaterialTheme.typography.titleSmall)
                    }
                    OutlinedButton(onClick = { vm.finish(false, defaultName, onFinished) }, enabled = !ui.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.onb_build_manually))
                    }
                    TextButton(onClick = vm::back, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
                }
            }
        }
    }
}

@Composable
private fun Question(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium)

/** Language choices of the onboarding (code, label). */
private val LANGUAGES = listOf("system" to R.string.language_system, "en" to R.string.language_en, "fr" to R.string.language_fr)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysPage(days: Set<Int>, minutes: Int, onDays: (Set<Int>) -> Unit, onMinutes: (Int) -> Unit) {
    Question(stringResource(R.string.onb_days_q))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..7).forEach { d ->
            FilterChip(selected = d in days, onClick = { onDays(if (d in days) days - d else days + d) }, label = { Text(dayLabel(d, short = true)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    Stepper(stringResource(R.string.onb_minutes), minutes, onMinutes, 5..90, 5) { stringResource(R.string.minutes_short, it) }
}

@Composable
private fun RadioPage(question: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Question(question)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(FieldRadius))
                    .background(if (i == selected) Sax.colors.accentSoft else Sax.colors.card)
                    .selectable(selected = i == selected, onClick = { onSelect(i) }, role = Role.RadioButton).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = i == selected, onClick = null)
                Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** First page: what the app is for, before any question. */
@Composable
private fun WelcomePage() {
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Color(0xFF15171C)), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, modifier = Modifier.size(84.dp))
    }
    Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineLarge)
    Text(stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge, color = Sax.colors.textSecondary)
    listOf(
        Triple(Icons.Rounded.CalendarMonth, R.string.onb_welcome_plan, R.string.onb_welcome_plan_sub),
        Triple(Icons.Rounded.GraphicEq, R.string.onb_welcome_listen, R.string.onb_welcome_listen_sub),
        Triple(Icons.Rounded.Smartphone, R.string.onb_welcome_phone, R.string.onb_welcome_phone_sub),
        Triple(Icons.Rounded.EmojiEvents, R.string.onb_welcome_rank, R.string.onb_welcome_rank_sub),
    ).forEach { (icon, title, sub) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
        }
    }
    Text(stringResource(R.string.onb_welcome_private), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
    Text(stringResource(R.string.onb_welcome_next), style = MaterialTheme.typography.titleSmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScalesPage(clean: Set<String>, bpm: Int, onClean: (Set<String>) -> Unit, onBpm: (Int) -> Unit) {
    Question(stringResource(R.string.onb_scales_q))
    Text(stringResource(R.string.onb_scales_hint), style = MaterialTheme.typography.bodyMedium)
    listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR).forEach { type ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScaleId.all(type).sortedBy { it.writtenKeySignature().fifths }.forEach { id ->
                val key = id.key
                FilterChip(selected = key in clean, onClick = { onClean(if (key in clean) clean - key else clean + key) },
                    label = { Text(scaleLabel(key)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    Stepper(stringResource(R.string.onb_bpm), bpm, onBpm, 40..200, 5) { stringResource(R.string.bpm_value, it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusPage(focus: List<Focus>, onFocus: (List<Focus>) -> Unit) {
    Question(stringResource(R.string.onb_focus_q))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Focus.entries.filter { it != Focus.REPERTOIRE }.forEach { f ->
            val selected = f in focus
            FilterChip(
                selected = selected,
                onClick = { onFocus(if (selected) focus - f else (focus + f).takeLast(2)) },
                label = { Text(focusLabel(f)) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** Language, note names and theme pickers, shared by onboarding and settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageNamingTheme(
    language: String,
    naming: NoteNaming,
    theme: ThemeMode,
    update: ((app.supersax.domain.backup.SettingsRecord) -> app.supersax.domain.backup.SettingsRecord) -> Unit,
) {
    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("system" to R.string.language_system, "en" to R.string.language_en, "fr" to R.string.language_fr).forEach { (code, label) ->
            FilterChip(selected = language == code, onClick = { update { it.copy(language = code) } }, label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    Text(stringResource(R.string.settings_note_names), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NoteNaming.entries.forEach { n ->
            FilterChip(selected = naming == n, onClick = { update { it.copy(noteNaming = n) } },
                label = { Text(stringResource(if (n == NoteNaming.LETTERS) R.string.naming_letters else R.string.naming_solfege)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { t ->
            FilterChip(selected = theme == t, onClick = { update { it.copy(theme = t) } },
                label = { Text(stringResource(when (t) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark })) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun ResultPage(ui: OnboardingUi) {
    Question(stringResource(R.string.onb_result_title))
    Text(stringResource(R.string.onb_result_body), style = MaterialTheme.typography.bodyLarge)
    val p = ui.preview ?: return
    p.days.forEach { d ->
        SectionCard(stringResource(R.string.day_minutes, dayLabel(d.dayOfWeek), d.minutes)) {
            d.blocks.forEach { b ->
                Text("${blockKindLabel(b.kind)} · ${stringResource(R.string.minutes_short, b.minutes)}")
            }
        }
    }
    if (p.phoneDrills.isNotEmpty()) SectionCard(stringResource(R.string.phone_sessions)) {
        p.phoneDrills.forEach { Text("${dayLabel(it.dayOfWeek)} · ${exerciseTypeLabel(it.type)} · ${stringResource(R.string.minutes_short, it.minutes)}") }
    }
}

@Composable
fun enduranceLabel(e: Endurance): String = stringResource(
    when (e) {
        Endurance.UNDER_3 -> R.string.endurance_under_3
        Endurance.FROM_3_TO_5 -> R.string.endurance_3_5
        Endurance.FROM_5_TO_10 -> R.string.endurance_5_10
        Endurance.FROM_10_TO_20 -> R.string.endurance_10_20
        Endurance.OVER_20 -> R.string.endurance_over_20
    },
)

@Composable
fun readingLevels() = listOf(R.string.reading_0, R.string.reading_1, R.string.reading_2, R.string.reading_3, R.string.reading_4).map { stringResource(it) }

@Composable
fun theoryLevels() = listOf(R.string.theory_0, R.string.theory_1, R.string.theory_2, R.string.theory_3, R.string.theory_4).map { stringResource(it) }

/** Separates a song title from the library piece id it was picked from (song goals keep the link). */
const val PIECE_SEPARATOR = "\u001F"
