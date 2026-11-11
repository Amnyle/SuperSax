package app.supersax.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.domain.backup.BackupData
import app.supersax.domain.backup.BackupVersionException
import app.supersax.domain.backup.SettingsRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Backup flow state: a validated backup waiting for the user's confirmation, or a result message. */
data class BackupUi(val pending: BackupData? = null, val busy: Boolean = false, val message: Int? = null, val messageArg: String? = null)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<SettingsRecord> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRecord())
    private val _backup = MutableStateFlow(BackupUi())
    val backup: StateFlow<BackupUi> = _backup.asStateFlow()
    private val staging: File get() = File(c.context.cacheDir, "import-staging")

    fun update(transform: (SettingsRecord) -> SettingsRecord) = viewModelScope.launch { c.settings.update(transform) }

    fun hasQuietAccess() = c.quietMode.hasAccess()

    fun export(resolver: ContentResolver, uri: Uri, includeRecordings: Boolean) = viewModelScope.launch {
        _backup.value = BackupUi(busy = true)
        val r = runCatching { withContext(Dispatchers.IO) { c.backup.export(resolver, uri, includeRecordings) } }
        _backup.value = BackupUi(message = if (r.isSuccess) app.supersax.R.string.backup_exported else app.supersax.R.string.backup_export_failed)
    }

    /** Reads and validates a backup; nothing is replaced until [confirmImport]. */
    fun stageImport(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _backup.value = BackupUi(busy = true)
        val r = runCatching { withContext(Dispatchers.IO) { c.backup.stage(resolver, uri, staging) } }
        _backup.value = r.fold(
            onSuccess = { BackupUi(pending = it) },
            onFailure = { e ->
                when ((e as? BackupVersionException)?.reason) {
                    BackupVersionException.Reason.TOO_NEW -> BackupUi(message = app.supersax.R.string.backup_too_new, messageArg = e.found.toString())
                    BackupVersionException.Reason.TOO_OLD -> BackupUi(message = app.supersax.R.string.backup_too_old, messageArg = (e as BackupVersionException).found.toString())
                    else -> BackupUi(message = app.supersax.R.string.backup_invalid)
                }
            },
        )
    }

    fun confirmImport(onDone: () -> Unit) {
        val data = _backup.value.pending ?: return
        viewModelScope.launch {
            _backup.value = BackupUi(busy = true)
            val r = runCatching { withContext(Dispatchers.IO) { c.backup.commit(data, staging) } }
            _backup.value = BackupUi(message = if (r.isSuccess) app.supersax.R.string.backup_imported else app.supersax.R.string.backup_invalid)
            if (r.isSuccess) onDone()
        }
    }

    fun cancelImport() { staging.deleteRecursively(); _backup.value = BackupUi() }
    fun clearMessage() { _backup.value = _backup.value.copy(message = null) }
}
