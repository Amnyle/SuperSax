package app.supersax.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.data.db.KeyValueEntity
import app.supersax.domain.grades.Award
import app.supersax.domain.grades.ExamResult
import app.supersax.domain.grades.Grade
import app.supersax.domain.grades.GradeScale
import app.supersax.domain.grades.SaxGrades
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.scale.ScaleId
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.scaleLabel
import app.supersax.ui.components.writtenNoteLabel
import app.supersax.ui.nav.LocalContainer
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Exam results, kept in the key-value table (and so in backups). */
class GradeStore(private val c: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(ExamResult.serializer())
    val results: Flow<List<ExamResult>> = c.db.kv().observe(KEY).map { e -> e?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() } ?: emptyList() }
    suspend fun add(r: ExamResult) {
        val now = c.db.kv().get(KEY)?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() } ?: emptyList()
        c.db.kv().put(KeyValueEntity(KEY, json.encodeToString(ser, now + r)))
    }
    companion object { const val KEY = "grades:results" }
}

/** Concert scale key ("MAJOR:Eb") of a written exam scale, for the listener and the labels. */
fun concertKey(s: GradeScale, instrument: Instrument): String = ScaleId.fromWritten(PitchClass.parse(s.writtenTonic), s.type, instrument).key

@Composable
fun awardLabel(a: Award): String = stringResource(when (a) {
    Award.DISTINCTION -> R.string.gr_distinction
    Award.MERIT -> R.string.gr_merit
    Award.PASS -> R.string.gr_pass
    Award.FAIL -> R.string.gr_fail
})

/** The grade ladder: each grade with its best result, what its exam asks, and the exam button. */
@Composable
fun GradesScreen(onBack: () -> Unit, onExam: (Int) -> Unit) {
    val c = LocalContainer.current
    val store = androidx.compose.runtime.remember { GradeStore(c) }
    val results by store.results.collectAsStateWithLifecycle(emptyList())
    val open = SaxGrades.open(results)
    var shown by rememberSaveable { mutableIntStateOf(0) }
    if (shown == 0) shown = open
    ScreenScaffold(stringResource(R.string.gr_title), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.gr_intro, SaxGrades.PASS, SaxGrades.MERIT, SaxGrades.DISTINCTION), style = MaterialTheme.typography.bodyMedium,
                color = Sax.colors.textSecondary)
            SaxGrades.grades.forEach { g ->
                val best = SaxGrades.best(results, g.number)
                val locked = g.number > open
                SectionCard(modifier = Modifier.clickable { shown = g.number }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(when {
                            best != null && best.award != Award.FAIL -> Sax.colors.good.copy(alpha = 0.18f)
                            locked -> Sax.colors.elevated
                            else -> Sax.colors.accentSoft
                        }), contentAlignment = Alignment.Center) {
                            if (locked) Icon(Icons.Rounded.Lock, stringResource(R.string.locked), tint = Sax.colors.textTertiary)
                            else Text("${g.number}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.gr_grade_n, g.number), style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    best != null -> stringResource(R.string.gr_best, awardLabel(best.award), best.total)
                                    locked -> stringResource(R.string.gr_locked, g.number - 1)
                                    else -> stringResource(R.string.gr_not_taken)
                                },
                                style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary,
                            )
                        }
                    }
                    if (shown == g.number) {
                        GradeContents(g)
                        if (!locked) Button(onClick = { onExam(g.number) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                            Text(stringResource(if (best == null) R.string.gr_take else R.string.gr_retake))
                        }
                    }
                }
            }
            Text(stringResource(R.string.gr_note), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        }
    }
}

/** What the exam of [g] asks, part by part. */
@Composable
fun GradeContents(g: Grade) {
    val instrument = LocalSettings.current.instrument
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val notes = g.longToneNotes.map { writtenNoteLabel(instrument.writtenToConcertMidi(it)) }
        Line(stringResource(R.string.gr_sec_tone), stringResource(R.string.gr_tone_req,
            notes.joinToString(" & "), g.longToneSec, Math.round(g.longToneInTune * 100).toInt()))
        val names = g.scales.map { scaleLabel(concertKey(it, instrument)) }
        Line(stringResource(R.string.gr_sec_scales), stringResource(R.string.gr_scales_req, SaxGrades.SCALES_ASKED, g.scaleBpm) + "\n" +
            names.joinToString(", "))
        Line(stringResource(R.string.gr_sec_reading), stringResource(R.string.gr_reading_req, g.readingLevel))
        Line(stringResource(R.string.gr_sec_aural), stringResource(R.string.gr_aural_req, SaxGrades.AURAL_QUESTIONS, g.earLevel))
        Line(stringResource(R.string.gr_sec_theory), stringResource(R.string.gr_theory_req, SaxGrades.THEORY_QUESTIONS))
    }
}

@Composable
private fun Line(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Sax.colors.accentInk)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
