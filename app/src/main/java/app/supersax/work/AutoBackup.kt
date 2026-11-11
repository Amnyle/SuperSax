package app.supersax.work

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.supersax.SaxApp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Automatic backups, like SuperHealth: checked every 6 hours when online, written when the last one is
 * older than the chosen frequency (2 hours of slack so it does not drift). To Google Drive, the file
 * chosen once in the Drive app is replaced each time (Drive uploads it); to a folder, a dated file is
 * added and only the 7 newest are kept.
 */
object AutoBackup {
    private const val WORK = "auto-backup"
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L
    const val DRIVE_PACKAGE = "com.google.android.apps.docs"

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }

    fun due(lastMs: Long, everyDays: Int, nowMs: Long) = everyDays > 0 && nowMs - lastMs >= everyDays * DAY - 2 * HOUR

    /** Writes a backup now to the chosen destination; the result (time or error) is kept in the settings. */
    suspend fun now(context: Context) {
        val c = (context.applicationContext as SaxApp).container
        val s = c.settings.current()
        try {
            val resolver = context.contentResolver
            when (s.autoBackupDest) {
                "DRIVE" -> {
                    val uri = Uri.parse(s.autoBackupDocument ?: error("No Google Drive file chosen"))
                    c.backup.export(resolver, uri, s.autoBackupRecordings)
                }
                "FOLDER" -> {
                    val tree = Uri.parse(s.autoBackupFolder ?: error("No folder chosen"))
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    val name = "supersax-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".zip"
                    val doc = DocumentsContract.createDocument(resolver, parent, "application/zip", name) ?: error("Cannot create $name")
                    c.backup.export(resolver, doc, s.autoBackupRecordings)
                    prune(context, tree)
                }
                else -> return
            }
            c.settings.update { it.copy(lastAutoBackupMs = System.currentTimeMillis(), lastAutoBackupError = null) }
        } catch (e: Exception) {
            c.settings.update { it.copy(lastAutoBackupError = e.message ?: e.javaClass.simpleName) }
            throw e
        }
    }

    /** Keeps the 7 newest supersax-*.zip files in the folder. */
    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(1) ?: continue
                if (name.startsWith("supersax-") && name.endsWith(".zip")) found += cur.getString(0) to name
            }
        }
        found.sortedByDescending { it.second }.drop(7).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SaxApp).container
        val s = c.settings.current()
        if (s.autoBackupDest == "NONE" || !AutoBackup.due(s.lastAutoBackupMs, s.autoBackupEveryDays, System.currentTimeMillis())) return Result.success()
        return runCatching { AutoBackup.now(applicationContext) }.fold({ Result.success() }, { if (runAttemptCount < 2) Result.retry() else Result.success() })
    }
}
