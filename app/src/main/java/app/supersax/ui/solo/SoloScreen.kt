package app.supersax.ui.solo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.audio.LoopPlayer
import app.supersax.data.db.KeyValueEntity
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.solo.Blues
import app.supersax.domain.solo.Groove
import app.supersax.ui.components.Overline
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.Stepper
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.CardRadius
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The guided steps, from the usual advice on learning to solo (see docs/DECISIONS.md). */
enum class SoloStep(val title: Int, val body: Int, val calls: Boolean = false, val playRest: Boolean = false) {
    FORM(R.string.solo_form, R.string.solo_form_body),
    THREE(R.string.solo_three, R.string.solo_three_body),
    CALL(R.string.solo_call, R.string.solo_call_body, calls = true),
    MINOR_PENTA(R.string.solo_minor_penta, R.string.solo_minor_penta_body),
    SCALE(R.string.solo_scale, R.string.solo_scale_body),
    MAJOR_PENTA(R.string.solo_major_penta, R.string.solo_major_penta_body),
    SPACE(R.string.solo_space, R.string.solo_space_body, playRest = true),
    TARGET(R.string.solo_target, R.string.solo_target_body),
    MOTIF(R.string.solo_motif, R.string.solo_motif_body),
    EAR(R.string.solo_ear, R.string.solo_ear_body),
}

data class SoloUi(
    val style: Groove.Style = Groove.Style.BLUES,
    val recording: Boolean = false,
    val takes: List<app.supersax.domain.common.RecordingRecord> = emptyList(),
    val playingTake: String? = null,
    val key: String = "Bb",
    val bpm: Int = 80,
    val step: SoloStep = SoloStep.FORM,
    val playing: Boolean = false,
    val preparing: Boolean = false,
    val bar: Int = 0,
    val done: Set<String> = emptySet(),
    val seed: Long = 1,
)

class SoloViewModel(private val c: AppContainer) : ViewModel() {
    private val player = LoopPlayer()
    private val recorder = app.supersax.audio.Recorder(c.context)
    private var takePlayer: android.media.MediaPlayer? = null
    private val _ui = MutableStateFlow(SoloUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val done = c.db.kv().get(DONE_KEY)?.json?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            _ui.value = _ui.value.copy(done = done)
        }
        viewModelScope.launch {
            c.media.recordings.collect { all -> _ui.value = _ui.value.copy(takes = all.filter { it.exerciseKey == TAKE_KEY }.take(5)) }
        }
        viewModelScope.launch {
            while (isActive) {
                val u = _ui.value
                if (u.playing) {
                    val bar = (player.positionSec() / (4 * 60.0 / u.bpm)).toInt().coerceIn(0, u.style.bars() - 1)
                    if (bar != u.bar) _ui.value = u.copy(bar = bar)
                }
                delay(40)
            }
        }
    }

    private fun render(u: SoloUi) {
        _ui.value = u.copy(preparing = true)
        viewModelScope.launch {
            val concert = PitchClass.parse(u.key)
            val bass = (concert.semitone + 36).let { if (it < 40) it + 12 else it }
            val calls = if (u.step.calls) (0 until u.style.bars() / 4).map { Blues.callPhrase(u.seed + it) } else null
            val samples = withContext(Dispatchers.Default) { Groove.render(u.style, bass, u.bpm.toDouble(), RATE, calls, bass + 24) }
            runCatching { player.play(samples, RATE, 1f) }
            _ui.value = _ui.value.copy(playing = true, preparing = false, bar = 0)
        }
    }

    fun toggle() { if (_ui.value.playing) stop() else render(_ui.value) }
    fun stop() { player.stop(); _ui.value = _ui.value.copy(playing = false, bar = 0) }

    private fun change(f: (SoloUi) -> SoloUi) {
        val u = f(_ui.value)
        _ui.value = u
        if (u.playing) render(u)
    }

    fun setKey(k: String) = change { it.copy(key = k) }
    fun setStyle(st: Groove.Style) = change { it.copy(style = st, bpm = st.defaultBpm, bar = 0) }

    /** Records a take over the backing: the backing restarts at bar 1 and the microphone records. */
    fun startTake() {
        if (_ui.value.recording) return
        val file = java.io.File(c.recordingsDir, "solo-${java.util.UUID.randomUUID()}.m4a")
        val ok = runCatching { recorder.start(file) }.isSuccess
        if (!ok) return
        _ui.value = _ui.value.copy(recording = true)
        render(_ui.value)
    }

    fun stopTake(styleName: String, keyName: String) {
        if (!_ui.value.recording) return
        val file = recorder.file
        val duration = runCatching { recorder.stop() }.getOrNull()
        stop()
        _ui.value = _ui.value.copy(recording = false)
        if (file != null && duration != null && duration > 1000) viewModelScope.launch {
            c.media.saveRecording(app.supersax.domain.common.RecordingRecord(java.util.UUID.randomUUID().toString(), c.clock.nowMs(), file.name, duration,
                exerciseKey = TAKE_KEY, note = "$styleName · $keyName · ${_ui.value.bpm} BPM"))
        }
    }

    fun playTake(r: app.supersax.domain.common.RecordingRecord) {
        takePlayer?.release(); takePlayer = null
        if (_ui.value.playingTake == r.id) { _ui.value = _ui.value.copy(playingTake = null); return }
        stop()
        takePlayer = runCatching {
            android.media.MediaPlayer().apply {
                setDataSource(c.media.fileOf(r).absolutePath); prepare(); start()
                setOnCompletionListener { _ui.value = _ui.value.copy(playingTake = null) }
            }
        }.getOrNull()
        _ui.value = _ui.value.copy(playingTake = if (takePlayer != null) r.id else null)
    }
    fun setBpm(b: Int) = change { it.copy(bpm = b) }
    fun setStep(s: SoloStep) = change { it.copy(step = s) }
    fun newPhrases() = change { it.copy(seed = it.seed + 3) }

    fun toggleDone(s: SoloStep) {
        val done = _ui.value.done.let { if (s.name in it) it - s.name else it + s.name }
        _ui.value = _ui.value.copy(done = done)
        viewModelScope.launch { c.db.kv().put(KeyValueEntity(DONE_KEY, done.joinToString(","))) }
    }

    override fun onCleared() {
        player.stop()
        runCatching { recorder.stop() }
        takePlayer?.release()
    }

    companion object {
        const val RATE = 32000
        const val DONE_KEY = "solo:done"
        const val TAKE_KEY = "solo"
    }
}

