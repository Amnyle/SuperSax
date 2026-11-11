package app.supersax.ui.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.common.DrillResultRecord
import app.supersax.domain.drills.ReadingDrill
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.notation.StaffLayout
import app.supersax.notation.StaffView
import app.supersax.ui.drills.NoteKeypad
import app.supersax.ui.drills.savePhoneDrill
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Best timed-reading score (right answers in 60 s) at [level]. */
fun bestSpeed(results: List<DrillResultRecord>, level: Int): Int? =
    results.filter { it.kind == DrillKind.READING_SPEED && it.level == level }.maxOfOrNull { it.correct }

data class SpeedUi(
    val note: SpelledPitch? = null,
    val secondsLeft: Int = SpeedReadingViewModel.SECONDS,
    val right: Int = 0,
    val wrong: Int = 0,
    /** Flash after an answer: true right, false wrong, null none. */
    val flash: Boolean? = null,
    val last: SpelledPitch? = null,
    val done: Boolean = false,
    val best: Int? = null,
    val newBest: Boolean = false,
)

/** 60 seconds: name as many notes as possible; each answer moves straight to the next note. */
class SpeedReadingViewModel(private val c: AppContainer, val level: Int) : ViewModel() {
    private val pool = ReadingDrill.round(level, 400, c.clock.nowMs())
    private var index = 0
    private val _ui = MutableStateFlow(SpeedUi(note = pool[0]))
    val ui = _ui.asStateFlow()
    private val startedAt = c.clock.nowMs()
    private var shownAt = c.clock.elapsedMs()
    private val times = mutableListOf<Long>()

    init {
        viewModelScope.launch {
            val best = bestSpeed(c.progress.drills.first(), level)
            _ui.value = _ui.value.copy(best = best)
            val end = c.clock.elapsedMs() + SECONDS * 1000L
            while (isActive) {
                val left = ((end - c.clock.elapsedMs() + 999) / 1000).toInt().coerceAtLeast(0)
                if (left != _ui.value.secondsLeft) _ui.value = _ui.value.copy(secondsLeft = left)
                if (left == 0) { finish(); break }
                delay(100)
            }
        }
    }

    fun answer(pc: PitchClass) {
        val u = _ui.value
        val q = u.note ?: return
        if (u.done) return
        val ok = ReadingDrill.isCorrect(q, pc)
        times += c.clock.elapsedMs() - shownAt
        index = (index + 1) % pool.size
        shownAt = c.clock.elapsedMs()
        _ui.value = u.copy(note = pool[index], right = u.right + if (ok) 1 else 0, wrong = u.wrong + if (ok) 0 else 1,
            flash = ok, last = if (ok) null else q)
    }

    private fun finish() {
        val u = _ui.value
        val newBest = u.right > 0 && (u.best == null || u.right > u.best)
        _ui.value = u.copy(done = true, newBest = newBest)
        val answered = u.right + u.wrong
        if (answered > 0) viewModelScope.launch {
            c.savePhoneDrill(DrillKind.READING_SPEED, level, answered, u.right, times.average().toLong(), startedAt)
        }
    }

    companion object { const val SECONDS = 60 }
}

@Composable
fun SpeedReadingScreen(level: Int, onClose: () -> Unit, onAgain: () -> Unit) {
    val vm = appViewModel(key = "speed-$level") { SpeedReadingViewModel(it, level) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val naming = LocalSettings.current.noteNaming
    var accidental by remember { mutableIntStateOf(0) }
    val good = Sax.colors.good
    val bad = MaterialTheme.colorScheme.error
    val flashColor by animateColorAsState(
        when (ui.flash) { true -> good.copy(alpha = 0.18f); false -> bad.copy(alpha = 0.18f); null -> Color.Transparent }, label = "flash",
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Sax.colors.textSecondary) }
            LinearProgressIndicator(
                progress = { ui.secondsLeft / SpeedReadingViewModel.SECONDS.toFloat() }, modifier = Modifier.weight(1f).height(12.dp).clip(CircleShape),
                color = if (ui.secondsLeft <= 10) Sax.colors.warn else Sax.colors.accent, trackColor = Sax.colors.elevated, drawStopIndicator = {},
            )
            Spacer(Modifier.size(12.dp))
            Icon(Icons.Rounded.Timer, null, tint = Sax.colors.textSecondary, modifier = Modifier.size(18.dp))
            Text(" ${ui.secondsLeft} s", style = MaterialTheme.typography.titleSmall)
        }
        if (ui.done) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.speed_score, ui.right), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
                Text(stringResource(R.string.speed_detail, ui.right, ui.right + ui.wrong), style = MaterialTheme.typography.titleMedium, color = Sax.colors.textSecondary)
                Text(
                    when {
                        ui.newBest -> stringResource(R.string.speed_new_best)
                        ui.best != null -> stringResource(R.string.speed_best, ui.best!!)
                        else -> ""
                    }, style = MaterialTheme.typography.titleLarge, color = if (ui.newBest) Sax.colors.accentInk else Sax.colors.textSecondary,
                )
                Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                    Text(stringResource(R.string.speed_again), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.level_back)) }
            }
            return@Column
        }
        Column(Modifier.weight(1f).fillMaxWidth().background(flashColor).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.speed_right, ui.right), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                ui.last?.let { Text(stringResource(R.string.it_was, naming.format(it.pitchClass)), color = bad, style = MaterialTheme.typography.titleSmall) }
            }
            ui.note?.let { StaffView(StaffLayout.singleNote(it), staffSpace = 14.dp, showTime = false, description = stringResource(R.string.staff_note_description)) }
            NoteKeypad(naming, accidental, true, { accidental = it }) { l -> vm.answer(PitchClass(l, accidental)); accidental = 0 }
        }
    }
}

/** The card on the note-reading ladder: start a 60-second run at an open level, with the best scores. */
@Composable
fun SpeedChallengeCard(results: List<DrillResultRecord>, openLevel: Int, onStart: (Int) -> Unit) {
    var level by remember(openLevel) { mutableIntStateOf(openLevel) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(app.supersax.ui.theme.CardRadius)).background(Sax.colors.onAccent).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, null, tint = Sax.colors.accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.speed_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(stringResource(R.string.speed_sub), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..openLevel).forEach { l ->
                val sel = l == level
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) Sax.colors.accent else Color.White.copy(alpha = 0.08f))
                        .clickable { level = l }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.level_short, l), style = MaterialTheme.typography.labelLarge, color = if (sel) Sax.colors.onAccent else Color.White)
                    Text(bestSpeed(results, l)?.toString() ?: "–", style = MaterialTheme.typography.titleMedium, color = if (sel) Sax.colors.onAccent else Sax.colors.accent)
                }
            }
        }
        Button(onClick = { onStart(level) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
            Text(stringResource(R.string.speed_start))
        }
    }
}
