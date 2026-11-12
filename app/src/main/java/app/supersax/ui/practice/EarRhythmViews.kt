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

/**
 * Rhythm question: read the bar, then after a 4-click count-in tap each note as it starts (hold the long ones) while
 * the bar goes by twice; the better pass counts. Taps land as dots on a beat ruler, coloured by timing.
 */
@Composable
fun RhythmView(item: DrillItem.Rhythm, key: Any, enabled: Boolean, onAnswer: (Boolean) -> Unit, onMetric: (Long) -> Unit = {}) {
    val c = LocalContainer.current
    val q = item.q
    val bpm = RhythmDrill.bpm(item.level)
    val beatMs = 60000.0 / bpm
    val barMs = 4 * beatMs
    val spans = remember(key) { q.spans() }
    val spansMs = remember(key) { spans.map { doubleArrayOf(it[0] * beatMs, it[1] * beatMs) } }
    val tol = RhythmDrill.toleranceMs(item.level)
    val scope = rememberCoroutineScope()
    var phase by remember(key) { mutableStateOf(RhythmPhase.READY) }
    var t0 by remember(key) { mutableStateOf(0L) }      // uptime of beat 1 of the first pass
    var exact by remember(key) { mutableStateOf(false) } // t0 measured from the speaker, not estimated
    var now by remember(key) { mutableStateOf(0L) }
    val presses = remember(key) { mutableStateListOf<LongArray>() } // {down, up} uptime; up = 0 while held
    var result by remember(key) { mutableStateOf<RhythmDrill.TapScore?>(null) }
    var bestPass by remember(key) { mutableIntStateOf(0) }
    var prior by remember { mutableStateOf(0.0) }
    val reps = 2
    LaunchedEffect(Unit) { prior = c.db.kv().get(LATENCY_KEY)?.json?.toDoubleOrNull() ?: 0.0 }
    DisposableEffect(key) { onDispose { c.audio.stopHold(); c.audio.stopAll() } }

    fun passPresses(pass: Int): List<DoubleArray> {
        val base = t0 + (pass * barMs).toLong()
        return presses.filter { val d = it[0] - base; d >= -beatMs * 0.5 && d < barMs - beatMs * 0.5 + (if (pass == reps - 1) beatMs else 0.0) }
            .map { doubleArrayOf((it[0] - base).toDouble(), if (it[1] == 0L) Double.NaN else (it[1] - base).toDouble()) }
    }

    LaunchedEffect(phase) {
        val countIn = 4
        while (phase == RhythmPhase.DEMO || phase == RhythmPhase.RUN) {
            now = android.os.SystemClock.uptimeMillis()
            if (!exact) {
                val heard = c.audio.clipStartUptimeMs()
                if (heard > 0) { t0 = heard + (countIn * beatMs).toLong(); exact = true }
            }
            val passes = if (phase == RhythmPhase.RUN) reps else 1
            if (now >= t0 + (passes * barMs + beatMs * 0.6).toLong()) {
                if (phase == RhythmPhase.RUN) {
                    c.audio.stopHold()
                    val scores = (0 until reps).map { RhythmDrill.scoreTaps(spansMs, passPresses(it), tol, beatMs, prior) }
                    val best = scores.indices.maxByOrNull { scores[it].percent }!!
                    val r = scores[best]
                    bestPass = best
                    result = r
                    phase = RhythmPhase.DONE
                    // Learn this phone's delay a little from each good run.
                    if (r.notes.count { !it.errorMs.isNaN() } >= 3) {
                        val learned = (0.7 * prior + 0.3 * r.offsetMs).coerceIn(-250.0, 350.0)
                        c.appScope.launch { c.db.kv().put(app.supersax.data.db.KeyValueEntity(LATENCY_KEY, learned.toString())) }
                    }
                    onMetric(r.meanErrorMs.toLong().coerceAtMost(999))
                    onAnswer(r.passed)
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
                app.supersax.domain.audio.Clips.rhythm(if (demo) sax else null, c.audio.sampleRate, bpm, spans, 4, if (demo) 1 else reps, MIDI_HOLD, c.audio.a4)
            }
            presses.clear(); result = null; exact = false
            c.audio.playClip(clip)
            // Until the speaker reports when the clip is heard, assume a typical output delay.
            t0 = android.os.SystemClock.uptimeMillis() + 120 + (4 * beatMs).toLong()
            now = android.os.SystemClock.uptimeMillis()
            phase = if (demo) RhythmPhase.DEMO else RhythmPhase.RUN
        }
    }

    Text(stringResource(if (q.swing) R.string.rhythm_q_swing else R.string.rhythm_q), style = MaterialTheme.typography.titleLarge)
    StaffView(Score(0, 4, 4, bpm, listOf(Score.parseMeasure(q.measure()))), staffSpace = 12.dp, showTime = true)
    if (item.level <= 4 || phase == RhythmPhase.DONE) Text(RhythmDrill.counting(q), style = MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        color = Sax.colors.accentInk, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    val elapsed = (now - t0).toDouble()
    val running = phase == RhythmPhase.RUN || phase == RhythmPhase.DEMO
    val pass = if (running && elapsed >= 0) (elapsed / barMs).toInt() else 0
    val cursor = if (running && elapsed >= 0) (elapsed - pass * barMs) / beatMs else null
    // Taps of the pass to show: the current one while playing, the better one after.
    val shownPass = if (phase == RhythmPhase.DONE) bestPass else pass.coerceAtMost(reps - 1)
    val shownTaps = if (phase == RhythmPhase.RUN || phase == RhythmPhase.DONE) passPresses(shownPass) else emptyList()
    RhythmRuler(
        spans = spans, showNotes = phase == RhythmPhase.DEMO || phase == RhythmPhase.DONE || item.level <= 2,
        taps = shownTaps.map { (it[0] - prior) / beatMs }, tapColors = shownTaps.map { tapQuality(it[0] - prior, spansMs, tol) },
        cursor = cursor, result = result, eighths = q.durations.any { kotlin.math.abs(it) % 4 != 0 },
    )
    val status = when {
        running && elapsed < 0 -> "${((elapsed + 4 * beatMs) / beatMs).toInt().coerceIn(0, 3) + 1}"
        phase == RhythmPhase.DEMO -> stringResource(R.string.rhythm_listening)
        phase == RhythmPhase.RUN -> stringResource(R.string.rhythm_pass, pass + 1, reps)
        result != null -> rhythmVerdict(result!!)
        else -> stringResource(R.string.rhythm_ready, bpm)
    }
    Text(status, style = if (running && elapsed < 0) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
        color = if (result?.passed == false) MaterialTheme.colorScheme.error else Sax.colors.accentInk,
        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    if (phase == RhythmPhase.READY && enabled) {
        Button(
            onClick = { start(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent),
        ) { Text(stringResource(R.string.rhythm_start, bpm), style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(onClick = { start(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.rhythm_listen)) }
    }
    val active = phase == RhythmPhase.RUN
    val held = presses.lastOrNull()?.get(1) == 0L
    Box(
        Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(28.dp))
            .background(when { !active -> Sax.colors.elevated; held -> Sax.colors.accentInk; else -> Sax.colors.accent })
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (phase == RhythmPhase.RUN) {
                        presses += longArrayOf(down.uptimeMillis, 0L)
                        c.audio.startHold(c.bandIfReady()?.altoSax, MIDI_HOLD)
                    }
                    val up = waitForUpOrCancellation()
                    val i = presses.indexOfLast { it[1] == 0L }
                    if (i >= 0) {
                        presses[i] = longArrayOf(presses[i][0], up?.uptimeMillis ?: android.os.SystemClock.uptimeMillis())
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

private const val LATENCY_KEY = "rhythm:latency"

private enum class RhythmPhase { READY, DEMO, RUN, DONE }

/** Concert pitch of the held note: B♭3 (written G4 for alto). */
private const val MIDI_HOLD = 58

/** 2 on time, 1 a little off, 0 off (relative to the nearest note start). */
private fun tapQuality(tapMs: Double, spansMs: List<DoubleArray>, tol: Double): Int {
    val d = spansMs.minOf { kotlin.math.abs(tapMs - it[0]) }
    return if (d <= tol * 0.5) 2 else if (d <= tol) 1 else 0
}

@Composable
private fun rhythmVerdict(r: RhythmDrill.TapScore): String {
    val i = r.firstWrong
    val pct = r.percent.toInt()
    return when {
        i < 0 && r.extra > 0 -> stringResource(R.string.rhythm_extra_pct, pct)
        i < 0 -> stringResource(R.string.rhythm_score_ok, pct, r.meanErrorMs.toInt())
        else -> {
            val n = r.notes[i]
            when (n.verdict) {
                RhythmDrill.Verdict.MISSED -> stringResource(R.string.rhythm_v_missed_pct, i + 1, pct)
                RhythmDrill.Verdict.EARLY -> stringResource(R.string.rhythm_v_early_ms, i + 1, -n.errorMs.toInt(), pct)
                RhythmDrill.Verdict.LATE -> stringResource(R.string.rhythm_v_late_ms, i + 1, n.errorMs.toInt(), pct)
                else -> stringResource(R.string.rhythm_v_short_pct, i + 1, pct)
            }
        }
    }
}

/**
 * One bar as a ruler: beats 1–4 (and "+" for eighths), the notes as bars as long as they last (when shown),
 * the taps as dots (green on time, amber a little off, red off), and the moving cursor.
 */
@Composable
private fun RhythmRuler(spans: List<DoubleArray>, showNotes: Boolean, taps: List<Double>, tapColors: List<Int>, cursor: Double?,
                        result: RhythmDrill.TapScore?, eighths: Boolean) {
    val soft = Sax.colors.accentSoft
    val good = Sax.colors.good
    val warn = Sax.colors.warn
    val bad = MaterialTheme.colorScheme.error
    val grid = Sax.colors.elevated
    val ink = Sax.colors.text
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = Sax.colors.textSecondary)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val w = size.width
        val top = size.height * 0.28f
        val laneH = size.height * 0.34f
        val dotY = top + laneH + size.height * 0.2f
        fun x(b: Double) = (b / 4.0 * w).toFloat().coerceIn(0f, w)
        for (k in 0..(if (eighths) 8 else 4)) {
            val b = if (eighths) k / 2.0 else k.toDouble()
            val major = !eighths || k % 2 == 0
            drawLine(grid, androidx.compose.ui.geometry.Offset(x(b), top - 4), androidx.compose.ui.geometry.Offset(x(b), size.height), strokeWidth = if (major) 3f else 1.5f)
            if (b < 4) drawText(measurer, if (major) "${b.toInt() + 1}" else "+", androidx.compose.ui.geometry.Offset(x(b) + 6f, 0f), labelStyle)
        }
        if (showNotes) spans.forEachIndexed { i, s ->
            val color = when (result?.notes?.getOrNull(i)?.verdict) { null -> soft; RhythmDrill.Verdict.OK -> good; else -> bad }
            drawRoundRect(color, androidx.compose.ui.geometry.Offset(x(s[0]) + 3f, top), androidx.compose.ui.geometry.Size((x(s[1]) - x(s[0]) - 6f).coerceAtLeast(6f), laneH),
                androidx.compose.ui.geometry.CornerRadius(12f))
        }
        taps.forEachIndexed { i, b ->
            if (b < -0.6 || b > 4.6) return@forEachIndexed
            val color = when (tapColors.getOrNull(i)) { 2 -> good; 1 -> warn; else -> bad }
            drawCircle(color, 11f, androidx.compose.ui.geometry.Offset(x(b), dotY))
        }
        if (cursor != null && cursor in 0.0..4.0) {
            drawLine(ink, androidx.compose.ui.geometry.Offset(x(cursor), top - 6), androidx.compose.ui.geometry.Offset(x(cursor), size.height), strokeWidth = 5f)
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
