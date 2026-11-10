package app.saxpractice.ui.session

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.saxpractice.R
import app.saxpractice.domain.planner.PlannedStep
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.ui.audio.HoldReportCard
import app.saxpractice.ui.audio.LiveTuner
import app.saxpractice.ui.audio.MetronomeControls
import app.saxpractice.ui.audio.MicErrorBanner
import app.saxpractice.ui.audio.TunerDisplay
import app.saxpractice.ui.audio.hasMicPermission
import app.saxpractice.ui.audio.rememberMicRequest
import app.saxpractice.ui.audio.saveHold
import app.saxpractice.ui.nav.LocalContainer
import androidx.compose.ui.platform.LocalContext

/** Metronome inside a scale block, set to the block's target tempo (follows an accepted bump). */
@Composable
fun MetronomeSlot(bpm: Int) {
    val c = LocalContainer.current
    val s by c.audio.state.collectAsStateWithLifecycle()
    LaunchedEffect(bpm) { c.audio.setMetronome(s.metronomeOn, bpm) }
    DisposableEffect(Unit) { onDispose { c.audio.setMetronome(false) } }
    MetronomeControls(s, { c.audio.setMetronome(!s.metronomeOn, s.bpm) }, { c.audio.setMetronome(s.metronomeOn, it) }, compact = true)
    if (s.metronomeOn) Text(stringResource(R.string.headphones_warning), style = MaterialTheme.typography.bodySmall)
}

/** Optional tuner inside a long-tone block; holds are saved to the tuner history. */
@Composable
fun TunerSlot(step: PlannedStep) {
    val c = LocalContainer.current
    val context = LocalContext.current
    var on by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tuner = remember { LiveTuner(scope, c.mic, { c.audio.a4 }) { c.saveHold(it) } }
    val request = rememberMicRequest { granted -> if (granted) { on = true; tuner.start(true) } }
    DisposableEffect(Unit) { onDispose { tuner.stop() } }
    LaunchedEffect(step.target.targetMidi) { tuner.targetMidi = step.target.targetMidi }
    if (!on) {
        OutlinedButton(onClick = request, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.show_tuner)) }
        return
    }
    val reading by tuner.reading.collectAsStateWithLifecycle()
    val mic by c.mic.state.collectAsStateWithLifecycle()
    MicErrorBanner(mic) { tuner.stop(); c.mic.retry(hasMicPermission(context)); tuner.start(hasMicPermission(context)) }
    TunerDisplay(reading, large = false)
    reading.lastReport?.let { HoldReportCard(it) }
    OutlinedButton(onClick = { tuner.stop(); on = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.hide_tuner)) }
}

/** Piece shown in new-material and play-for-fun blocks. */
@Composable
fun PiecePanel(step: PlannedStep) {
    if (step.exerciseType == ExerciseType.NEW_MATERIAL || step.exerciseType == ExerciseType.PLAY_FOR_FUN) {
        Text(stringResource(R.string.piece_free_choice), style = MaterialTheme.typography.bodyMedium)
    }
}
