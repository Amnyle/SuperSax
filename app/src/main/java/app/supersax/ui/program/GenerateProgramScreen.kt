package app.supersax.ui.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.generator.Generator
import app.supersax.domain.program.Endurance
import app.supersax.domain.program.Focus
import app.supersax.domain.program.Pillar
import app.supersax.domain.program.Program
import app.supersax.domain.program.ProgramOrigin
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.pillarLabel
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.onboarding.DaysPage
import app.supersax.ui.onboarding.FocusPage
import app.supersax.ui.onboarding.ProgramPreview
import app.supersax.ui.onboarding.Question
import app.supersax.ui.onboarding.RadioPage
import app.supersax.ui.onboarding.enduranceLabel
import app.supersax.ui.rank.rankName
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** The player's level as the generator sees it, shown with the preview. */
data class LevelSummary(val tier: Int, val division: Int, val limiter: Pillar?, val scales: Int, val fastest: Pair<String, Int>?)

data class GenerateUi(
    val page: Int = 0,
    val days: Set<Int> = setOf(1, 3, 5),
    val minutes: Int = 20,
    val endurance: Endurance = Endurance.FROM_5_TO_10,
    val focus: List<Focus> = emptyList(),
    val level: LevelSummary? = null,
    val preview: Program? = null,
    val saving: Boolean = false,
)

/**
 * "Generate a program": a few questions (days and session length, how long you can play without a break now,
 * what to focus on), then a program built for the player's current level (rank and weakest pillar, scales and
 * their tempos), shown before it is saved.
 */
class GenerateProgramViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(GenerateUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val a = c.practice.onboarding()
            val active = c.practice.activeProgram.first()
            val player = c.practice.playerNow()
            val caps = c.content.generatorConfig.enduranceCapSec
            // Today's answers as defaults: the active program's days and length, the stamina and focus on record.
            val days = active?.days?.map { it.dayOfWeek }?.toSet()?.takeIf { it.isNotEmpty() } ?: a?.days ?: setOf(1, 3, 5)
            val minutes = active?.days?.takeIf { it.isNotEmpty() }?.let { d -> (d.sumOf { it.minutes } / d.size / 5) * 5 }?.coerceIn(5, 90)
                ?: a?.minutesPerSession ?: 20
            val endurance = caps.entries.filter { it.value <= player.enduranceCapSec }.maxByOrNull { it.value }?.key ?: a?.endurance ?: Endurance.FROM_5_TO_10
            _ui.update { it.copy(days = days, minutes = minutes, endurance = endurance, focus = player.focus.filter { f -> f != Focus.REPERTOIRE }) }
            val rank = runCatching { c.rank.current() }.getOrNull()
            val fastest = player.scales.filter { it.bestCleanBpm > 0 }.maxByOrNull { it.bestCleanBpm }?.let { it.scaleKey to it.bestCleanBpm }
            _ui.update { it.copy(level = LevelSummary(rank?.overallTier ?: 0, rank?.division ?: 0, rank?.limiter, player.scales.size, fastest)) }
        }
    }

    fun days(d: Set<Int>) = _ui.update { it.copy(days = d) }
    fun minutes(m: Int) = _ui.update { it.copy(minutes = m) }
    fun endurance(e: Endurance) = _ui.update { it.copy(endurance = e) }
    fun focus(f: List<Focus>) = _ui.update { it.copy(focus = f) }
    fun back() = _ui.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }

    fun next() {
        val u = _ui.value
        if (u.page < LAST_QUESTION) { _ui.update { it.copy(page = it.page + 1) }; return }
        viewModelScope.launch {
            val cfg = c.content.generatorConfig
            val p = Generator.generate(
                Generator.Input(UUID.randomUUID().toString(), "", u.days.ifEmpty { setOf(1) }, u.minutes, cfg.enduranceCapSec[u.endurance] ?: 300,
                    u.focus, u.level?.limiter ?: runCatching { c.currentLimiter() }.getOrNull(), c.availablePhoneDrills,
                    c.settings.current().restSec, c.clock.nowMs()),
                cfg,
            )
            _ui.update { it.copy(page = LAST_QUESTION + 1, preview = p) }
        }
    }

    /** Saves the program (active when [activate]), records the answers and the player's stamina and focus. */
    fun save(name: String, activate: Boolean, onSaved: (String) -> Unit) {
        val u = _ui.value
        val p = u.preview ?: return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val cfg = c.content.generatorConfig
            val program = p.copy(name = name, origin = ProgramOrigin.GENERATED)
            c.practice.saveProgram(program, makeActive = activate, nowMs = c.clock.nowMs())
            val a = c.practice.onboarding()
            if (a != null) c.practice.saveOnboarding(a.copy(days = u.days, minutesPerSession = u.minutes, endurance = u.endurance, focus = u.focus))
            val player = c.practice.playerNow()
            c.practice.savePlayer(player.copy(enduranceCapSec = cfg.enduranceCapSec[u.endurance] ?: player.enduranceCapSec, focus = u.focus))
            onSaved(program.id)
        }
    }

    companion object { const val LAST_QUESTION = 2 }
}