/** Blues jam with guided steps for learning to improvise. Everything shown is written for alto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoloScreen(onBack: () -> Unit, onSongPlayer: () -> Unit) {
    val vm = appViewModel { SoloViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val naming = LocalSettings.current.noteNaming
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    val written = Blues.altoWritten(PitchClass.parse(ui.key))
    ScreenScaffold(stringResource(R.string.solo_title), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.solo_intro), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
            // Style, key and tempo.
            SectionCard {
                Text(stringResource(R.string.solo_style), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Groove.Style.entries.forEach { st ->
                        FilterChip(selected = ui.style == st, onClick = { vm.setStyle(st) }, modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(styleName(st)) })
                    }
                }
                Text(stringResource(R.string.solo_key), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Blues.KEYS.forEach { k ->
                        val w = Blues.altoWritten(PitchClass.parse(k))
                        FilterChip(selected = ui.key == k, onClick = { vm.setKey(k) }, modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(stringResource(R.string.solo_key_chip, naming.format(w), naming.format(PitchClass.parse(k)))) })
                    }
                }
                Stepper(stringResource(R.string.solo_tempo), ui.bpm, vm::setBpm, 50..160, 5) { stringResource(R.string.bpm_value, it) }
                Button(
                    onClick = vm::toggle, enabled = !ui.preparing, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent),
                ) { Text(stringResource(if (ui.playing) R.string.solo_stop else R.string.solo_play), style = MaterialTheme.typography.titleMedium) }
                val style = styleName(ui.style)
                val keyLabel = naming.format(written)
                val askMic = app.supersax.ui.audio.rememberMicRequest { granted -> if (granted) vm.startTake() }
                OutlinedButton(
                    onClick = { if (ui.recording) vm.stopTake(style, keyLabel) else askMic() }, enabled = !ui.preparing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(if (ui.recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord, null,
                        tint = if (ui.recording) MaterialTheme.colorScheme.error else Sax.colors.accentInk)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (ui.recording) R.string.solo_take_stop else R.string.solo_take_start))
                }
                if (!ui.recording) Text(stringResource(R.string.solo_take_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
            // The bars with the chords, the current one in amber.
            SectionCard {
                (0 until ui.style.bars() / 4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until 4).forEach { col ->
                            val bar = row * 4 + col
                            val current = ui.playing && bar == ui.bar
                            val cue = cueOf(ui.step, bar)
                            Box(
                                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp))
                                    .background(when {
                                        current -> Sax.colors.accent
                                        cue == Cue.LISTEN || cue == Cue.REST -> Sax.colors.elevated
                                        else -> Sax.colors.accentSoft
                                    }),
                                contentAlignment = Alignment.Center,
                            ) {
                                val ch = ui.style.form[bar]
                                Text(naming.format(ch.root(written)) + ch.quality.suffix, style = MaterialTheme.typography.titleMedium,
                                    color = if (current) Sax.colors.onAccent else Sax.colors.text)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.solo_written_note), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
            // What to do right now.
            if (ui.playing) LiveCue(ui, written, naming)
            // Notes for this step.
            NotesCard(ui.step, written, naming)
            if (ui.takes.isNotEmpty()) SectionCard(stringResource(R.string.solo_takes)) {
                ui.takes.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(onClick = { vm.playTake(t) }) {
                            Icon(if (ui.playingTake == t.id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                stringResource(if (ui.playingTake == t.id) R.string.pause else R.string.play), tint = Sax.colors.accentInk)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t.note, style = MaterialTheme.typography.titleSmall)
                            Text(android.text.format.DateUtils.getRelativeTimeSpanString(t.atMs).toString() + " · " +
                                app.supersax.ui.components.formatClock(t.durationMs / 1000), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
                        }
                    }
                }
            }
            // The steps.
            Text(stringResource(R.string.solo_steps), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            SoloStep.entries.forEachIndexed { i, s ->
                val selected = s == ui.step
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(MaterialTheme.colorScheme.surface)
                        .clickable { vm.setStep(s) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(if (selected) Sax.colors.accent else Sax.colors.accentSoft), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = if (selected) Sax.colors.onAccent else Sax.colors.accentInk)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(s.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                        androidx.compose.material3.IconButton(onClick = { vm.toggleDone(s) }) {
                            Icon(if (s.name in ui.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                stringResource(R.string.solo_mark_done), tint = if (s.name in ui.done) Sax.colors.good else Sax.colors.textTertiary)
                        }
                    }
                    if (selected) {
                        Text(stringResource(s.body), style = MaterialTheme.typography.bodyMedium)
                        if (s.calls) TextButton(onClick = vm::newPhrases) {
                            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.solo_new_phrases))
                        }
                        if (s == SoloStep.EAR) OutlinedButton(onClick = onSongPlayer) { Text(stringResource(R.string.solo_open_song_player)) }
                    }
                }
            }
            Text(stringResource(R.string.solo_sources), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
        }
    }
}

enum class Cue { NONE, LISTEN, ANSWER, PLAY, REST }

/** What the player does in [bar] for this step: listen to the call or answer it; play or rest. */
fun cueOf(step: SoloStep, bar: Int): Cue {
    val first = bar % 4 < 2
    return when {
        step.calls -> if (first) Cue.LISTEN else Cue.ANSWER
        step.playRest -> if (first) Cue.PLAY else Cue.REST
        else -> Cue.NONE
    }
}

