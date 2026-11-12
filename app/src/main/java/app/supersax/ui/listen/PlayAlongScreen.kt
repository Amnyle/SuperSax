package app.supersax.ui.listen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.SightReading
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.notation.StaffView
import app.supersax.ui.audio.LiveTuner
import app.supersax.ui.audio.MicErrorBanner
import app.supersax.ui.audio.hasMicPermission
import app.supersax.ui.audio.rememberMicRequest
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.drills.savePhoneDrill
import app.supersax.ui.nav.LocalSettings
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.theme.Sax
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayAlongUi(
    val level: Int = 1,
    val melody: List<SpelledPitch> = emptyList(),
    val index: Int = 0,
    /** Notes played wrong before the right one (by index). */
    val wrongAt: Set<Int> = emptySet(),
    val heardWrong: SpelledPitch? = null,
    val done: Boolean = false,
    val seconds: Int = 0,
    val seed: Long = 1,
)

/**
 * Play-along sight-reading: the app follows the player note by note. A note counts when it is held in
 * tune (within 50 cents) for 0.15 s; a different note held for 0.3 s marks a mistake on that note.
 */
class PlayAlongViewModel(private val c: AppContainer) : ViewModel() {
    val tuner = LiveTuner(viewModelScope, c.mic, { c.audio.a4 }, 50.0)
    private val _ui = MutableStateFlow(PlayAlongUi())
    val ui = _ui.asStateFlow()
    val micState = c.mic.state
    private var startedAt = 0L
    private var stableMidi: Int? = null
    private var stableSince = 0L

    init {
        newMelody(1)
        viewModelScope.launch {
            tuner.reading.collect { r ->
                val u = _ui.value
                if (u.done || u.melody.isEmpty()) return@collect
                val now = c.clock.elapsedMs()
                val midi = r.concertMidi
                if (midi != stableMidi) { stableMidi = midi; stableSince = now }
                val held = now - stableSince
                val target = concert(u.melody[u.index])
                if (midi == null) return@collect
                if (startedAt == 0L) startedAt = now
                if (midi == target && kotlin.math.abs(r.cents) <= 50 && held >= 150) advance()
                else if (midi != target && held >= 300 && u.index !in u.wrongAt) {
                    _ui.value = u.copy(wrongAt = u.wrongAt + u.index, heardWrong = SpelledPitch.fromMidi(Instrument.ALTO_SAX.concertToWrittenMidi(midi)))
                }
            }
        }
    }

    private fun concert(p: SpelledPitch) = Instrument.ALTO_SAX.writtenToConcertMidi(p.midi)

    private fun advance() {
        val u = _ui.value
        stableSince = c.clock.elapsedMs() // the next note needs its own hold
        stableMidi = null
        if (u.index + 1 >= u.melody.size) {
            val secs = ((c.clock.elapsedMs() - startedAt) / 1000).toInt()
            _ui.value = u.copy(done = true, heardWrong = null, seconds = secs)
            tuner.targetMidi = null
            val right = u.melody.size - u.wrongAt.size
            viewModelScope.launch { c.savePhoneDrill(DrillKind.PLAY_ALONG, u.level, u.melody.size, right, 0, c.clock.nowMs() - secs * 1000L) }
        } else {
            _ui.value = u.copy(index = u.index + 1, heardWrong = null)
            tuner.targetMidi = concert(u.melody[u.index + 1])
        }
    }

    fun newMelody(level: Int = _ui.value.level) {
        val seed = _ui.value.seed + 1
        val melody = SightReading.melody(level, c.clock.nowMs() + seed)
        _ui.value = PlayAlongUi(level = level, melody = melody, seed = seed)
        startedAt = 0L
        tuner.targetMidi = concert(melody[0])
    }

    fun start(granted: Boolean) = tuner.start(granted)
    override fun onCleared() { tuner.stop() }
}

@Composable
fun PlayAlongScreen(onBack: () -> Unit) {
    val vm = appViewModel { PlayAlongViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val mic by vm.micState.collectAsStateWithLifecycle()
    val naming = LocalSettings.current.noteNaming
    val context = LocalContext.current
    val request = rememberMicRequest { vm.start(it) }
    LaunchedEffect(Unit) { request() }
    DisposableEffect(Unit) { onDispose { vm.tuner.stop() } }
    ScreenScaffold(stringResource(R.string.playalong_title), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.playalong_hint), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { l ->
                    FilterChip(selected = ui.level == l, onClick = { vm.newMelody(l) }, label = { Text(stringResource(R.string.level_short, l)) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            MicErrorBanner(mic) { vm.tuner.stop(); vm.start(hasMicPermission(context)) }
            SectionCard {
                StaffView(SightReading.score(ui.melody, 80), staffSpace = 11.dp, highlightIndex = if (ui.done) -1 else ui.index, showTime = true,
                    highlight = Sax.colors.accentInk)
                if (!ui.done) {
                    Text(stringResource(R.string.playalong_now, ui.index + 1, ui.melody.size), style = MaterialTheme.typography.titleMedium)
                    ui.heardWrong?.let { Text(stringResource(R.string.playalong_wrong, naming.format(it)), color = MaterialTheme.colorScheme.error) }
                } else {
                    val right = ui.melody.size - ui.wrongAt.size
                    Text(stringResource(R.string.playalong_done, right, ui.melody.size), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.playalong_time, ui.seconds), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
                }
            }
            Button(onClick = { vm.newMelody() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sax.colors.accent, contentColor = Sax.colors.onAccent)) {
                Text(stringResource(R.string.playalong_new), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
