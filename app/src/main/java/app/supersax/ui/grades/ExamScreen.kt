package app.supersax.ui.grades

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.R
import app.supersax.domain.audio.RunDetector
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.SightReading
import app.supersax.domain.grades.Award
import app.supersax.domain.grades.ExamResult
import app.supersax.domain.grades.ExamSection
import app.supersax.domain.grades.Grade
import app.supersax.domain.grades.SaxGrades
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.notation.StaffView
import app.supersax.ui.audio.LiveTuner
import app.supersax.ui.audio.MicErrorBanner
import app.supersax.ui.audio.hasMicPermission
import app.supersax.ui.audio.rememberMicRequest
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.components.writtenNoteLabel
import app.supersax.ui.listen.upAndDown
import app.supersax.ui.longtone.TuningCircle
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.practice.DrillItem
import app.supersax.ui.practice.DrillItemView
import app.supersax.ui.practice.levelItems
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun sectionLabel(s: ExamSection): String = stringResource(when (s) {
    ExamSection.TONE -> R.string.gr_sec_tone
    ExamSection.SCALES -> R.string.gr_sec_scales
    ExamSection.SIGHT_READING -> R.string.gr_sec_reading
    ExamSection.AURAL -> R.string.gr_sec_aural
    ExamSection.THEORY -> R.string.gr_sec_theory
})

/**
 * A grade exam, part by part: tone (two long tones), scales (three, by ear, two tries each), sight-reading (a
 * melody followed note by note), aural and theory questions. Each part gives its marks; the total and the award
 * come at the end and are saved. Leaving in the middle cancels the exam.
 */
@Composable
fun ExamScreen(gradeNumber: Int, onBack: () -> Unit) {
    val g = SaxGrades.grade(gradeNumber)
    val c = LocalContainer.current
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val seed = remember { System.currentTimeMillis() }
    var section by remember { mutableIntStateOf(-1) }
    val marks = remember { mutableStateListOf<Int>() }
    var saved by remember { mutableStateOf<ExamResult?>(null) }
    val scope = rememberCoroutineScope()
    var micOk by remember { mutableStateOf(false) }
    val request = rememberMicRequest { granted -> micOk = granted; if (granted) section = 0 }
    BackHandler(section in 0 until ExamSection.entries.size) { onBack() }

    fun done(score: Double) {
        val s = ExamSection.entries[section]
        marks += SaxGrades.marks(s, score)
        if (section + 1 < ExamSection.entries.size) section++
        else {
            val r = ExamResult(g.number, System.currentTimeMillis(), ExamSection.entries.mapIndexed { i, e -> e.name to marks[i] }.toMap())
            scope.launch { GradeStore(c).add(r) }
            saved = r
            section = ExamSection.entries.size
        }
    }

    ScreenScaffold(stringResource(R.string.gr_exam_title, g.number), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (section in ExamSection.entries.indices) {
                LinearProgressIndicator(progress = { (section + 1f) / ExamSection.entries.size }, modifier = Modifier.fillMaxWidth())
                val s = ExamSection.entries[section]
                Text(stringResource(R.string.gr_part, section + 1, ExamSection.entries.size, sectionLabel(s), s.marks), style = MaterialTheme.typography.titleMedium)
            }
            when {
                section < 0 -> {
                    SectionCard(stringResource(R.string.gr_before_title)) {
                        Text(stringResource(R.string.gr_before), style = MaterialTheme.typography.bodyMedium)
                        GradeContents(g)
                    }
                    Button(onClick = request, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                        Text(stringResource(R.string.gr_start))
                    }
                }
                section >= ExamSection.entries.size -> saved?.let { ExamResultView(it, onBack) }
                else -> when (ExamSection.entries[section]) {
                    ExamSection.TONE -> ToneTest(g, ::done)
                    ExamSection.SCALES -> ScalesTest(g, seed, ::done)
                    ExamSection.SIGHT_READING -> ReadingTest(g, seed, ::done)
                    ExamSection.AURAL -> QuestionsTest(remember(seed) { levelItems(c, DrillKind.EAR, g.earLevel, SaxGrades.AURAL_QUESTIONS, seed) }, ::done)
                    ExamSection.THEORY -> QuestionsTest(remember(seed) {
                        val half = SaxGrades.THEORY_QUESTIONS / 2
                        (levelItems(c, DrillKind.SCALE_DEGREES, g.degreesLevel, half, seed) +
                            levelItems(c, DrillKind.SCALE_RECOGNITION, g.recognitionLevel, SaxGrades.THEORY_QUESTIONS - half, seed + 1)).shuffled(java.util.Random(seed))
                    }, ::done)
                }
            }
        }
    }
}

