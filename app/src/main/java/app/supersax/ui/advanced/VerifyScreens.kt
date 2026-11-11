package app.supersax.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.audio.AltissimoCheck
import app.supersax.domain.audio.OvertoneDrill
import app.supersax.domain.common.DrillKind
import app.supersax.domain.common.DrillResultRecord
import app.supersax.domain.common.RankEventKind
import app.supersax.domain.common.RankEventRecord
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.pitch.Tuning
import app.supersax.notation.StaffLayout
import app.supersax.notation.StaffView
import app.supersax.ui.audio.CentsNeedle
import app.supersax.ui.audio.LiveTuner
import app.supersax.ui.audio.MicErrorBanner
import app.supersax.ui.audio.centsText
import app.supersax.ui.audio.hasMicPermission
import app.supersax.ui.audio.rememberMicRequest
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Shared listening state for the overtone and altissimo checks. */
class VerifyViewModel(private val c: AppContainer) : ViewModel() {
    val tuner = LiveTuner(viewModelScope, c.mic, { c.audio.a4 }, 40.0)
    val micState = c.mic.state
    val a4: Double get() = c.audio.a4
    private val _overtone = MutableStateFlow(OvertoneDrill.milestone.first())
    val overtone = _overtone.asStateFlow()
    private val _altissimo = MutableStateFlow(AltissimoCheck.TARGETS.first())
    val altissimo = _altissimo.asStateFlow()
    private val _success = MutableStateFlow(false)
    val success = _success.asStateFlow()

