package app.supersax.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.supersax.R
import app.supersax.domain.audio.HoldReport
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.concertNoteLabel
import app.supersax.ui.components.writtenNoteLabel
import app.supersax.ui.theme.InTuneGreen
import app.supersax.ui.theme.OffPitchAmber
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** "+12¢ sharp", "−8¢ flat", "in tune". */
@Composable
fun centsText(cents: Double, tolerance: Double = 10.0): String {
    val c = cents.roundToInt()
    return when {
        abs(cents) <= tolerance / 2 -> stringResource(R.string.cents_in_tune, if (c >= 0) "+$c" else "−${-c}")
        cents > 0 -> stringResource(R.string.cents_sharp, c)
        else -> stringResource(R.string.cents_flat, -c)
    }
}

/** Big written note, small concert note, needle and readout. */
@Composable
fun TunerDisplay(reading: TunerReading, tolerance: Double = 10.0, large: Boolean = true) {
    val midi = reading.concertMidi
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (midi != null) writtenNoteLabel(midi) else "–",
            fontSize = if (large) 72.sp else 40.sp, fontWeight = FontWeight.Light,
        )
        if (midi != null) Text(stringResource(R.string.concert_name, concertNoteLabel(midi)), style = MaterialTheme.typography.bodyMedium)
        CentsNeedle(if (reading.frequencyHz != null) reading.cents else null, tolerance, Modifier.fillMaxWidth().height(if (large) 140.dp else 90.dp))
        Text(
            if (reading.frequencyHz != null) centsText(reading.cents, tolerance) else stringResource(R.string.tuner_play_a_note),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { },
        )
        if (reading.holding) Text(stringResource(R.string.holding_for, reading.holdSec.roundToInt()), style = MaterialTheme.typography.bodyMedium)
    }
}

/** A half-dial from −50 to +50 cents with the in-tune zone shaded. Null cents = no needle. */
@Composable
fun CentsNeedle(cents: Double?, tolerance: Double, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val desc = cents?.let { stringResource(R.string.cents_description, it.roundToInt()) } ?: stringResource(R.string.tuner_play_a_note)
    Canvas(modifier.semantics { contentDescription = desc }) {
        val cx = size.width / 2
        val cy = size.height * 0.95f
        val r = minOf(size.width / 2, size.height) * 0.9f
        fun angle(c: Double) = Math.toRadians(-90 + c / 50.0 * 70)
        val zoneStart = Math.toDegrees(angle(-tolerance)).toFloat()
        val zoneSweep = (Math.toDegrees(angle(tolerance)) - Math.toDegrees(angle(-tolerance))).toFloat()
        drawArc(InTuneGreen.copy(alpha = 0.25f), zoneStart, zoneSweep, useCenter = true, topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r))
        drawArc(outline, Math.toDegrees(angle(-50.0)).toFloat(), 140f, useCenter = false, topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r), style = Stroke(3f))
        listOf(-50, -25, 0, 25, 50).forEach { t ->
            val a = angle(t.toDouble())
            drawLine(outline, Offset(cx + (r * 0.9f * cos(a)).toFloat(), cy + (r * 0.9f * sin(a)).toFloat()),
                Offset(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat()), strokeWidth = 3f)
        }
        if (cents != null) {
            val a = angle(cents.coerceIn(-50.0, 50.0))
            val col = if (abs(cents) <= tolerance) InTuneGreen else OffPitchAmber
            drawLine(col, Offset(cx, cy), Offset(cx + (r * 0.95f * cos(a)).toFloat(), cy + (r * 0.95f * sin(a)).toFloat()), strokeWidth = 8f, cap = StrokeCap.Round)
        }
    }
}

/** Pitch trace over time with the ±tolerance band. */
@Composable
fun TraceChart(trace: List<Float>, traceHz: Double, tolerance: Double, modifier: Modifier = Modifier.fillMaxWidth().height(140.dp)) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val desc = stringResource(R.string.trace_description, tolerance.roundToInt())
    Canvas(modifier.semantics { contentDescription = desc }) {
        val range = maxOf(30.0, (trace.maxOfOrNull { abs(it.toDouble()) } ?: 0.0) + 5).toFloat()
        fun y(c: Float) = size.height / 2 - c / range * size.height / 2
        drawRect(InTuneGreen.copy(alpha = 0.18f), Offset(0f, y(tolerance.toFloat())), Size(size.width, y(-tolerance.toFloat()) - y(tolerance.toFloat())))
        drawLine(axis, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.5f)
        if (trace.size >= 2) {
            val path = Path()
            trace.forEachIndexed { i, c ->
                val x = i / (trace.size - 1f) * size.width
                if (i == 0) path.moveTo(x, y(c)) else path.lineTo(x, y(c))
            }
            drawPath(path, line, style = Stroke(4f))
        }
    }
}

/** The hold report: trace, "in tune for 9 of 12 s", "ends 22¢ flat". */
@Composable
fun HoldReportCard(r: HoldReport) {
    SectionCard(stringResource(R.string.hold_report_title, writtenNoteLabel(r.refMidi))) {
        TraceChart(r.trace, r.traceHz, r.toleranceCents)
        Text(stringResource(R.string.hold_in_tune_for, r.inTuneSec.roundToInt(), r.stableSec.roundToInt(), r.toleranceCents.roundToInt()))
        Text(stringResource(R.string.hold_ends, centsText(r.endCents, r.toleranceCents)))
        Text(stringResource(R.string.hold_stats, signed(r.meanCents), signed(r.driftCents), signed(r.minCents), signed(r.maxCents)), style = MaterialTheme.typography.bodySmall)
    }
}

fun signed(v: Double): String { val c = v.roundToInt(); return if (c > 0) "+$c" else if (c < 0) "−${-c}" else "0" }

