package app.saxpractice.ui.media

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.R
import app.saxpractice.domain.backup.SettingsRecord
import app.saxpractice.domain.common.MouthpieceRecord
import app.saxpractice.domain.common.ReedRecord
import app.saxpractice.ui.components.ScreenScaffold
import app.saxpractice.ui.components.ScrollColumn
import app.saxpractice.ui.components.SectionCard
import app.saxpractice.ui.nav.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class GearViewModel(private val c: AppContainer) : ViewModel() {
    val reeds = c.media.reeds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mouthpieces = c.media.mouthpieces.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRecord())
    /** Minutes played on each reed, from session logs. */
    val minutesPerReed = combine(c.practice.sessions, c.media.reeds) { s, _ ->
        s.filter { it.reedId != null }.groupBy { it.reedId!! }.mapValues { (_, l) -> l.sumOf { it.playedSec } / 60 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun addReed(brand: String, strength: String) = viewModelScope.launch {
        val r = ReedRecord(UUID.randomUUID().toString(), brand.trim(), strength.trim(), LocalDate.now().toEpochDay())
        c.media.saveReed(r)
        c.settings.update { it.copy(currentReedId = r.id) }
    }
    fun setCurrentReed(id: String) = viewModelScope.launch { c.settings.update { it.copy(currentReedId = id) } }
    fun retire(r: ReedRecord) = viewModelScope.launch {
        c.media.saveReed(r.copy(retired = true))
        c.settings.update { if (it.currentReedId == r.id) it.copy(currentReedId = null) else it }
    }
    fun addMouthpiece(name: String) = viewModelScope.launch {
        val m = MouthpieceRecord(UUID.randomUUID().toString(), name.trim())
        c.media.saveMouthpiece(m)
        c.settings.update { it.copy(currentMouthpieceId = m.id) }
    }
    fun setCurrentMouthpiece(id: String) = viewModelScope.launch { c.settings.update { it.copy(currentMouthpieceId = id) } }
}

/** Reeds and mouthpieces. Hours per reed come from session minutes. No reminders. */
@Composable
fun GearScreen(onBack: () -> Unit, onCompare: () -> Unit) {
    val vm = appViewModel { GearViewModel(it) }
    val reeds by vm.reeds.collectAsStateWithLifecycle()
    val mps by vm.mouthpieces.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val minutes by vm.minutesPerReed.collectAsStateWithLifecycle()
    var brand by rememberSaveable { mutableStateOf("") }
    var strength by rememberSaveable { mutableStateOf("") }
    var mpName by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(stringResource(R.string.gear), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            SectionCard(stringResource(R.string.reeds)) {
                reeds.filter { !it.retired }.forEach { r ->
                    val current = s.currentReedId == r.id
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reed_line, r.brand, r.strength, LocalDate.ofEpochDay(r.startedEpochDay).toString(),
                            (minutes[r.id] ?: 0) / 60.0) + if (current) " · " + stringResource(R.string.current) else "", Modifier.weight(1f))
                        if (!current) TextButton(onClick = { vm.setCurrentReed(r.id) }) { Text(stringResource(R.string.use)) }
                        TextButton(onClick = { vm.retire(r) }) { Text(stringResource(R.string.retire)) }
                    }
                }
                OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.reed_brand)) }, singleLine = true)
                OutlinedTextField(strength, { strength = it }, label = { Text(stringResource(R.string.reed_strength)) }, singleLine = true)
                Button(onClick = { vm.addReed(brand, strength); brand = ""; strength = "" }, enabled = brand.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_reed)) }
                val retired = reeds.filter { it.retired }
                if (retired.isNotEmpty()) Text(stringResource(R.string.retired_reeds, retired.size), style = MaterialTheme.typography.bodySmall)
            }
            SectionCard(stringResource(R.string.mouthpieces)) {
                mps.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name + if (s.currentMouthpieceId == m.id) " · " + stringResource(R.string.current) else "", Modifier.weight(1f))
                        if (s.currentMouthpieceId != m.id) TextButton(onClick = { vm.setCurrentMouthpiece(m.id) }) { Text(stringResource(R.string.use)) }
                    }
                }
                OutlinedTextField(mpName, { mpName = it }, label = { Text(stringResource(R.string.mouthpiece_name)) }, singleLine = true)
                Button(onClick = { vm.addMouthpiece(mpName); mpName = "" }, enabled = mpName.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.add_mouthpiece))
                }
                Text(stringResource(R.string.mouthpiece_compare_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onCompare) { Text(stringResource(R.string.recordings)) }
            }
        }
    }
}
