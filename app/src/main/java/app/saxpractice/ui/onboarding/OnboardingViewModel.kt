package app.saxpractice.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.domain.backup.SettingsRecord
import app.saxpractice.domain.common.GoalRecord
import app.saxpractice.domain.common.GoalType
import app.saxpractice.domain.generator.Generator
import app.saxpractice.domain.pitch.AltoRegisters
import app.saxpractice.domain.program.OnboardingAnswers
import app.saxpractice.domain.program.PlayerState
import app.saxpractice.domain.program.Program
import app.saxpractice.domain.program.ProgramOrigin
import app.saxpractice.domain.program.Registers
import app.saxpractice.domain.program.ScaleProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class OnboardingUi(
    val page: Int = 0,
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val settings: SettingsRecord = SettingsRecord(),
    val preview: Program? = null,
    val saving: Boolean = false,
)

/** Onboarding: one decision per page, re-runnable from settings. Answers seed the player state and the program. */
class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui: StateFlow<OnboardingUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val previous = c.practice.onboarding()
            _ui.update { it.copy(answers = previous ?: it.answers, settings = c.settings.current()) }
        }
    }

    fun answers(transform: (OnboardingAnswers) -> OnboardingAnswers) = _ui.update { it.copy(answers = transform(it.answers)) }

    /** Settings step: applied immediately so the language and note names change on screen. */
    fun settings(transform: (SettingsRecord) -> SettingsRecord) {
        _ui.update { it.copy(settings = transform(it.settings)) }
        viewModelScope.launch { c.settings.update(transform) }
    }

    fun next() = _ui.update {
        val page = (it.page + 1).coerceAtMost(LAST_PAGE)
        it.copy(page = page, preview = if (page == LAST_PAGE) generate(it.answers) else it.preview)
    }

    fun back() = _ui.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }

    private fun generate(a: OnboardingAnswers): Program {
        val cfg = c.content.generatorConfig
        return Generator.generate(
            Generator.Input(
                programId = UUID.randomUUID().toString(),
                name = "",
                days = a.days.ifEmpty { setOf(1) },
                minutesPerSession = a.minutesPerSession,
                enduranceCapSec = cfg.enduranceCapSec[a.endurance] ?: 300,
                focus = a.focus,
                availablePhoneDrills = c.availablePhoneDrills,
                restSec = _ui.value.settings.restSec,
                createdAtEpochMs = c.clock.nowMs(),
            ),
            cfg,
        )
    }

    /** Seeds the player state from the answers (kept separate so it can be tested by hand in the UI). */
    private fun seedPlayer(a: OnboardingAnswers, previous: PlayerState): PlayerState {
        val cfg = c.content.generatorConfig
        val now = c.clock.nowMs()
        val scales = a.cleanScales.ifEmpty { cfg.adaptation.unlockOrder.take(1).toSet() }.mapIndexed { i, key ->
            previous.scale(key) ?: ScaleProgress(key, a.comfortableBpm, unlockedAtEpochMs = now + i,
                bestCleanBpm = if (key in a.cleanScales) a.comfortableBpm else 0)
        }
        return previous.copy(
            scales = scales + previous.scales.filter { p -> scales.none { it.scaleKey == p.scaleKey } },
            holdTargetSec = a.longestHoldSec.coerceIn(cfg.adaptation.holdMinSec, cfg.adaptation.holdMaxSec),
            enduranceCapSec = cfg.enduranceCapSec[a.endurance] ?: 300,
            registers = a.registers,
            readingLevel = a.readingLevel,
            theoryLevel = a.theoryLevel,
            focus = a.focus.take(2),
            writtenLowMidi = if (Registers.LOW in a.registers) AltoRegisters.LOW_BB else 62,
            writtenHighMidi = if (Registers.HIGH in a.registers) AltoRegisters.HIGH_FSHARP else 84,
        )
    }

    /** Finishes onboarding. Returns the id of the program to open in the editor when building manually. */
    fun finish(useGenerated: Boolean, programName: String, onDone: (manualProgramId: String?) -> Unit) {
        val state = _ui.value
        if (state.saving) return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val a = state.answers
            val now = c.clock.nowMs()
            c.practice.saveOnboarding(a)
            c.practice.savePlayer(seedPlayer(a, c.practice.playerNow()))
            val program = if (useGenerated) (state.preview ?: generate(a)).copy(name = programName)
            else Program(UUID.randomUUID().toString(), programName, ProgramOrigin.MANUAL, emptyList(),
                enduranceCapSec = c.content.generatorConfig.enduranceCapSec[a.endurance] ?: 300,
                restSec = state.settings.restSec, createdAtEpochMs = now)
            c.practice.saveProgram(program, makeActive = true, nowMs = now)
            (a.songsNow + a.songsNext).filter { it.isNotBlank() }.forEach { title ->
                c.practice.saveGoal(GoalRecord(UUID.randomUUID().toString(), GoalType.SONG_MILESTONE, title.trim(), now))
            }
            c.settings.update { it.copy(onboardingDone = true) }
            onDone(if (useGenerated) null else program.id)
        }
    }

    companion object { const val LAST_PAGE = 9 }
}
