package app.saxpractice.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.saxpractice.R
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.components.Stepper
import app.saxpractice.ui.components.writtenNoteLabel
import app.saxpractice.ui.nav.LocalSettings
import app.saxpractice.ui.nav.appViewModel

@Composable
fun TunerScreen(onBack: () -> Unit, onHistory: () -> Unit) {
    val vm = appViewModel { TunerViewModel(it) }
    val reading by vm.tuner.reading.collectAsStateWithLifecycle()
    val mic by vm.micState.collectAsStateWithLifecycle()
    val out by vm.output.collectAsStateWithLifecycle()
    val target by vm.target.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val request = rememberMicRequest { vm.start(it) }
    LaunchedEffect(Unit) { request() }
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val instrument = LocalSettings.current.instrument

    ScreenScaffold(stringResource(R.string.tuner), onBack = onBack, actions = {
        TextButton(onClick = onHistory) { Text(stringResource(R.string.per_note_history)) }
    }) { padding ->
        ScrollColumn(padding) {
            MicErrorBanner(mic) { vm.retry(hasMicPermission(context)) }
            TunerDisplay(reading)
            SectionCard(stringResource(R.string.tuner_target)) {
                if (target == null) {
                    Text(stringResource(R.string.tuner_target_auto), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { vm.setTarget(instrument.writtenToConcertMidi(72)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.tuner_set_target))
                    }
                } else {
                    val t = target!!
                    Stepper(stringResource(R.string.target_note), instrument.concertToWrittenMidi(t), { w -> vm.setTarget(instrument.writtenToConcertMidi(w)) },
                        instrument.writtenLowMidi..instrument.writtenHighMidi + 6) { writtenNoteLabel(instrument.writtenToConcertMidi(it)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = vm::playReference, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.play_reference)) }
                        FilledTonalButton(onClick = vm::toggleDrone, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (out.droneMidi != null) R.string.drone_stop else R.string.drone_start))
                        }
                        TextButton(onClick = { vm.setTarget(null) }) { Text(stringResource(R.string.clear)) }
                    }
                    if (out.droneMidi != null) Text(stringResource(R.string.headphones_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            reading.lastReport?.let { HoldReportCard(it) }
            Text(stringResource(R.string.tuner_privacy), style = MaterialTheme.typography.bodySmall)
        }
    }
}
