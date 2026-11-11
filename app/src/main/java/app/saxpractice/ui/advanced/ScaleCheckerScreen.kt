package app.saxpractice.ui.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.R
import app.saxpractice.domain.audio.PitchFrame
import app.saxpractice.domain.audio.ScaleCheck
import app.saxpractice.domain.audio.ScaleChecker
import app.saxpractice.domain.common.RunRecord
import app.saxpractice.domain.scale.ScaleGenerator
import app.saxpractice.domain.scale.ScaleId
import app.saxpractice.ui.audio.MicErrorBanner
import app.saxpractice.ui.audio.hasMicPermission
import app.saxpractice.ui.audio.rememberMicRequest
import app.saxpractice.ui.components.BigButton
import app.saxpractice.ui.components.ScalePickerDialog
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.components.Stepper
import app.saxpractice.ui.components.scaleLabel
import app.saxpractice.ui.nav.appViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CheckerUi(val scaleKey: String? = null, val bpm: Int = 60, val listening: Boolean = false, val result: ScaleCheck? = null, val logged: Boolean = false)

/** Experimental: listens to a scale, segments the notes, compares, and only logs after the player confirms. */
class ScaleCheckerViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CheckerUi())
    val ui = _ui.asStateFlow()
    val micState = c.mic.state
    private val frames = mutableListOf<PitchFrame>()
    private var job: Job? = null
    private var startedAt = 0L

    init {
        // Default to the scale of the running session's current step, if any.
        val step = c.sessions.run.value?.step
        _ui.value = CheckerUi(step?.target?.scaleKey, step?.target?.bpm ?: 60)
    }

    fun setScale(k: String) { _ui.value = _ui.value.copy(scaleKey = k, result = null, logged = false) }
    fun setBpm(b: Int) { _ui.value = _ui.value.copy(bpm = b) }

    /** Expected notes: the scale over the player's range, up and down (concert). */
    private suspend fun expected(key: String): List<Int> {
        val p = c.practice.playerNow()
        val inst = c.settings.current().instrument
        return ScaleGenerator.fullRangeUpDown(ScaleId.parse(key), inst.writtenToConcertMidi(p.writtenLowMidi), inst.writtenToConcertMidi(p.writtenHighMidi)).map { it.midi }
    }

    fun start(granted: Boolean) {
        if (!granted || _ui.value.scaleKey == null) return
        frames.clear()
        startedAt = c.clock.nowMs()
        c.mic.start(true)
        job = viewModelScope.launch { c.mic.frames.collect { frames += it } }
        _ui.value = _ui.value.copy(listening = true, result = null, logged = false)
    }

    fun stop() {
        job?.cancel(); job = null
        if (_ui.value.listening) c.mic.stop()
        val key = _ui.value.scaleKey ?: return
        viewModelScope.launch {
            val result = ScaleChecker(a4 = c.audio.a4).check(frames.toList(), expected(key))
            _ui.value = _ui.value.copy(listening = false, result = result)
        }
    }

    /** The player confirms what the run was; nothing is written before this. */
    fun confirm(clean: Boolean) {
        val u = _ui.value
        val key = u.scaleKey ?: return
        viewModelScope.launch {
            c.sessions.logRuns(listOf(RunRecord(key, u.bpm, clean)), ((c.clock.nowMs() - startedAt) / 1000).toInt().coerceAtLeast(1))
            _ui.value = _ui.value.copy(logged = true)
        }
    }

    override fun onCleared() { if (job != null) { job?.cancel(); c.mic.stop() } }
}

@Composable
fun ScaleCheckerScreen(onBack: () -> Unit) {
    val vm = appViewModel { ScaleCheckerViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val mic by vm.micState.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val request = rememberMicRequest { vm.start(it) }
    DisposableEffect(Unit) { onDispose { if (vm.ui.value.listening) vm.stop() } }
    ScreenScaffold(stringResource(R.string.scale_checker), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.experimental_label), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.scale_checker_hint), style = MaterialTheme.typography.bodySmall)
            MicErrorBanner(mic) { request() }
            TextButton(onClick = { pick = true }) { Text(ui.scaleKey?.let { scaleLabel(it) } ?: stringResource(R.string.pick_scale)) }
            Stepper(stringResource(R.string.bpm_logged), ui.bpm, vm::setBpm, 30..240, 5)
            if (ui.listening) BigButton(stringResource(R.string.stop_listening), vm::stop)
            else BigButton(stringResource(R.string.start_listening), { if (hasMicPermission(context)) vm.start(true) else request() }, enabled = ui.scaleKey != null)
            ui.result?.let { r ->
                SectionCard(stringResource(R.string.checker_result)) {
                    Text(stringResource(R.string.checker_counts, r.matched, r.expected.size, r.wrong, r.missing, r.extra))
                    Text(stringResource(R.string.checker_speed, r.notesPerMinute.roundToInt()))
                    val settings = app.saxpractice.ui.nav.LocalSettings.current
                    val heard = r.played.joinToString(" ") { app.saxpractice.ui.components.writtenLabel(settings.noteNaming, settings.instrument, it.midi) }
                    Text(stringResource(R.string.checker_heard, heard), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (r.clean) R.string.checker_looks_clean else R.string.checker_looks_not_clean), style = MaterialTheme.typography.titleMedium)
                    if (ui.logged) Text(stringResource(R.string.checker_logged), color = MaterialTheme.colorScheme.tertiary)
                    else {
                        Text(stringResource(R.string.checker_confirm))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.confirm(true) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.clean_run)) }
                            OutlinedButton(onClick = { vm.confirm(false) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.mistake)) }
                        }
                    }
                }
            }
        }
    }
    if (pick) ScalePickerDialog(onPick = { vm.setScale(it); pick = false }, onDismiss = { pick = false })
}
