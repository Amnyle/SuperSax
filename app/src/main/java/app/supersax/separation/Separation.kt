package app.supersax.separation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.supersax.AppContainer
import app.supersax.MainActivity
import app.supersax.R
import app.supersax.SaxApp
import app.supersax.data.db.KeyValueEntity
import app.supersax.domain.common.AudioRefRecord
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The licence key of the separation service, kept in app-private storage that is never backed up. */
object SeparationKey {
    private fun file(c: AppContainer) = File(c.context.noBackupFilesDir, "lalal_key")
    fun get(c: AppContainer): String? = file(c).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    fun set(c: AppContainer, key: String?) { if (key.isNullOrBlank()) file(c).delete() else file(c).writeText(key.trim()) }
}

/** Marker error: the phone could not reach the service. */
const val OFFLINE = "offline"

/** One separation in the queue. Each step's result is saved, so a stopped job resumes without paying twice. */
@Serializable
data class SeparationJob(
    val id: String,
    val songId: String,
    val title: String,
    /** A Lalal.Stem name. */
    val stem: String,
    val suffix: String,
    val addedAtMs: Long,
    val step: Step = Step.QUEUED,
    val sourceId: String? = null,
    val taskId: String? = null,
    val backTrack: String? = null,
    val progress: Int = 0,
    val error: String? = null,
    val resultSongId: String? = null,
) {
    @Serializable enum class Step { QUEUED, UPLOADING, SEPARATING, DOWNLOADING, DONE, FAILED }
    val active: Boolean get() = step != Step.DONE && step != Step.FAILED
    val stemValue: Lalal.Stem get() = Lalal.Stem.entries.firstOrNull { it.name == stem } ?: Lalal.Stem.WIND
}

