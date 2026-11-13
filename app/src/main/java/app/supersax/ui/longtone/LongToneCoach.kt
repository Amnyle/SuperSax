package app.supersax.ui.longtone

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.longtone.DroneInterval
import app.supersax.domain.longtone.DroneSound
import app.supersax.domain.longtone.HoldFirstLatch
import app.supersax.domain.longtone.LongToneMeter
import app.supersax.ui.audio.LiveTuner
import app.supersax.ui.audio.MicErrorBanner
import app.supersax.ui.audio.TunerReading
import app.supersax.ui.audio.hasMicPermission
import app.supersax.ui.audio.rememberMicRequest
import app.supersax.ui.audio.saveHold
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.writtenNoteLabel
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.theme.InTuneGreen
import app.supersax.ui.theme.OffPitchAmber
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TOLERANCE = 10.0
private val OffRed = Color(0xFFD0573E)

/** Drone choices, kept on the device between visits. */
private class DronePrefs(context: Context) {
    private val p = context.getSharedPreferences("long_tone", Context.MODE_PRIVATE)
    var sound: DroneSound
        get() = runCatching { DroneSound.valueOf(p.getString("sound", null) ?: "WARM") }.getOrDefault(DroneSound.WARM)
        set(v) { p.edit().putString("sound", v.name).apply() }
    var intervals: Set<DroneInterval>
        get() = (p.getStringSet("intervals", null) ?: setOf("UNISON")).mapNotNull { runCatching { DroneInterval.valueOf(it) }.getOrNull() }.toSet()
        set(v) { p.edit().putStringSet("intervals", v.map { it.name }.toSet()).apply() }
    var volume: Float
        get() = p.getFloat("volume", 0.6f)
        set(v) { p.edit().putFloat("volume", v).apply() }
}

/** The long-tone coach on its own page (Training). */
@Composable
fun LongToneScreen(onBack: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    ScreenScaffold(stringResource(R.string.longtone_title), onBack = onBack) { padding ->
        ScrollColumn(padding) { LongToneCoach(targetMidi = null, autoStart = true) }
    }
}

