package app.supersax.ui.listen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.supersax.R
import app.supersax.domain.audio.HoldReport
import app.supersax.domain.audio.RunDetector
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.scale.ScaleGenerator
import app.supersax.domain.scale.ScaleId
import app.supersax.ui.audio.TunerReading
import app.supersax.ui.audio.rememberMicRequest
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.Sax
import kotlin.math.roundToInt

/** Concert notes of a scale played up and down one octave. */
fun upAndDown(scaleKey: String): List<Int> {
    val up = ScaleGenerator.oneOctave(ScaleId.parse(scaleKey), 4).map { it.midi }
    return up + up.dropLast(1).reversed()
}

/**
 * Scale check by ear: while on, the microphone cuts the playing into runs (a pause ends a run); each run
 * is compared with the scale up and down and logged as clean or as a mistake, with the first wrong note.
 */
@Composable
fun ScaleListenSlot(scaleKey: String, onRun: (Boolean, Double?) -> Unit) {
    val c = LocalContainer.current
    val naming = LocalSettings.current.noteNaming
    var on by rememberSaveable { mutableStateOf(false) }
    var last by remember { mutableStateOf<RunDetector.Run?>(null) }
    val request = rememberMicRequest { granted -> if (granted) on = true }
    if (!on) {
        OutlinedButton(onClick = request, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Rounded.Hearing, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.listen_runs_on))
        }
        return
    }
    DisposableEffect(Unit) { c.mic.start(true); onDispose { c.mic.stop() } }
    LaunchedEffect(scaleKey) {
        val detector = RunDetector(upAndDown(scaleKey), c.audio.a4)
        c.mic.frames.collect { f -> detector.feed(f)?.let { run -> last = run; onRun(run.clean, run.path.bpm) } }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sax.colors.elevated).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.listen_runs_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        last?.let { run ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (run.clean) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null,
                    tint = if (run.clean) Sax.colors.good else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                val p = run.path
                val root = written(run.expected[0], naming)
                Text(
                    when (p.problem) {
                        app.supersax.domain.audio.ScalePath.Problem.NONE ->
                            if (p.bpm != null) stringResource(R.string.path_clean_bpm, p.notes, p.bpm!!.roundToInt()) else stringResource(R.string.path_clean, p.notes)
                        app.supersax.domain.audio.ScalePath.Problem.TOO_SHORT -> stringResource(R.string.listen_run_short)
                        app.supersax.domain.audio.ScalePath.Problem.WRONG_START -> stringResource(R.string.path_start, written(p.played, naming), root)
                        app.supersax.domain.audio.ScalePath.Problem.NOT_IN_SCALE -> stringResource(R.string.path_not_in_scale, p.index + 1, written(p.played, naming))
                        app.supersax.domain.audio.ScalePath.Problem.NOT_NEIGHBOUR -> stringResource(R.string.path_jump, p.index + 1, written(p.before, naming), written(p.played, naming))
                        app.supersax.domain.audio.ScalePath.Problem.NO_OCTAVE -> stringResource(R.string.path_octave, root)
                        app.supersax.domain.audio.ScalePath.Problem.NOT_HOME -> stringResource(R.string.path_home, written(p.played, naming), written(p.before, naming))
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
    OutlinedButton(onClick = { on = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.listen_runs_off)) }
}

private fun written(concertMidi: Int, naming: app.supersax.domain.pitch.NoteNaming) =
    naming.format(SpelledPitch.fromMidi(Instrument.ALTO_SAX.concertToWrittenMidi(concertMidi)))

/** The last seconds of pitch as a line inside the in-tune band (± tolerance), for long tones. */
@Composable
fun PitchTrace(reading: TunerReading, tolerance: Double = 10.0) {
    val points = remember { mutableStateListOf<Float>() }
    LaunchedEffect(reading) {
        if (reading.frequencyHz != null) { points += reading.cents.toFloat().coerceIn(-50f, 50f); if (points.size > 300) points.removeAt(0) }
    }
    val good = Sax.colors.good
    val line = Sax.colors.accentInk
    val band = Sax.colors.good.copy(alpha = 0.14f)
    val desc = stringResource(R.string.trace_desc)
    Canvas(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Sax.colors.elevated).semantics { contentDescription = desc }) {
        val mid = size.height / 2
        val scale = size.height / 100f
        drawRect(band, Offset(0f, mid - tolerance.toFloat() * scale), Size(size.width, 2 * tolerance.toFloat() * scale))
        drawLine(good.copy(alpha = 0.5f), Offset(0f, mid), Offset(size.width, mid), 2f)
        if (points.size > 1) {
            val p = Path()
            val dx = size.width / 299f
            points.forEachIndexed { i, v -> val x = i * dx; val y = mid - v * scale; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, line, style = Stroke(4f))
        }
    }
}

/** Steadiness of the last hold, 0 to 100: share of the hold in tune, less a penalty for drift. */
fun steadiness(r: HoldReport): Int = (r.inTuneShare * 100 - kotlin.math.abs(r.driftCents) * 0.5).roundToInt().coerceIn(0, 100)

@Composable
fun SteadinessCard(r: HoldReport) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.coach_steadiness), style = MaterialTheme.typography.labelLarge, color = Sax.colors.textSecondary)
            Text("${steadiness(r)}", style = MaterialTheme.typography.displaySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.coach_hold_line, r.stableSec.roundToInt(), (r.inTuneShare * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.coach_drift_line, r.driftCents.roundToInt()), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        }
    }
}