/** Two long tones on the grade's notes; each scored on held time and tuning. */
@Composable
private fun ToneTest(g: Grade, onDone: (Double) -> Unit) {
    val c = LocalContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val instrument = LocalSettings.current.instrument
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    val scores = remember { mutableStateListOf<Double>() }
    val tuner = remember { LiveTuner(scope, c.mic, { c.audio.a4 }, 10.0) }
    val target = instrument.writtenToConcertMidi(g.longToneNotes[index.coerceAtMost(g.longToneNotes.size - 1)])
    LaunchedEffect(target) { tuner.targetMidi = target }
    DisposableEffect(Unit) { tuner.start(hasMicPermission(context)); onDispose { tuner.stop() } }
    val reading by tuner.reading.collectAsStateWithLifecycle()
    val mic by c.mic.state.collectAsStateWithLifecycle()
    MicErrorBanner(mic) { tuner.stop(); c.mic.retry(hasMicPermission(context)); tuner.start(hasMicPermission(context)) }
    Text(stringResource(R.string.gr_tone_play, writtenNoteLabel(target), g.longToneSec), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.gr_tone_hint, Math.round(g.longToneInTune * 100).toInt()), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
    TuningCircle(reading, null, Modifier.fillMaxWidth().padding(horizontal = 40.dp))
    if (reading.holding) Text(stringResource(R.string.holding_for, reading.holdSec.roundToInt()), style = MaterialTheme.typography.titleMedium)
    // A hold on the right note (a wrong note or a blip does not count) is the try.
    LaunchedEffect(reading.lastReport) {
        val r = reading.lastReport ?: return@LaunchedEffect
        tuner.clearReport()
        if (r.refMidi != target || abs(r.meanCents) > 50 || r.stableSec < 1.5) return@LaunchedEffect
        scores += SaxGrades.toneScore(r.stableSec, r.inTuneShare, g)
        if (index + 1 < g.longToneNotes.size) index++ else onDone(scores.average())
    }
    scores.lastOrNull()?.let { Text(stringResource(R.string.gr_tone_done, (it * 100).roundToInt()), style = MaterialTheme.typography.bodyMedium) }
}

/** Three scales played by ear, two tries each; the best try counts. */
@Composable
private fun ScalesTest(g: Grade, seed: Long, onDone: (Double) -> Unit) {
    val c = LocalContainer.current
    val instrument = LocalSettings.current.instrument
    val scales = remember(seed) { SaxGrades.pickScales(g, seed) }
    var index by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }
    var lastMsg by remember { mutableStateOf<String?>(null) }
    val scores = remember { mutableStateListOf<Double>() }
    val item = scales[index.coerceAtMost(scales.size - 1)]
    val key = concertKey(item, instrument)
    DisposableEffect(Unit) { c.mic.start(true); onDispose { c.mic.stop() } }
    val clean = stringResource(R.string.gr_scale_clean)
    val wrong = stringResource(R.string.gr_scale_wrong)
    val short = stringResource(R.string.gr_scale_short)
    fun next(score: Double) {
        scores += score
        tries = 0
        if (index + 1 < scales.size) index++ else onDone(scores.average())
    }
    LaunchedEffect(key) {
        val detector = RunDetector(upAndDown(key), c.audio.a4)
        c.mic.frames.collect { f ->
            val run = detector.feed(f) ?: return@collect
            val second = tries == 1
            tries++
            val score = SaxGrades.scaleScore(run.clean, run.path.bpm, run.path.span, item, g, second)
            lastMsg = if (!run.clean) wrong else if (run.path.span < item.range) short.format(run.path.bpm?.roundToInt() ?: 0) else clean.format(run.path.bpm?.roundToInt() ?: 0)
            if (run.clean || tries >= SaxGrades.SCALE_TRIES) next(score)
        }
    }
    Text(stringResource(R.string.gr_scale_play, index + 1, scales.size, scaleLabel(key)), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.gr_scale_range, rangeLabel(item.range)), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.gr_scale_hint, g.notesPerMin(item), SaxGrades.SCALE_TRIES), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
    Text(stringResource(R.string.gr_try, (tries + 1).coerceAtMost(SaxGrades.SCALE_TRIES), SaxGrades.SCALE_TRIES), style = MaterialTheme.typography.bodyMedium)
    lastMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary) }
    TextButton(onClick = { next(0.0) }) { Text(stringResource(R.string.gr_skip)) }
}

