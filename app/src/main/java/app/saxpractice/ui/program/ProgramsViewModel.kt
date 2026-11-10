package app.saxpractice.ui.program

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.saxpractice.AppContainer
import app.saxpractice.domain.generator.Generator
import app.saxpractice.domain.program.Program
import app.saxpractice.domain.program.ProgramOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ProgramsUi(val programs: List<Program> = emptyList(), val activeId: String? = null)

/** Program list: activate, create (manual or generated), import/export as JSON, delete. */
class ProgramsViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<ProgramsUi> = combine(c.practice.programs, c.practice.activeProgram) { all, active ->
        ProgramsUi(all, active?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramsUi())

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun activate(id: String) = viewModelScope.launch { c.practice.setActive(id) }
    fun delete(id: String) = viewModelScope.launch { c.practice.deleteProgram(id) }

    fun newManual(name: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val p = Program(UUID.randomUUID().toString(), name, ProgramOrigin.MANUAL, emptyList(),
            enduranceCapSec = c.practice.playerNow().enduranceCapSec, restSec = c.settings.current().restSec, createdAtEpochMs = c.clock.nowMs())
        c.practice.saveProgram(p, makeActive = ui.value.programs.isEmpty(), nowMs = c.clock.nowMs())
        onCreated(p.id)
    }

    /** Generates a fresh program from the onboarding answers, the current focus and the rank limiter. */
    fun regenerate(name: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val a = c.practice.onboarding() ?: return@launch
        val cfg = c.content.generatorConfig
        val player = c.practice.playerNow()
        val p = Generator.generate(
            Generator.Input(UUID.randomUUID().toString(), name, a.days.ifEmpty { setOf(1) }, a.minutesPerSession,
                player.enduranceCapSec, player.focus, c.currentLimiter(), c.availablePhoneDrills, c.settings.current().restSec, c.clock.nowMs()),
            cfg,
        )
        c.practice.saveProgram(p, makeActive = false, nowMs = c.clock.nowMs())
        onCreated(p.id)
    }

    fun export(resolver: ContentResolver, uri: Uri, id: String) = viewModelScope.launch {
        val p = c.practice.program(id) ?: return@launch
        val ok = runCatching {
            withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "wt")!!.use { it.write(c.practice.encodeProgram(p).toByteArray()) } }
        }.isSuccess
        _message.value = if (ok) app.saxpractice.R.string.program_exported else app.saxpractice.R.string.program_export_failed
    }

    fun import(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
            val p = c.practice.decodeProgram(text)
            val fresh = p.copy(id = UUID.randomUUID().toString())
            c.practice.saveProgram(fresh, makeActive = false, nowMs = c.clock.nowMs())
        }
        _message.value = if (result.isSuccess) app.saxpractice.R.string.program_imported else app.saxpractice.R.string.program_import_failed
    }

    suspend fun hasOnboarding() = c.practice.onboarding() != null
    suspend fun active() = c.practice.activeProgram.first()
}
