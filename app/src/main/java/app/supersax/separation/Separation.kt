package app.supersax.separation

import android.net.Uri
import app.supersax.AppContainer
import app.supersax.domain.common.AudioRefRecord
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The licence key of the separation service, kept in app-private storage that is never backed up. */
object SeparationKey {
    private fun file(c: AppContainer) = File(c.context.noBackupFilesDir, "lalal_key")
    fun get(c: AppContainer): String? = file(c).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    fun set(c: AppContainer, key: String?) { if (key.isNullOrBlank()) file(c).delete() else file(c).writeText(key.trim()) }
}

/** One running or finished separation, per song. */
data class SeparationJob(
    val stem: Lalal.Stem,
    val step: Step,
    val progress: Int = 0,
    val error: String? = null,
    /** The new song (the version without the instrument) once done. */
    val resultSongId: String? = null,
) { enum class Step { UPLOADING, SEPARATING, DOWNLOADING, DONE, FAILED } }

/** The audio extension at the end of a download link's file name ("mp3" when it has none). */
internal fun fileExtension(url: String): String {
    val name = url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
    val ext = name.substringAfterLast('.', "").lowercase()
    return if (ext in setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus")) ext else "mp3"
}

/** Runs separations in the app scope, so they go on when the screen is closed. */
class SeparationJobs(private val c: AppContainer) {
    private val _jobs = MutableStateFlow<Map<String, SeparationJob>>(emptyMap())
    val jobs = _jobs.asStateFlow()

    private fun set(songId: String, j: SeparationJob) { _jobs.value = _jobs.value + (songId to j) }

    fun dismiss(songId: String) { _jobs.value = _jobs.value - songId }

    /** Makes a copy of song [ref] without [stem] and adds it to the song list ("Title (no sax)"). */
    fun start(ref: AudioRefRecord, stem: Lalal.Stem, titleSuffix: String) {
        val key = SeparationKey.get(c) ?: return
        if (_jobs.value[ref.id]?.step.let { it != null && it != SeparationJob.Step.DONE && it != SeparationJob.Step.FAILED }) return
        set(ref.id, SeparationJob(stem, SeparationJob.Step.UPLOADING))
        c.appScope.launch {
            try {
                val uri = Uri.parse(ref.uri)
                val ext = (uri.lastPathSegment ?: "").substringAfterLast('.', "m4a").take(5).ifBlank { "m4a" }
                val length = runCatching { c.context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
                val id = withContext(Dispatchers.IO) {
                    val input = c.context.contentResolver.openInputStream(uri) ?: throw Lalal.ServiceException("cannot read the song file")
                    Lalal.upload(key, input, "${ref.title}.$ext", length)
                }
                val task = withContext(Dispatchers.IO) { Lalal.split(key, id, stem) }
                set(ref.id, SeparationJob(stem, SeparationJob.Step.SEPARATING))
                var status: Lalal.Status
                var waited = 0
                while (true) {
                    delay(3000); waited += 3
                    status = withContext(Dispatchers.IO) { Lalal.check(key, task) }
                    if (status.state == "success" || status.state == "error") break
                    set(ref.id, SeparationJob(stem, SeparationJob.Step.SEPARATING, status.progress))
                    if (waited > 30 * 60) throw Lalal.ServiceException("timed out")
                }
                if (status.state == "error" || status.backTrack == null) throw Lalal.ServiceException(status.error ?: "separation failed")
                set(ref.id, SeparationJob(stem, SeparationJob.Step.DOWNLOADING))
                // Results are asked as mp3; the download link itself carries no extension.
                val out = File(c.songsDir, "${UUID.randomUUID()}.mp3")
                withContext(Dispatchers.IO) { Lalal.download(status.backTrack!!, out) { p -> set(ref.id, SeparationJob(stem, SeparationJob.Step.DOWNLOADING, (p * 100).toInt())) } }
                val rec = AudioRefRecord(UUID.randomUUID().toString(), Uri.fromFile(out).toString(), "${ref.title} ($titleSuffix)", c.clock.nowMs())
                c.media.saveAudioRef(rec)
                // Same song, same key, tempo and bars: keep what was found and set for the original.
                // Linked to the original (a version of a version is linked to the same original).
                val original = c.songs.get(ref.id)
                c.songs.save(rec.id, original.copy(parentId = original.parentId ?: ref.id, removed = stem.name))
                set(ref.id, SeparationJob(stem, SeparationJob.Step.DONE, 100, resultSongId = rec.id))
            } catch (e: Exception) {
                set(ref.id, SeparationJob(stem, SeparationJob.Step.FAILED, error = e.message ?: e.javaClass.simpleName))
            }
        }
    }
}