/**
 * Tuning circle, hold timer, in-tune share, pitch + volume graph and a drone. With [targetMidi] (a session block)
 * cents are measured against that note and the drone can sound it; otherwise "Hold first" latches the first
 * steady note played. Holds are saved to the tuner history.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LongToneCoach(targetMidi: Int?, autoStart: Boolean, onStop: (() -> Unit)? = null) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { DronePrefs(context) }
    val tuner = remember { LiveTuner(scope, c.mic, { c.audio.a4 }, TOLERANCE) { c.saveHold(it) } }
    var listening by remember { mutableStateOf(false) }
    val request = rememberMicRequest { granted -> if (granted) { listening = true; tuner.start(true) } }
    LaunchedEffect(Unit) { if (autoStart) request() }
    LaunchedEffect(targetMidi) { tuner.targetMidi = targetMidi }

    val meter = remember { LongToneMeter(TOLERANCE, 0.3) }
    val latch = remember { HoldFirstLatch() }
    var stats by remember { mutableStateOf(Triple(0.0, null as Int?, 0.0)) }
    val pitch = remember { mutableStateListOf<Float>() }
    val volume = remember { mutableStateListOf<Float>() }

    var droneOn by remember { mutableStateOf(false) }
    var holdFirst by remember { mutableStateOf(targetMidi == null) }
    var latched by remember { mutableStateOf<Int?>(null) }
    var sound by remember { mutableStateOf(prefs.sound) }
    var intervals by remember { mutableStateOf(prefs.intervals) }
    var droneVolume by remember { mutableStateOf(prefs.volume) }
    var revision by remember { mutableIntStateOf(0) }

    val reading by tuner.reading.collectAsStateWithLifecycle()
    LaunchedEffect(reading) {
        val r = reading
        val midi = r.concertMidi?.takeIf { r.frequencyHz != null }
        meter.push(r.timeSec, midi, r.cents)
        stats = Triple(meter.holdSec, meter.inTunePercent, meter.bestSec)
        if (droneOn && holdFirst && latched == null) latch.push(r.timeSec, midi)?.let { latched = it }
        pitch += if (midi != null) r.cents.toFloat().coerceIn(-50f, 50f) else Float.NaN
        volume += ((LongToneMeter.decibels(r.rms) + 60) / 60).toFloat().coerceIn(0f, 1f)
        if (pitch.size > 300) pitch.removeAt(0)
        if (volume.size > 300) volume.removeAt(0)
    }

    // The drone follows the root (latched note or target), the chosen intervals and the sound.
    val root = if (holdFirst) latched else targetMidi
    LaunchedEffect(droneOn, root, sound, intervals, revision) {
        val notes = if (droneOn && root != null) DroneInterval.notes(root, intervals).toList() else emptyList()
        val sax = if (sound == DroneSound.SAX && notes.isNotEmpty()) withContext(Dispatchers.IO) { runCatching { c.band.altoSax }.getOrNull() } else null
        c.audio.setDroneChord(notes, sound, droneVolume, sax)
    }
    LaunchedEffect(droneVolume) { c.audio.setDroneChordVolume(droneVolume) }
    DisposableEffect(Unit) { onDispose { tuner.stop(); c.audio.setDroneChord(emptyList(), DroneSound.PURE, 0f, null) } }

    if (!listening) {
        OutlinedButton(onClick = request, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.show_coach)) }
        return
    }
    val mic by c.mic.state.collectAsStateWithLifecycle()
    MicErrorBanner(mic) { tuner.stop(); c.mic.retry(hasMicPermission(context)); tuner.start(hasMicPermission(context)) }

    TuningCircle(reading, stats.second, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat(stringResource(R.string.lt_hold), stringResource(R.string.lt_seconds, stats.first))
        Stat(stringResource(R.string.lt_in_tune), stats.second?.let { "$it %" } ?: "–")
        Stat(stringResource(R.string.lt_best), stringResource(R.string.lt_seconds, stats.third))
    }
    PitchVolumeGraph(pitch, volume)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.lt_graph_legend), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary, modifier = Modifier.weight(1f))
        TextButton(onClick = { meter.reset(); stats = Triple(0.0, null, 0.0); pitch.clear(); volume.clear() }) { Text(stringResource(R.string.lt_reset)) }
    }

    SectionCard(stringResource(R.string.lt_drone)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    !droneOn -> stringResource(R.string.lt_drone)
                    root != null -> stringResource(R.string.lt_drone_on, writtenNoteLabel(root))
                    else -> stringResource(R.string.lt_drone_wait)
                },
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            )
            Switch(droneOn, { droneOn = it; if (it && holdFirst) { latch.reset(); latched = null } })
        }
        if (droneOn) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(holdFirst, { holdFirst = true; latch.reset(); latched = null }, { Text(stringResource(R.string.lt_drone_hold_first)) })
                if (targetMidi != null) FilterChip(!holdFirst, { holdFirst = false }, { Text(stringResource(R.string.lt_drone_target)) })
                if (holdFirst && latched != null) TextButton(onClick = { latch.reset(); latched = null }) { Text(stringResource(R.string.lt_drone_reset)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DroneInterval.UNISON to R.string.lt_unison, DroneInterval.MAJOR_THIRD to R.string.lt_third, DroneInterval.FIFTH to R.string.lt_fifth).forEach { (i, label) ->
                    FilterChip(i in intervals, {
                        intervals = if (i in intervals) intervals - i else intervals + i
                        prefs.intervals = intervals
                    }, { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.lt_sound), style = MaterialTheme.typography.labelLarge, color = Sax.colors.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DroneSound.PURE to R.string.lt_sound_pure, DroneSound.WARM to R.string.lt_sound_warm,
                    DroneSound.ORGAN to R.string.lt_sound_organ, DroneSound.SAX to R.string.lt_sound_sax).forEach { (s, label) ->
                    FilterChip(sound == s, { sound = s; prefs.sound = s }, { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.lt_volume), style = MaterialTheme.typography.labelLarge, color = Sax.colors.textSecondary)
            Slider(droneVolume, { droneVolume = it }, onValueChangeFinished = { prefs.volume = droneVolume }, valueRange = 0.05f..1f)
            Text(stringResource(R.string.lt_headphones), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        }
    }
    if (onStop != null) OutlinedButton(onClick = { tuner.stop(); listening = false; droneOn = false; onStop() }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.hide_tuner))
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 88.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Sax.colors.textSecondary)
    }
}

private fun centsColor(cents: Double): Color = when {
    abs(cents) <= TOLERANCE -> InTuneGreen
    abs(cents) <= 25 -> OffPitchAmber
    else -> OffRed
}

/**
 * The big circle: an arc from the top that grows clockwise when sharp and anticlockwise when flat (full half
 * circle at 50 cents), the disc tinted by how much of the hold is in tune, and the note, cents, Hz and dB inside.
 */