@Composable
fun GenerateProgramScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val vm = appViewModel { GenerateProgramViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    BackHandler(ui.page > 0) { vm.back() }
    val name = stringResource(R.string.program_default_name)
    ScreenScaffold(stringResource(R.string.generate_program), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (ui.page <= GenerateProgramViewModel.LAST_QUESTION) {
                LinearProgressIndicator(progress = { (ui.page + 1f) / (GenerateProgramViewModel.LAST_QUESTION + 2) }, modifier = Modifier.fillMaxWidth())
            }
            when (ui.page) {
                0 -> DaysPage(ui.days, ui.minutes, vm::days, vm::minutes)
                1 -> RadioPage(stringResource(R.string.gen_endurance_q), Endurance.entries.map { enduranceLabel(it) }, ui.endurance.ordinal) { i ->
                    vm.endurance(Endurance.entries[i])
                }
                2 -> FocusPage(ui.focus, vm::focus)
                else -> {
                    Question(stringResource(R.string.gen_result_title))
                    ui.level?.let { LevelCard(it) }
                    ui.preview?.let { ProgramPreview(it) }
                }
            }
            val amber = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)
            if (ui.page <= GenerateProgramViewModel.LAST_QUESTION) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (ui.page > 0) TextButton(onClick = vm::back, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = vm::next, enabled = ui.days.isNotEmpty(), colors = amber, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text(stringResource(if (ui.page == GenerateProgramViewModel.LAST_QUESTION) R.string.gen_build else R.string.next))
                    }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.save(name, true) { onBack() } }, enabled = !ui.saving, colors = amber,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.onb_use_program)) }
                OutlinedButton(onClick = { vm.save(name, false, onEdit) }, enabled = !ui.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.gen_edit_first)) }
                TextButton(onClick = vm::back, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
            }
        }
    }
}

@Composable
private fun LevelCard(l: LevelSummary) {
    SectionCard(stringResource(R.string.gen_level_title)) {
        Text(stringResource(R.string.gen_level_rank, rankName(l.tier, l.division)), style = MaterialTheme.typography.bodyLarge)
        l.limiter?.let { Text(stringResource(R.string.gen_level_weakest, pillarLabel(it)), style = MaterialTheme.typography.bodyLarge) }
        Text(stringResource(R.string.gen_level_scales, l.scales), style = MaterialTheme.typography.bodyLarge)
        l.fastest?.let { (k, bpm) -> Text(stringResource(R.string.gen_level_fastest, scaleLabel(k), bpm), style = MaterialTheme.typography.bodyLarge) }
        Text(stringResource(R.string.gen_level_note), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
    }
}