/** A melody at the grade's reading level, followed note by note; a note first played wrong loses its mark. */
@Composable
private fun ReadingTest(g: Grade, seed: Long, onDone: (Double) -> Unit) {
    val c = LocalContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val instrument = LocalSettings.current.instrument
    val scope = rememberCoroutineScope()
    val melody: List<SpelledPitch> = remember(seed) { SightReading.melody(g.readingLevel, seed) + SightReading.melody(g.readingLevel, seed + 7) }
    val tuner = remember { LiveTuner(scope, c.mic, { c.audio.a4 }, 50.0) }
    var index by remember { mutableIntStateOf(0) }
    val wrong = remember { mutableStateListOf<Int>() }
    var stable by remember { mutableStateOf<Int?>(null) }
    var since by remember { mutableStateOf(0L) }
    DisposableEffect(Unit) { tuner.start(hasMicPermission(context)); onDispose { tuner.stop() } }
    val reading by tuner.reading.collectAsStateWithLifecycle()
    LaunchedEffect(reading) {
        if (index >= melody.size) return@LaunchedEffect
        val midi = reading.concertMidi?.takeIf { reading.frequencyHz != null } ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (midi != stable) { stable = midi; since = now }
        val held = now - since
        val target = instrument.writtenToConcertMidi(melody[index].midi)
        if (midi == target && held >= 150) {
            stable = null
            if (index + 1 >= melody.size) { index++; onDone(1.0 - wrong.size.toDouble() / melody.size) } else index++
        } else if (midi != target && held >= 300 && index !in wrong) wrong += index
    }
    Text(stringResource(R.string.gr_reading_play), style = MaterialTheme.typography.titleLarge)
    val shown = melody.chunked(SightReading.NOTES)
    shown.forEachIndexed { i, part ->
        StaffView(SightReading.score(part, 80), staffSpace = 10.dp, showTime = false)
    }
    Text(stringResource(R.string.gr_reading_progress, index.coerceAtMost(melody.size), melody.size, wrong.size), style = MaterialTheme.typography.bodyMedium)
    if (index < melody.size) Text(stringResource(R.string.gr_reading_next, writtenNoteLabel(instrument.writtenToConcertMidi(melody[index].midi))),
        style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
}

/** Questions answered on the phone, no feedback between them (as in an exam). */
@Composable
private fun QuestionsTest(items: List<DrillItem>, onDone: (Double) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    if (items.isEmpty()) { LaunchedEffect(Unit) { onDone(1.0) }; return }
    val item = items[index.coerceAtMost(items.size - 1)]
    Text(stringResource(R.string.gr_question, index + 1, items.size), style = MaterialTheme.typography.labelLarge, color = Sax.colors.textSecondary)
    DrillItemView(item, "exam-$index", !answered, { ok ->
        if (answered) return@DrillItemView
        answered = true
        if (ok) right++
    })
    LaunchedEffect(answered) {
        if (!answered) return@LaunchedEffect
        delay(500)
        answered = false
        if (index + 1 < items.size) index++ else onDone(right.toDouble() / items.size)
    }
}

/** Marks per part, the total and the award; a pass opens the next grade. */
@Composable
private fun ExamResultView(r: ExamResult, onBack: () -> Unit) {
    val medal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { medal.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    val passed = r.award != Award.FAIL
    val color = if (passed) Sax.colors.good else Sax.colors.warn
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(120.dp).scale(medal.value).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.WorkspacePremium, null, tint = color, modifier = Modifier.size(76.dp))
        }
        Text(awardLabel(r.award), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.gr_total, r.total), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(if (passed) (if (r.grade < SaxGrades.grades.size) R.string.gr_passed_next else R.string.gr_passed_last) else R.string.gr_failed,
            r.grade + 1, SaxGrades.PASS), style = MaterialTheme.typography.bodyMedium)
    }
    SectionCard(stringResource(R.string.gr_marks)) {
        ExamSection.entries.forEach { s ->
            val m = r.marks[s.name] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sectionLabel(s), Modifier.weight(1f))
                Text("$m / ${s.marks}", fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { m.toFloat() / s.marks }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = if (m * 100 >= s.marks * SaxGrades.PASS) Sax.colors.good else Sax.colors.warn)
        }
    }
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.gr_back_to_grades)) }
}