/** The queue of separations, saved on the phone and worked through in the background (one at a time). */
class SeparationQueue(private val c: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = ListSerializer(SeparationJob.serializer())
    private val lock = Mutex()

    val jobs: Flow<List<SeparationJob>> = c.db.kv().observe(KEY).map { e ->
        e?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() } ?: emptyList()
    }

    suspend fun all(): List<SeparationJob> = c.db.kv().get(KEY)?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() } ?: emptyList()

    private suspend fun edit(f: (List<SeparationJob>) -> List<SeparationJob>) = lock.withLock {
        c.db.kv().put(KeyValueEntity(KEY, json.encodeToString(ser, f(all()))))
    }

    suspend fun update(id: String, f: (SeparationJob) -> SeparationJob) = edit { l -> l.map { if (it.id == id) f(it) else it } }

    /** Adds a separation of [ref] (removing [stem]) and makes sure the background work runs. */
    suspend fun add(ref: AudioRefRecord, stem: Lalal.Stem, suffix: String) {
        edit { it + SeparationJob(UUID.randomUUID().toString(), ref.id, ref.title, stem.name, suffix, c.clock.nowMs()) }
        kick(c.context)
    }

    /** Puts a failed job back in the queue; it resumes from its last saved step. */
    suspend fun retry(id: String) {
        update(id) { j -> j.copy(step = if (j.taskId != null) SeparationJob.Step.SEPARATING else SeparationJob.Step.QUEUED, error = null) }
        kick(c.context)
    }

    suspend fun remove(id: String) = edit { l -> l.filterNot { it.id == id } }

    suspend fun clearFinished() = edit { l -> l.filter { it.active } }

    companion object {
        const val KEY = "separation:queue"
        private const val WORK = "separation-queue"
        const val CHANNEL = "separation"

        /** Starts (or keeps) the background worker; it runs whenever the phone is online. */
        fun kick(context: Context) {
            val req = OneTimeWorkRequestBuilder<SeparationWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}

/**
 * Works through the queue: upload, start the separation, wait for it, download the version without the
 * instrument and add it to the songs (linked to the original). Runs at most ~8 minutes per go, then asks
 * WorkManager to come back, so long queues go on in the background without a foreground service.
 */
class SeparationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SaxApp).container
        val q = c.separationQueue
        val key = SeparationKey.get(c) ?: return Result.success()
        val started = System.currentTimeMillis()
        while (true) {
            val job = q.all().firstOrNull { it.active } ?: return Result.success()
            if (System.currentTimeMillis() - started > 8 * 60_000L) return Result.retry()
            try {
                step(c, q, key, job)
            } catch (e: java.io.IOException) {
                // Network trouble: try again later from the same step.
                return if (runAttemptCount < 20) Result.retry() else { q.update(job.id) { it.copy(step = SeparationJob.Step.FAILED, error = OFFLINE) }; Result.success() }
            } catch (e: Exception) {
                q.update(job.id) { it.copy(step = SeparationJob.Step.FAILED, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** Moves [job] forward by one step (or to the end), saving each result. */
    private suspend fun step(c: AppContainer, q: SeparationQueue, key: String, job: SeparationJob) {
        when {
            job.sourceId == null -> {
                q.update(job.id) { it.copy(step = SeparationJob.Step.UPLOADING, progress = 0) }
                val ref = c.media.audioRefs.firstValue().firstOrNull { it.id == job.songId } ?: throw Lalal.ServiceException("the song was removed")
                val uri = Uri.parse(ref.uri)
                val ext = (uri.lastPathSegment ?: "").substringAfterLast('.', "m4a").take(5).ifBlank { "m4a" }
                val length = runCatching { c.context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
                val id = withContext(Dispatchers.IO) {
                    val input = c.context.contentResolver.openInputStream(uri) ?: throw Lalal.ServiceException("cannot read the song file")
                    Lalal.upload(key, input, "${ref.title}.$ext", length)
                }
                q.update(job.id) { it.copy(sourceId = id) }
            }
            job.taskId == null -> {
                val task = withContext(Dispatchers.IO) { Lalal.split(key, job.sourceId, job.stemValue) }
                q.update(job.id) { it.copy(taskId = task, step = SeparationJob.Step.SEPARATING, progress = 0) }
            }
            job.backTrack == null -> {
                q.update(job.id) { it.copy(step = SeparationJob.Step.SEPARATING) }
                while (true) {
                    val s = withContext(Dispatchers.IO) { Lalal.check(key, job.taskId) }
                    if (s.state == "success" && s.backTrack != null) { q.update(job.id) { it.copy(backTrack = s.backTrack) }; return }
                    if (s.state == "error") throw Lalal.ServiceException(s.error ?: "separation failed")
                    q.update(job.id) { it.copy(progress = s.progress) }
                    delay(4000)
                }
            }
            else -> {
                q.update(job.id) { it.copy(step = SeparationJob.Step.DOWNLOADING, progress = 0) }
                val out = File(c.songsDir, "${UUID.randomUUID()}.mp3")
                var last = 0
                withContext(Dispatchers.IO) {
                    Lalal.download(job.backTrack, out) { p -> val pct = (p * 100).toInt(); if (pct >= last + 5) { last = pct; kotlinx.coroutines.runBlocking { q.update(job.id) { it.copy(progress = pct) } } } }
                }
                val rec = AudioRefRecord(UUID.randomUUID().toString(), Uri.fromFile(out).toString(), "${job.title} (${job.suffix})", c.clock.nowMs())
                c.media.saveAudioRef(rec)
                val original = c.songs.get(job.songId)
                c.songs.save(rec.id, original.copy(parentId = original.parentId ?: job.songId, removed = job.stem))
                q.update(job.id) { it.copy(step = SeparationJob.Step.DONE, progress = 100, resultSongId = rec.id) }
                notifyDone(applicationContext, rec.title)
            }
        }
    }

    private fun notifyDone(ctx: Context, title: String) {
        if (!app.supersax.work.Reminders.canNotify(ctx)) return
        if (Build.VERSION.SDK_INT >= 26) ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(SeparationQueue.CHANNEL, ctx.getString(R.string.queue_title), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_QUEUE, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, SeparationQueue.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(ctx.getString(R.string.queue_done_title))
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(title.hashCode(), n)
    }
}

private suspend fun <T> Flow<T>.firstValue(): T = first()