    /** Overtone targets already confirmed (stored as drill results, so they survive restarts). */
    val doneOvertones = c.progress.drills.map { list ->
        list.filter { it.kind == DrillKind.OVERTONE && it.correct > 0 }.mapNotNull { r ->
            val (m, p) = r.detail.removePrefix("target=").split(":").map { it.toIntOrNull() ?: return@mapNotNull null }
            OvertoneDrill.Target(m, p)
        }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val milestone = c.progress.rankEvents.map { e -> e.any { it.kind == RankEventKind.OVERTONE_MILESTONE && it.verified } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun start(granted: Boolean) = tuner.start(granted)
    fun stop() = tuner.stop()
    fun selectOvertone(t: OvertoneDrill.Target) { _overtone.value = t; _success.value = false }
    fun selectAltissimo(w: Int) { _altissimo.value = w; _success.value = false }

    /** Checks the live pitch against the overtone target; held long enough → recorded. */
    fun checkOvertone(hz: Double?, holdSec: Double) {
        if (_success.value || hz == null) return
        val t = _overtone.value
        if (OvertoneDrill.matches(hz, t, a4) && holdSec >= OvertoneDrill.HOLD_SEC) {
            _success.value = true
            viewModelScope.launch {
                val now = c.clock.nowMs()
                c.progress.saveDrill(DrillResultRecord(UUID.randomUUID().toString(), now, DrillKind.OVERTONE, t.partial, 1, 1, 0, "target=${t.writtenFingeringMidi}:${t.partial}"))
                if (OvertoneDrill.milestoneDone(doneOvertones.value + t) && !milestone.value) {
                    c.progress.addRankEvent(RankEventRecord(UUID.randomUUID().toString(), now, RankEventKind.OVERTONE_MILESTONE, verified = true))
                }
            }
        }
    }

    /** Altissimo: the written note held for about two seconds within a generous tolerance → verified Range note. */
    fun checkAltissimo(hz: Double?, holdSec: Double) {
        if (_success.value || hz == null) return
        val w = _altissimo.value
        if (AltissimoCheck.matches(hz, w, a4) && holdSec >= AltissimoCheck.HOLD_SEC) {
            _success.value = true
            viewModelScope.launch {
                val now = c.clock.nowMs()
                c.progress.addRankEvent(RankEventRecord(UUID.randomUUID().toString(), now, RankEventKind.RANGE_NOTE, writtenMidi = w, verified = true))
                c.progress.saveDrill(DrillResultRecord(UUID.randomUUID().toString(), now, DrillKind.ALTISSIMO, w, 1, 1, 0, "written=$w"))
            }
        }
    }

    override fun onCleared() { tuner.stop() }
}

@Composable
private fun Listening(vm: VerifyViewModel) {
    val context = LocalContext.current
    val mic by vm.micState.collectAsStateWithLifecycle()
    val request = rememberMicRequest { vm.start(it) }
    LaunchedEffect(Unit) { request() }
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    MicErrorBanner(mic) { vm.stop(); vm.start(hasMicPermission(context)) }
}

/** Overtone drill: finger a low note, overblow to the 2nd or 3rd partial; the tuner confirms. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OvertoneScreen(onBack: () -> Unit) {
    val vm = appViewModel { VerifyViewModel(it) }
    val reading by vm.tuner.reading.collectAsStateWithLifecycle()
    val target by vm.overtone.collectAsStateWithLifecycle()
    val done by vm.doneOvertones.collectAsStateWithLifecycle()
    val success by vm.success.collectAsStateWithLifecycle()
    val milestone by vm.milestone.collectAsStateWithLifecycle()
    val naming = LocalSettings.current.noteNaming
    LaunchedEffect(reading) { vm.checkOvertone(reading.frequencyHz, reading.holdSec) }
    ScreenScaffold(stringResource(R.string.overtones), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Listening(vm)
            Text(stringResource(R.string.overtones_hint), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OvertoneDrill.milestone.forEach { t ->
                    FilterChip(selected = t == target, onClick = { vm.selectOvertone(t) },
                        label = { Text((if (t in done) "✓ " else "") + stringResource(R.string.overtone_chip, naming.format(SpelledPitch.fromMidi(t.writtenFingeringMidi)), t.partial)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            val sounding = OvertoneDrill.soundingWrittenMidi(target, vm.a4)
            SectionCard(stringResource(R.string.overtone_target, naming.format(SpelledPitch.fromMidi(target.writtenFingeringMidi)), target.partial,
                naming.format(SpelledPitch.fromMidi(sounding)))) {
                StaffView(StaffLayout.singleNote(SpelledPitch.fromMidi(target.writtenFingeringMidi)), staffSpace = 9.dp, showTime = false)
                val off = reading.frequencyHz?.let { OvertoneDrill.centsOff(it, target, vm.a4) }
                CentsNeedle(off?.takeIf { kotlin.math.abs(it) < 300 }, OvertoneDrill.TOLERANCE_CENTS, Modifier.heightIn(min = 90.dp))
                Text(off?.let { if (kotlin.math.abs(it) < 300) centsText(it, OvertoneDrill.TOLERANCE_CENTS) else stringResource(R.string.overtone_wrong_partial) }
                    ?: stringResource(R.string.tuner_play_a_note), style = MaterialTheme.typography.titleLarge)
                if (success) Text(stringResource(R.string.verified), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleLarge)
            }
            Text(stringResource(if (milestone) R.string.overtone_milestone_done else R.string.overtone_milestone_todo, done.size, OvertoneDrill.milestone.size))
        }
    }
}

/** Altissimo verification: hold a written note from G6 up for about two seconds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AltissimoScreen(onBack: () -> Unit) {
    val vm = appViewModel { VerifyViewModel(it) }
    val reading by vm.tuner.reading.collectAsStateWithLifecycle()
    val target by vm.altissimo.collectAsStateWithLifecycle()
    val success by vm.success.collectAsStateWithLifecycle()
    val s = LocalSettings.current
    LaunchedEffect(reading) { vm.checkAltissimo(reading.frequencyHz, reading.holdSec) }
    ScreenScaffold(stringResource(R.string.altissimo), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Listening(vm)
            Text(stringResource(R.string.altissimo_hint), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AltissimoCheck.TARGETS.forEach { w ->
                    FilterChip(selected = w == target, onClick = { vm.selectAltissimo(w) }, label = { Text(s.noteNaming.format(SpelledPitch.fromMidi(w))) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            StaffView(StaffLayout.singleNote(SpelledPitch.fromMidi(target)), staffSpace = 10.dp, showTime = false)
            val off = reading.frequencyHz?.let { Tuning.cents(it, Tuning.midiToFrequency(s.instrument.writtenToConcertMidi(target), vm.a4)) }
            CentsNeedle(off?.takeIf { kotlin.math.abs(it) < 300 }, AltissimoCheck.TOLERANCE_CENTS, Modifier.heightIn(min = 90.dp))
            Text(off?.let { if (kotlin.math.abs(it) < 300) centsText(it, AltissimoCheck.TOLERANCE_CENTS) else stringResource(R.string.altissimo_other_note) }
                ?: stringResource(R.string.tuner_play_a_note), style = MaterialTheme.typography.titleLarge)
            if (reading.holding) Text(stringResource(R.string.holding_for, reading.holdSec.toInt()))
            if (success) Text(stringResource(R.string.altissimo_verified), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.altissimo_caveat), style = MaterialTheme.typography.bodySmall)
        }
    }
}
