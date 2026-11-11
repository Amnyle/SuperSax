package app.supersax.ui.advanced

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.audio.PracticePlayer
import app.supersax.domain.common.AudioRefRecord
import app.supersax.domain.common.LoopPoint
import app.supersax.ui.components.EmptyState
import app.supersax.ui.components.ErrorBanner
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.formatClock
import app.supersax.ui.nav.appViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class SlowDownViewModel(private val c: AppContainer) : ViewModel() {
    val refs = c.media.audioRefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val player = PracticePlayer(c.context)
    private val _current = MutableStateFlow<AudioRefRecord?>(null)
    val current = _current.asStateFlow()

    init { viewModelScope.launch { while (isActive) { player.tick(); delay(50) } } }

    /** Keeps a reference to the user's file (it stays where it is). */
    fun add(uri: Uri) = viewModelScope.launch {
        runCatching { c.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = c.context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "audio"
        val rec = AudioRefRecord(UUID.randomUUID().toString(), uri.toString(), name, c.clock.nowMs())
        c.media.saveAudioRef(rec)
        open(rec)
    }

    fun open(r: AudioRefRecord) { _current.value = r; player.setLoop(null, null); player.load(Uri.parse(r.uri)) }
    fun remove(r: AudioRefRecord) = viewModelScope.launch { if (_current.value?.id == r.id) { player.pause(); _current.value = null }; c.media.deleteAudioRef(r.id) }

    fun setA() { val u = player.ui.value; player.setLoop(player.position(), u.loopEndMs?.takeIf { it > player.position() }) }
    fun setB() { val u = player.ui.value; player.setLoop(u.loopStartMs ?: 0, player.position()) }
    fun clearLoop() = player.setLoop(null, null)

    fun saveLoop(name: String) = viewModelScope.launch {
        val r = _current.value ?: return@launch
        val u = player.ui.value
        val a = u.loopStartMs ?: return@launch; val b = u.loopEndMs ?: return@launch
        val updated = r.copy(loops = r.loops + LoopPoint(name.ifBlank { "${formatClock(a / 1000)}–${formatClock(b / 1000)}" }, a, b))
        c.media.saveAudioRef(updated); _current.value = updated
    }

    fun useLoop(l: LoopPoint) { player.setLoop(l.startMs, l.endMs); player.seekTo(l.startMs) }
    fun deleteLoop(l: LoopPoint) = viewModelScope.launch {
        val r = _current.value ?: return@launch
        val updated = r.copy(loops = r.loops - l); c.media.saveAudioRef(updated); _current.value = updated
    }

    override fun onCleared() { player.release() }
}

/** Slow-down and loop player: 50–100 percent speed without changing pitch, A–B loop, named loop points. */
@Composable
fun SlowDownScreen(onBack: () -> Unit) {
    val vm = appViewModel { SlowDownViewModel(it) }
    val refs by vm.refs.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val ui by vm.player.ui.collectAsStateWithLifecycle()
    var loopName by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.add(uri) }
    ScreenScaffold(stringResource(R.string.slow_down), onBack = onBack, actions = {
        TextButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.add_audio)) }
    }) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.slow_down_hint), style = MaterialTheme.typography.bodySmall)
            val r = current
            if (r != null) SectionCard(r.title) {
                ui.error?.let { ErrorBanner(stringResource(R.string.audio_open_failed, it)) }
                Slider(value = ui.positionMs.toFloat(), onValueChange = { vm.player.seekTo(it.toLong()) }, valueRange = 0f..ui.durationMs.coerceAtLeast(1).toFloat())
                Text("${formatClock(ui.positionMs / 1000)} / ${formatClock(ui.durationMs / 1000)}")
                Button(onClick = vm.player::toggle, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(if (ui.playing) R.string.pause else R.string.play)) }
                Text(stringResource(R.string.speed_percent, (ui.speed * 100).toInt()))
                Slider(value = ui.speed, onValueChange = { vm.player.setSpeed((it * 20).toInt() / 20f) }, valueRange = 0.5f..1f, steps = 9)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = vm::setA, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.set_a)) }
                    FilledTonalButton(onClick = vm::setB, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.set_b)) }
                    TextButton(onClick = vm::clearLoop) { Text(stringResource(R.string.clear)) }
                }
                val a = ui.loopStartMs; val b = ui.loopEndMs
                Text(if (a != null && b != null) stringResource(R.string.loop_from_to, formatClock(a / 1000), formatClock(b / 1000)) else stringResource(R.string.no_loop))
                if (a != null && b != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(loopName, { loopName = it }, label = { Text(stringResource(R.string.loop_name)) }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.saveLoop(loopName); loopName = "" }) { Text(stringResource(R.string.save)) }
                }
                r.loops.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.useLoop(l) }, modifier = Modifier.weight(1f)) { Text("${l.name} (${formatClock(l.startMs / 1000)}–${formatClock(l.endMs / 1000)})") }
                        TextButton(onClick = { vm.deleteLoop(l) }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
            if (refs.isEmpty()) EmptyState(stringResource(R.string.slow_down_empty), stringResource(R.string.add_audio)) { picker.launch(arrayOf("audio/*")) }
            refs.forEach { ref ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.open(ref) }, modifier = Modifier.weight(1f)) { Text(ref.title) }
                        TextButton(onClick = { vm.remove(ref) }) { Text(stringResource(R.string.remove)) }
                    }
                }
            }
        }
    }
}
