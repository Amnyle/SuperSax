package app.supersax.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.audio.MelodyNote
import app.supersax.domain.drills.Answer
import app.supersax.domain.drills.EarDrill
import app.supersax.domain.drills.QuestionType
import app.supersax.domain.drills.RhythmDrill
import app.supersax.domain.library.Score
import app.supersax.domain.pitch.Instrument
import app.supersax.notation.StaffView
import app.supersax.ui.audio.LiveTuner
import app.supersax.ui.audio.rememberMicRequest
import app.supersax.ui.learn.AnswerInput
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun concert(written: Int) = Instrument.ALTO_SAX.writtenToConcertMidi(written)

/** Ear question: plays itself, then the player taps the answer (or plays the last note on the sax). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarView(item: DrillItem.Ear, key: Any, enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val c = LocalContainer.current
    val q = item.q
    fun play() {
        if (q.kind == EarDrill.Kind.CHORD) q.writtenMidi.forEach { c.audio.playTone(concert(it), 1600) }
        else c.audio.playMelody(q.writtenMidi.map { MelodyNote(concert(it), 1.0) }, 92)
    }
    LaunchedEffect(key) { delay(250); play() }
    val sax = item.sax && q.kind != EarDrill.Kind.CHORD
    Text(stringResource(when {
        sax && q.kind == EarDrill.Kind.MELODY -> R.string.ear_q_sax_melody
        sax -> R.string.ear_q_sax
        q.kind == EarDrill.Kind.DIRECTION -> R.string.ear_q_direction
        q.kind == EarDrill.Kind.INTERVAL -> R.string.ear_q_interval
        q.kind == EarDrill.Kind.CHORD -> R.string.ear_q_chord
        else -> R.string.ear_q_melody
    }), style = MaterialTheme.typography.titleLarge)
    FilledTonalButton(onClick = { play() }, modifier = Modifier.heightIn(min = 52.dp)) {
        Icon(Icons.Rounded.VolumeUp, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ear_play_again))
    }
    when {
        sax -> SaxAnswer(q.targetWrittenMidi, key, enabled, onAnswer)
        q.kind == EarDrill.Kind.MELODY ->
            AnswerInput(QuestionType.NOTES, "ear-$key", enabled) { a -> onAnswer(EarDrill.checkMelody(q, (a as Answer.Notes).values)) }
        else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            q.options.forEachIndexed { i, opt ->
                Button(
                    onClick = { onAnswer(i == q.answer) }, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accentSoft, contentColor = Sax.colors.text),
                ) { Text(earOptionLabel(q.kind, opt), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** Listens to the saxophone until the target written note is held in tune for a moment. */
@Composable
private fun SaxAnswer(targetWritten: Int, key: Any, enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val naming = LocalSettings.current.noteNaming
    val tuner = remember(key) { LiveTuner(scope, c.mic, { c.audio.a4 }, 40.0).also { it.targetMidi = concert(targetWritten) } }
    val request = rememberMicRequest { tuner.start(it) }
    LaunchedEffect(key) { request() }
    DisposableEffect(key) { onDispose { tuner.stop() } }
    val r by tuner.reading.collectAsStateWithLifecycle()
    var answered by remember(key) { mutableStateOf(false) }
    LaunchedEffect(r, enabled) {
        if (enabled && !answered && r.frequencyHz != null && kotlin.math.abs(r.cents) <= 40 && r.holdSec >= 0.3) { answered = true; onAnswer(true) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Mic, null, tint = Sax.colors.accentInk)
        Spacer(Modifier.width(8.dp))
        Text(
            r.concertMidi?.let { stringResource(R.string.ear_hearing, naming.format(app.supersax.domain.pitch.SpelledPitch.fromMidi(Instrument.ALTO_SAX.concertToWrittenMidi(it)))) }
                ?: stringResource(R.string.ear_listening),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (enabled) TextButton(onClick = { answered = true; onAnswer(false) }) { Text(stringResource(R.string.ear_give_up)) }
}

/** Rhythm question: the bar on the staff; Start counts in one bar, then the player taps the rhythm. */
@Composable
fun RhythmView(item: DrillItem.Rhythm, key: Any, enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val c = LocalContainer.current
    val q = item.q
    val bpm = RhythmDrill.bpm(item.level)
    val beatMs = 60000.0 / bpm
    val scope = rememberCoroutineScope()
    var phase by remember(key) { mutableIntStateOf(0) } // 0 ready, 1 count-in, 2 tap, 3 done
    var beat by remember(key) { mutableIntStateOf(0) }
    val taps = remember(key) { mutableStateListOf<Long>() }
    var startAt by remember(key) { mutableStateOf(0L) }
    DisposableEffect(key) { onDispose { c.audio.setMetronome(false) } }
    Text(stringResource(if (q.swing) R.string.rhythm_q_swing else R.string.rhythm_q), style = MaterialTheme.typography.titleLarge)
    StaffView(Score(0, 4, 4, bpm, listOf(Score.parseMeasure(q.measure()))), staffSpace = 12.dp, showTime = true)
    when (phase) {
        0 -> Button(
            onClick = {
                taps.clear(); phase = 1
                c.audio.setMetronome(true, bpm, 4, 1, true)
                startAt = android.os.SystemClock.elapsedRealtime()
                scope.launch {
                    for (b in 1..8) { beat = b; delay(beatMs.toLong()) }
                    delay(350)
                    c.audio.setMetronome(false)
                    phase = 3
                    val expected = q.onsets().map { it * beatMs / 4 }
                    val tapped = taps.map { (it - startAt - 4 * beatMs) }
                    onAnswer(RhythmDrill.judge(expected, tapped, RhythmDrill.toleranceMs(item.level)).correct)
                }
            },
            enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent),
        ) { Text(stringResource(R.string.rhythm_start, bpm), style = MaterialTheme.typography.titleMedium) }
        else -> {
            Text(
                if (phase == 1 && beat <= 4) stringResource(R.string.rhythm_count, beat.coerceAtLeast(1)) else stringResource(R.string.rhythm_tap_now),
                style = MaterialTheme.typography.headlineSmall, color = Sax.colors.accentInk,
            )
            Box(
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(28.dp)).background(Sax.colors.accent)
                    .pointerInput(key) { awaitEachGesture { awaitFirstDown(); if (phase == 1) taps += android.os.SystemClock.elapsedRealtime() } },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.rhythm_pad), style = MaterialTheme.typography.headlineMedium, color = Sax.colors.onAccent) }
        }
    }
    if (phase == 0) OutlinedButton(onClick = {
        // Hear the rhythm first: the bar played on one note after the count-in.
        c.audio.playMelody(rhythmMelody(q), bpm)
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.rhythm_listen)) }
}

/** The bar as a melody on concert B♭3 (written G4), rests as silences. */
private fun rhythmMelody(q: RhythmDrill.Question): List<MelodyNote> =
    q.durations.map { d -> MelodyNote(if (d > 0) 58 else null, kotlin.math.abs(d) / 4.0) }
