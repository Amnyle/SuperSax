package app.supersax.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.text.drawText
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun play() {
        scope.launch { playEar(c, q) }
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

/** Rhythm question: after a count-in, press and hold for each note, and let go at its end. */
@Composable
fun RhythmView(item: DrillItem.Rhythm, key: Any, enabled: Boolean, onAnswer: (Boolean) -> Unit) {
    val c = LocalContainer.current
    val q = item.q
    val bpm = RhythmDrill.bpm(item.level)
    val beatMs = 60000.0 / bpm
    val spans = remember(key) { q.spans() }
    val scope = rememberCoroutineScope()
    // READY, DEMO (listening), RUN (count-in and bar), DONE
    var phase by remember(key) { mutableStateOf(RhythmPhase.READY) }
    var t0 by remember(key) { mutableStateOf(0L) }          // uptime of beat 1 of the bar
    var now by remember(key) { mutableStateOf(0L) }
    val presses = remember(key) { mutableStateListOf<DoubleArray>() } // {down, up} ms from beat 1; up = NaN while held
    var result by remember(key) { mutableStateOf<RhythmDrill.HoldResult?>(null) }
    val hold = MIDI_HOLD
    DisposableEffect(key) { onDispose { c.audio.stopHold(); c.audio.stopAll() } }
    LaunchedEffect(phase) {
        while (phase == RhythmPhase.DEMO || phase == RhythmPhase.RUN) {
            now = android.os.SystemClock.uptimeMillis()
            val end = t0 + (4.5 * beatMs).toLong()
            if (now >= end) {
                if (phase == RhythmPhase.RUN) {
                    c.audio.stopHold()
                    val ms = (now - t0).toDouble()
                    val done = presses.map { if (it[1].isNaN()) doubleArrayOf(it[0], ms) else it }
                    val r = RhythmDrill.judgeHolds(spans.map { doubleArrayOf(it[0] * beatMs, it[1] * beatMs) }, done, RhythmDrill.toleranceMs(item.level), beatMs)
                    presses.clear(); presses.addAll(done)
                    result = r
                    phase = RhythmPhase.DONE
                    onAnswer(r.correct)
                } else phase = RhythmPhase.READY
                break
            }
            withFrameMillis { }
        }
    }
    fun start(demo: Boolean) {
        scope.launch {
            val sax = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { c.band.altoSax }
            val clip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                app.supersax.domain.audio.Clips.rhythm(if (demo) sax else null, c.audio.sampleRate, bpm, spans, 4, hold, c.audio.a4)
            }
            presses.clear(); result = null
            c.audio.playClip(clip)
            t0 = android.os.SystemClock.uptimeMillis() + (4 * beatMs).toLong()
            now = android.os.SystemClock.uptimeMillis()
            phase = if (demo) RhythmPhase.DEMO else RhythmPhase.RUN
        }
    }

    Text(stringResource(if (q.swing) R.string.rhythm_q_swing else R.string.rhythm_q), style = MaterialTheme.typography.titleLarge)
    StaffView(Score(0, 4, 4, bpm, listOf(Score.parseMeasure(q.measure()))), staffSpace = 12.dp, showTime = true)
    val beatNow = if (phase == RhythmPhase.DEMO || phase == RhythmPhase.RUN) (now - t0) / beatMs else null
    RhythmTimeline(spans, presses.map { doubleArrayOf(it[0] / beatMs, it[1] / beatMs) }, beatNow, result, q.durations.any { kotlin.math.abs(it) % 4 != 0 })
    // What to do now: the count-in numbers, then "hold", or the verdict.
    val status = when {
        beatNow != null && beatNow < 0 -> "${(beatNow + 5).toInt().coerceIn(1, 4)}"
        phase == RhythmPhase.DEMO -> stringResource(R.string.rhythm_listening)
        phase == RhythmPhase.RUN -> stringResource(R.string.rhythm_play_now)
        result != null -> result!!.firstWrong.let { i -> if (i < 0) stringResource(R.string.rhythm_all_good) else verdictText(i + 1, result!!.verdicts[i]) }
            .let { if (result!!.extra > 0 && result!!.firstWrong < 0) stringResource(R.string.rhythm_extra) else it }
        else -> stringResource(R.string.rhythm_ready, bpm)
    }
    Text(status, style = if (beatNow != null && beatNow < 0) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
        color = if (result?.correct == false) MaterialTheme.colorScheme.error else Sax.colors.accentInk,
        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    if (phase == RhythmPhase.READY && enabled) {
        Button(
            onClick = { start(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent),
        ) { Text(stringResource(R.string.rhythm_start, bpm), style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(onClick = { start(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.rhythm_listen)) }
    }
    val active = phase == RhythmPhase.RUN
    val held = presses.lastOrNull()?.get(1)?.isNaN() == true
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(28.dp))
            .background(when { !active -> Sax.colors.elevated; held -> Sax.colors.accentInk; else -> Sax.colors.accent })
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (phase == RhythmPhase.RUN) {
                        presses += doubleArrayOf((android.os.SystemClock.uptimeMillis() - t0).toDouble(), Double.NaN)
                        c.audio.startHold(c.band.altoSax, hold)
                    }
                    waitForUpOrCancellation()
                    val last = presses.lastOrNull()
                    if (last != null && last[1].isNaN() && phase == RhythmPhase.RUN) {
                        presses[presses.size - 1] = doubleArrayOf(last[0], (android.os.SystemClock.uptimeMillis() - t0).toDouble())
                        c.audio.stopHold()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.rhythm_pad), style = MaterialTheme.typography.headlineMedium,
            color = if (active) Sax.colors.onAccent else Sax.colors.textTertiary)
    }
}

private enum class RhythmPhase { READY, DEMO, RUN, DONE }

/** Concert pitch of the held note: B♭3 (written G4 for alto). */
private const val MIDI_HOLD = 58

@Composable
private fun verdictText(note: Int, v: RhythmDrill.Verdict): String = stringResource(when (v) {
    RhythmDrill.Verdict.EARLY -> R.string.rhythm_v_early
    RhythmDrill.Verdict.LATE -> R.string.rhythm_v_late
    RhythmDrill.Verdict.SHORT -> R.string.rhythm_v_short
    RhythmDrill.Verdict.LONG -> R.string.rhythm_v_long
    RhythmDrill.Verdict.MISSED -> R.string.rhythm_v_missed
    RhythmDrill.Verdict.OK -> R.string.rhythm_all_good
}, note)

/**
 * The bar as a timeline: beats 1–4 with a grid, each note as a block as long as it lasts (top lane), what the
 * player held (bottom lane), and the moving cursor. After the bar, blocks turn green or red.
 */
@Composable
private fun RhythmTimeline(spans: List<DoubleArray>, presses: List<DoubleArray>, beatNow: Double?, result: RhythmDrill.HoldResult?, eighths: Boolean) {
    val accent = Sax.colors.accent
    val soft = Sax.colors.accentSoft
    val good = Sax.colors.good
    val bad = MaterialTheme.colorScheme.error
    val grid = Sax.colors.elevated
    val ink = Sax.colors.text
    val label = Sax.colors.textSecondary
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = label)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(112.dp)) {
        val w = size.width
        val laneH = size.height * 0.3f
        val topY = size.height * 0.2f
        val botY = topY + laneH + size.height * 0.08f
        fun x(b: Double) = (b / 4.0 * w).toFloat()
        // grid and beat numbers
        for (k in 0..(if (eighths) 8 else 4)) {
            val b = if (eighths) k / 2.0 else k.toDouble()
            drawLine(grid, androidx.compose.ui.geometry.Offset(x(b), topY - 4), androidx.compose.ui.geometry.Offset(x(b), botY + laneH),
                strokeWidth = if (k % (if (eighths) 2 else 1) == 0) 3f else 1.5f)
            if (b < 4) {
                val txt = if (eighths && k % 2 == 1) "+" else "${b.toInt() + 1}"
                drawText(measurer, txt, androidx.compose.ui.geometry.Offset(x(b) + 6f, 0f), labelStyle)
            }
        }
        // the notes
        spans.forEachIndexed { i, s ->
            val color = when (result?.verdicts?.getOrNull(i)) { null -> soft; RhythmDrill.Verdict.OK -> good; else -> bad }
            drawRoundRect(color, androidx.compose.ui.geometry.Offset(x(s[0]) + 3f, topY), androidx.compose.ui.geometry.Size(x(s[1]) - x(s[0]) - 6f, laneH),
                androidx.compose.ui.geometry.CornerRadius(12f))
        }
        // what was held (presses are in beats here)
        presses.forEach { p ->
            val end = if (p[1].isNaN()) (beatNow ?: p[0]) else p[1]
            val a = p[0].coerceIn(-0.2, 4.4); val b = end.coerceIn(-0.2, 4.4)
            if (b > a) drawRoundRect(accent, androidx.compose.ui.geometry.Offset(x(a), botY), androidx.compose.ui.geometry.Size(x(b) - x(a), laneH * 0.7f),
                androidx.compose.ui.geometry.CornerRadius(10f))
        }
        // cursor
        if (beatNow != null && beatNow in 0.0..4.4) {
            drawLine(ink, androidx.compose.ui.geometry.Offset(x(beatNow), topY - 6), androidx.compose.ui.geometry.Offset(x(beatNow), botY + laneH), strokeWidth = 5f)
        }
    }
}

/** Plays an ear question on the recorded piano (the tone synth if the sound file is missing). */
suspend fun playEar(c: app.supersax.AppContainer, q: EarDrill.Question) {
    val piano = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { c.band.piano }
    val a4 = c.audio.a4
    if (piano == null) {
        if (q.kind == EarDrill.Kind.CHORD) q.writtenMidi.forEach { c.audio.playTone(concert(it), 1600) }
        else c.audio.playMelody(q.writtenMidi.map { MelodyNote(concert(it), 1.0) }, 92)
        return
    }
    val clip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        if (q.kind == EarDrill.Kind.CHORD) app.supersax.domain.audio.Clips.chord(piano, c.audio.sampleRate, q.writtenMidi.map { concert(it) }, 1.8, a4)
        else app.supersax.domain.audio.Clips.melody(piano, c.audio.sampleRate, q.writtenMidi.map { MelodyNote(concert(it), 1.0) }, 92, a4)
    }
    c.audio.playClip(clip)
}
