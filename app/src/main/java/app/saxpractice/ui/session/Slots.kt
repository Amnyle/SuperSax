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
import kotlinx.coroutines.launch
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

/** Piece of a new-material or play-for-fun block: its section on the staff, Listen, and the stage buttons. */
@Composable
fun PiecePanel(step: PlannedStep) {
    if (step.exerciseType != ExerciseType.NEW_MATERIAL && step.exerciseType != ExerciseType.PLAY_FOR_FUN) return
    val c = LocalContainer.current
    val language = app.saxpractice.LocaleHelper.effective(LocalContext.current)
    val piece = c.libraryService.piece(step.target.pieceId)
    val score = piece?.score
    if (piece == null || score == null) {
        Text(stringResource(R.string.piece_free_choice), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val section = piece.file.sections.firstOrNull { it.id == step.target.sectionId }
    val shown = section?.let { s -> score.copy(measures = score.measures.subList((s.from - 1).coerceAtLeast(0), s.to.coerceAtMost(score.measures.size)), pickup = score.pickup && s.from == 1) } ?: score
    val out by c.audio.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val instrument = app.saxpractice.ui.nav.LocalSettings.current.instrument
    Text(piece.file.title.get(language) + (section?.let { " · " + it.title.get(language) } ?: ""), style = MaterialTheme.typography.titleMedium)
    app.saxpractice.notation.StaffView(shown.toWritten(instrument), staffSpace = 10.dp)
    OutlinedButton(onClick = {
        if (out.melodyPlaying) c.audio.stopMelody() else c.audio.playMelody(app.saxpractice.ui.library.melodyOf(shown).first, score.tempo)
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(if (out.melodyPlaying) R.string.stop else R.string.listen)) }
    val progress by c.library.progress.collectAsStateWithLifecycle(emptyList())
    val sid = section?.id ?: ""
    val stage = progress.firstOrNull { it.pieceId == piece.id && it.sectionId == sid }?.stage ?: app.saxpractice.domain.common.PieceStage.NOT_STARTED
    app.saxpractice.ui.library.StageRow(section?.title?.get(language) ?: stringResource(R.string.whole_piece), stage) { st ->
        scope.launch { c.libraryService.setStage(piece.id, sid, st, c.clock.nowMs()) }
    }
}