@Composable
private fun LiveCue(ui: SoloUi, written: PitchClass, naming: app.supersax.domain.pitch.NoteNaming) {
    val cue = cueOf(ui.step, ui.bar)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius)).background(Sax.colors.onAccent).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Overline(stringResource(R.string.solo_bar_of, ui.bar + 1, ui.style.bars()))
        if (cue != Cue.NONE) Text(
            stringResource(when (cue) { Cue.LISTEN -> R.string.solo_cue_listen; Cue.ANSWER -> R.string.solo_cue_answer; Cue.PLAY -> R.string.solo_cue_play; else -> R.string.solo_cue_rest }),
            style = MaterialTheme.typography.displaySmall, color = Sax.colors.accent,
        )
        val ch = ui.style.form[ui.bar.coerceIn(0, ui.style.bars() - 1)]
        Text(stringResource(R.string.solo_chord_now, naming.format(ch.root(written)) + ch.quality.suffix), style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
        if (ui.step == SoloStep.TARGET) Text(stringResource(R.string.solo_target_now, naming.format(ch.third(written))),
            style = MaterialTheme.typography.headlineMedium, color = Sax.colors.accent)
    }
}

@Composable
private fun NotesCard(step: SoloStep, written: PitchClass, naming: app.supersax.domain.pitch.NoteNaming) {
    val notes = when (step) {
        SoloStep.THREE -> Blues.threeNotes(written)
        SoloStep.MINOR_PENTA -> Blues.minorPentatonic(written)
        SoloStep.MAJOR_PENTA -> Blues.majorPentatonic(written)
        SoloStep.FORM, SoloStep.EAR -> emptyList()
        else -> Blues.scale(written)
    }
    if (notes.isEmpty()) return
    val blues = step != SoloStep.THREE && step != SoloStep.MINOR_PENTA && step != SoloStep.MAJOR_PENTA
    SectionCard(stringResource(when (step) {
        SoloStep.THREE -> R.string.solo_notes_three
        SoloStep.MINOR_PENTA -> R.string.scale_minor_pentatonic_title
        SoloStep.MAJOR_PENTA -> R.string.scale_major_pentatonic_title
        else -> R.string.solo_notes_scale
    })) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.forEachIndexed { i, n ->
                Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (blues && i == 3) Sax.colors.elevated else Sax.colors.accentSoft), contentAlignment = Alignment.Center) {
                    Text(naming.format(n), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        if (blues) Text(stringResource(R.string.solo_blue_note), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
    }
}

@Composable
fun styleName(st: Groove.Style): String = stringResource(
    when (st) {
        Groove.Style.BLUES -> R.string.style_blues
        Groove.Style.MINOR_BLUES -> R.string.style_minor_blues
        Groove.Style.SLOW_BLUES -> R.string.style_slow_blues
        Groove.Style.FUNK -> R.string.style_funk
        Groove.Style.JAZZ_251 -> R.string.style_jazz
    },
)