@Composable
fun TuningCircle(reading: TunerReading, inTunePercent: Int?, modifier: Modifier = Modifier) {
    val voiced = reading.frequencyHz != null && reading.concertMidi != null
    val cents = if (voiced) reading.cents else 0.0
    val color = if (voiced) centsColor(cents) else Sax.colors.textTertiary
    val track = Sax.colors.elevated
    val note = if (voiced) writtenNoteLabel(reading.concertMidi!!) else "–"
    val desc = if (voiced) stringResource(R.string.lt_circle_desc, note, cents.roundToInt()) else stringResource(R.string.lt_play)
    val fill = (inTunePercent ?: 0) / 100f
    Box(modifier.aspectRatio(1f).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val stroke = size.minDimension * 0.07f
            val r = size.minDimension / 2 - stroke
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(2 * r, 2 * r)
            drawCircle(InTuneGreen.copy(alpha = 0.06f + 0.22f * fill), r - stroke / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            // In-tune zone around the top.
            val zone = (TOLERANCE / 50.0 * 180).toFloat()
            drawArc(InTuneGreen.copy(alpha = 0.35f), -90f - zone, 2 * zone, false, topLeft, arcSize, style = Stroke(stroke))
            if (voiced) {
                val sweep = (cents.coerceIn(-50.0, 50.0) / 50.0 * 180).toFloat()
                drawArc(color, -90f, if (abs(sweep) < 2f) (if (sweep < 0) -2f else 2f) else sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(note, fontSize = 64.sp, fontWeight = FontWeight.Light, color = Sax.colors.text)
            Text(
                if (voiced) "%+d¢".format(cents.roundToInt()) else stringResource(R.string.lt_play),
                style = MaterialTheme.typography.titleLarge, color = if (voiced) color else Sax.colors.textSecondary,
            )
            if (voiced) Text(
                stringResource(R.string.lt_hz_db, reading.frequencyHz!!, LongToneMeter.decibels(reading.rms).roundToInt()),
                style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary,
            )
        }
    }
}

/** Pitch (cents line, gaps when silent) over a shaded volume area, last few seconds. */
@Composable
fun PitchVolumeGraph(pitch: List<Float>, volume: List<Float>) {
    val line = Sax.colors.accentInk
    val vol = Sax.colors.textTertiary.copy(alpha = 0.25f)
    val band = InTuneGreen.copy(alpha = 0.14f)
    val desc = stringResource(R.string.lt_graph_desc)
    Canvas(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(Sax.colors.elevated).semantics { contentDescription = desc }) {
        val dx = size.width / 299f
        if (volume.size > 1) {
            val area = Path()
            area.moveTo(0f, size.height)
            volume.forEachIndexed { i, v -> area.lineTo(i * dx, size.height - v * size.height * 0.9f) }
            area.lineTo((volume.size - 1) * dx, size.height); area.close()
            drawPath(area, vol)
        }
        val mid = size.height / 2
        val scale = size.height / 100f
        drawRect(band, Offset(0f, mid - TOLERANCE.toFloat() * scale), Size(size.width, 2 * TOLERANCE.toFloat() * scale))
        drawLine(InTuneGreen.copy(alpha = 0.5f), Offset(0f, mid), Offset(size.width, mid), 2f)
        var path: Path? = null
        pitch.forEachIndexed { i, v ->
            if (v.isNaN()) { path?.let { drawPath(it, line, style = Stroke(4f)) }; path = null; return@forEachIndexed }
            val x = i * dx; val y = mid - v * scale
            val p = path ?: Path().also { it.moveTo(x, y); path = it }
            p.lineTo(x, y)
        }
        path?.let { drawPath(it, line, style = Stroke(4f)) }
    }
}
