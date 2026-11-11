package app.supersax.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.audio.OutputState
import app.supersax.domain.audio.MetronomeGenerator
import app.supersax.ui.components.BigButton
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.Stepper
import app.supersax.ui.components.writtenNoteLabel
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tempo trainer: two clean runs in a row raise the tempo by the configured step. */
data class TrainerState(val streak: Int = 0, val runs: Int = 0, val bumps: Int = 0)

class MetronomeViewModel(private val c: AppContainer) : ViewModel() {
    val state = c.audio.state
    private val _trainer = MutableStateFlow(TrainerState())
    val trainer = _trainer.asStateFlow()
    private var droneWritten = 72
    private val adaptation = c.content.generatorConfig.adaptation

    fun toggle() { val s = state.value; c.audio.setMetronome(!s.metronomeOn, s.bpm) }
    fun setBpm(bpm: Int) = c.audio.setMetronome(state.value.metronomeOn, bpm)
    fun setBeats(n: Int) = c.audio.setBeatsPerBar(n)
    fun setSubdivision(n: Int) = c.audio.setSubdivision(n)
    fun setAccent(on: Boolean) = c.audio.setAccent(on)

    fun run(clean: Boolean) {
        val t = _trainer.value
        val streak = if (clean) t.streak + 1 else 0
        if (streak >= adaptation.cleanRunsForBump) {
            setBpm((state.value.bpm + adaptation.bpmStep).coerceAtMost(MetronomeGenerator.MAX_BPM))
            _trainer.value = TrainerState(0, t.runs + 1, t.bumps + 1)
        } else _trainer.value = t.copy(streak = streak, runs = t.runs + 1)
    }

    fun droneWritten() = droneWritten
    fun setDroneWritten(w: Int) {
        droneWritten = w
        if (state.value.droneMidi != null) c.audio.setDrone(LocalInstrument.writtenToConcertMidi(w))
    }
    fun toggleDrone() {
        c.audio.setDrone(if (state.value.droneMidi == null) LocalInstrument.writtenToConcertMidi(droneWritten) else null)
    }

    override fun onCleared() { c.audio.setMetronome(false); c.audio.setDrone(null) }
}

/** Only alto exists; kept as a single place to change when tenor arrives. */
private val LocalInstrument = app.supersax.domain.pitch.Instrument.ALTO_SAX

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomeScreen(onBack: () -> Unit) {
    val vm = appViewModel { MetronomeViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val trainer by vm.trainer.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.metronome_drone), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            MetronomeControls(s, vm::toggle, vm::setBpm)
            SectionCard(stringResource(R.string.metronome_options)) {
                Stepper(stringResource(R.string.beats_per_bar), s.beatsPerBar, vm::setBeats, 1..12)
                Text(stringResource(R.string.subdivision))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to R.string.sub_none, 2 to R.string.sub_eighths, 3 to R.string.sub_triplets, 4 to R.string.sub_sixteenths).forEach { (n, label) ->
                        FilterChip(selected = s.subdivision == n, onClick = { vm.setSubdivision(n) }, label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                FilterChip(selected = s.accent, onClick = { vm.setAccent(!s.accent) }, label = { Text(stringResource(R.string.accent_beat_one)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
            SectionCard(stringResource(R.string.tempo_trainer)) {
                Text(stringResource(R.string.tempo_trainer_hint), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.run(true) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.clean_run)) }
                    OutlinedButton(onClick = { vm.run(false) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.mistake)) }
                }
                Text(stringResource(R.string.trainer_status, trainer.runs, trainer.streak, trainer.bumps))
            }
            SectionCard(stringResource(R.string.drone)) {
                var w by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(vm.droneWritten()) }
                Stepper(stringResource(R.string.drone_note), w, { w = it; vm.setDroneWritten(it) }, 58..90) {
                    writtenNoteLabel(LocalSettings.current.instrument.writtenToConcertMidi(it))
                }
                Button(onClick = vm::toggleDrone, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (s.droneMidi != null) R.string.drone_stop else R.string.drone_start))
                }
                Text(stringResource(R.string.headphones_warning), style = MaterialTheme.typography.bodySmall)
            }
            s.error?.let { Text(stringResource(R.string.audio_output_error, it), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Start/stop and tempo; reused inside scale blocks. */
@Composable
fun MetronomeControls(s: OutputState, onToggle: () -> Unit, onBpm: (Int) -> Unit, compact: Boolean = false) {
    Text(stringResource(R.string.bpm_value, s.bpm), style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayMedium)
    Slider(
        value = s.bpm.toFloat(), onValueChange = { onBpm(it.toInt()) },
        valueRange = MetronomeGenerator.MIN_BPM.toFloat()..MetronomeGenerator.MAX_BPM.toFloat(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(-5, -1, +1, +5).forEach { d ->
            OutlinedButton(onClick = { onBpm(s.bpm + d) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (d > 0) "+$d" else "$d") }
        }
    }
    if (compact) Button(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(if (s.metronomeOn) R.string.metronome_stop else R.string.metronome_start))
    } else BigButton(stringResource(if (s.metronomeOn) R.string.metronome_stop else R.string.metronome_start), onToggle)
}
