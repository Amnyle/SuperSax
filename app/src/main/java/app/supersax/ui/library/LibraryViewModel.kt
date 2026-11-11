package app.supersax.ui.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.R
import app.supersax.domain.audio.MelodyNote
import app.supersax.domain.common.PieceStage
import app.supersax.domain.common.UserPieceRecord
import app.supersax.domain.library.MusicXmlImport
import app.supersax.domain.library.PieceFile
import app.supersax.domain.library.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Turns a score into notes for the tone synth (tied notes merged); beats are in units of 1/den. */
fun melodyOf(score: Score): Pair<List<MelodyNote>, List<Int>> {
    val notes = mutableListOf<MelodyNote>()
    val sourceIndex = mutableListOf<Int>()
    var tiedFrom = false
    score.notes.forEachIndexed { i, n ->
        val beats = n.duration.value * score.timeDen
        if (tiedFrom && notes.isNotEmpty()) {
            val last = notes.removeAt(notes.lastIndex)
            notes += last.copy(beats = last.beats + beats)
        } else {
            notes += MelodyNote(n.pitch?.midi, beats); sourceIndex += i
        }
        tiedFrom = n.tie && n.pitch != null
    }
    return notes to sourceIndex
}

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    val matches = c.libraryService.matches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress = c.library.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val userFiles = c.library.userPieces.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val output = c.audio.state
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    private val json = Json { encodeDefaults = true }
    private var melodyMap: List<Int> = emptyList()

    fun clearMessage() { _message.value = null }

    private fun displayName(resolver: ContentResolver, uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "file"

    /** Imports a MusicXML (parsed, playable, transposable) or a PDF (shown as it is). Files stay in app storage and go into backups. */
    fun import(resolver: ContentResolver, uri: Uri, attachTo: String? = null) = viewModelScope.launch {
        val r = runCatching {
            withContext(Dispatchers.IO) {
                val name = displayName(resolver, uri)
                val id = "user-" + UUID.randomUUID().toString().take(8)
                val isPdf = name.lowercase().endsWith(".pdf") || resolver.getType(uri) == "application/pdf"
                val ext = if (isPdf) "pdf" else name.substringAfterLast('.', "musicxml")
                val fileName = "$id.$ext"
                val target = File(c.importsDir, fileName)
                resolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                val pieceJson = if (isPdf) null else {
                    val title = name.substringBeforeLast('.')
                    val parsed = target.inputStream().use { MusicXmlImport.parse(it, id, title) }
                    json.encodeToString(PieceFile.serializer(), parsed)
                }
                c.library.saveUserPiece(UserPieceRecord(id, name.substringBeforeLast('.'), if (isPdf) "pdf" else "musicxml", fileName, pieceJson, attachTo, c.clock.nowMs()))
            }
        }
        _message.value = if (r.isSuccess) R.string.import_done else R.string.import_failed
    }

    fun deleteUserFile(rec: UserPieceRecord) = viewModelScope.launch {
        File(c.importsDir, rec.fileName).delete()
        c.library.deleteUserPiece(rec.id)
    }

    fun setStage(pieceId: String, sectionId: String, stage: PieceStage) = viewModelScope.launch {
        c.libraryService.setStage(pieceId, sectionId, stage, c.clock.nowMs())
    }

    fun listen(score: Score, bpm: Int) {
        if (c.audio.state.value.melodyPlaying) { c.audio.stopMelody(); return }
        val (notes, map) = melodyOf(score)
        melodyMap = map
        c.audio.playMelody(notes, bpm)
    }

    /** Index of the score note being played, for highlighting. */
    fun highlighted(melodyIndex: Int): Int = melodyMap.getOrNull(melodyIndex) ?: -1

    fun importsDir() = c.importsDir

    override fun onCleared() { c.audio.stopMelody() }
}
