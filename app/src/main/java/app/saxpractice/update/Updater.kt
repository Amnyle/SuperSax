package app.saxpractice.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.saxpractice.MainActivity
import app.saxpractice.R
import app.saxpractice.SaxApp
import app.saxpractice.domain.update.AvailableUpdate
import app.saxpractice.domain.update.GhRelease
import app.saxpractice.domain.update.Releases
import app.saxpractice.work.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * In-app updates from the GitHub releases of the (public) repository: no account, no token.
 * Check → download → verify (same app, same signing key, newer version) → install with PackageInstaller.
 * This is the only network use of the app, and it can be turned off in Settings.
 */
object Updater {
    const val REPO = "Amnyle/SuperSax"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val CHANNEL = "updates"
    private const val NOTIF = 4301

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: String) : State
        data class Available(val update: AvailableUpdate) : State
        data class Downloading(val update: AvailableUpdate, val fraction: Float) : State
        data class Installing(val version: String) : State
        data class Failed(val reason: Reason, val detail: String = "") : State
    }

    enum class Reason { NETWORK, NO_RELEASE, INCOMPLETE, NOT_THIS_APP, SIGNATURE, NOT_NEWER, INSTALL, CANCELLED }

    private class UpdateException(val reason: Reason, detail: String = "") : Exception(detail)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    fun installedVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 60_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "SaxPractice-Updater")
            instanceFollowRedirects = true
        }

    /** The newer release, or null when up to date. Throws [UpdateException] on network or API problems. */
    suspend fun check(ctx: Context): AvailableUpdate? = withContext(Dispatchers.IO) {
        val conn = try {
            open("https://api.github.com/repos/$REPO/releases/latest", "application/vnd.github+json").also { it.connect() }
        } catch (e: Exception) { throw UpdateException(Reason.NETWORK, e.message.orEmpty()) }
        try {
            when (val code = conn.responseCode) {
                200 -> Unit
                404 -> throw UpdateException(Reason.NO_RELEASE)
                else -> throw UpdateException(Reason.NETWORK, "GitHub $code")
            }
            val release = json.decodeFromString(GhRelease.serializer(), conn.inputStream.bufferedReader().readText())
            Releases.available(release, installedVersion(ctx))
        } finally { conn.disconnect() }
    }

    /** Checks and updates [state] (settings screen). */
    suspend fun refresh(ctx: Context) {
        if (!lock.tryLock()) return
        try {
            _state.value = State.Checking
            _state.value = check(ctx)?.let { State.Available(it) } ?: State.UpToDate(installedVersion(ctx))
        } catch (e: UpdateException) {
            _state.value = State.Failed(e.reason, e.message.orEmpty())
        } catch (e: Exception) {
            _state.value = State.Failed(Reason.NETWORK, e.message.orEmpty())
        } finally { lock.unlock() }
    }

    /** Downloads, verifies and hands the APK to Android's installer. */
    suspend fun downloadAndInstall(ctx: Context, update: AvailableUpdate) = lock.withLock {
        try {
            val file = download(ctx, update)
            verify(ctx, file)
            install(ctx, file, update.version)
        } catch (e: UpdateException) {
            _state.value = State.Failed(e.reason, e.message.orEmpty())
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.value = State.Available(update); throw e
        } catch (e: Exception) {
            _state.value = State.Failed(Reason.NETWORK, e.message.orEmpty())
        }
    }

    private suspend fun download(ctx: Context, update: AvailableUpdate): File = withContext(Dispatchers.IO) {
        _state.value = State.Downloading(update, 0f)
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "SaxPractice-${update.version}.apk")
        val conn = open(update.apk.browser_download_url, "application/octet-stream")
        try {
            if (conn.responseCode !in 200..299) throw UpdateException(Reason.NETWORK, "HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.apk.size
            var done = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) _state.value = State.Downloading(update, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (update.apk.size > 0 && out.length() != update.apk.size) { out.delete(); throw UpdateException(Reason.INCOMPLETE) }
        } finally { conn.disconnect() }
        out
    }

    /** Same package, same signing key as the installed app, and newer — otherwise the file is deleted. */
    private fun verify(ctx: Context, apk: File) {
        fun fail(r: Reason): Nothing { apk.delete(); throw UpdateException(r) }
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.path, flags) ?: fail(Reason.INCOMPLETE)
        if (info.packageName != ctx.packageName) fail(Reason.NOT_THIS_APP)
        val mine = pm.getPackageInfo(ctx.packageName, flags)
        @Suppress("DEPRECATION")
        fun certs(i: PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) i.signingInfo?.apkContentsSigners else i.signatures)?.map { it.toCharsString() }?.toSet().orEmpty()
        if (certs(info).isEmpty() || certs(info) != certs(mine)) fail(Reason.SIGNATURE)
        if (!Releases.isNewer(info.versionName ?: "0", installedVersion(ctx))) fail(Reason.NOT_NEWER)
    }

    private suspend fun install(ctx: Context, apk: File, version: String) = withContext(Dispatchers.IO) {
        _state.value = State.Installing(version)
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { s ->
                s.openWrite("saxpractice.apk", 0, apk.length()).use { o -> apk.inputStream().use { it.copyTo(o) }; s.fsync(o) }
                val intent = Intent(ctx, InstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw UpdateException(Reason.INSTALL, e.message.orEmpty())
        }
    }

    /** Result from Android's installer. */
    internal fun onInstallResult(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { ctx.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.UpToDate(installedVersion(ctx))
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = State.Failed(Reason.CANCELLED)
            else -> _state.value = State.Failed(Reason.INSTALL, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
        }
    }

    /** Android asks once to allow this app to install updates. */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Schedules or cancels the daily check. Never throws: a scheduler problem must not stop the app. */
    fun schedule(ctx: Context, enabled: Boolean) = runCatching {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) { wm.cancelUniqueWork("update-check"); return@runCatching }
        wm.enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }

    internal fun notifyAvailable(ctx: Context, update: AvailableUpdate) {
        if (!Reminders.canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.updates), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, NOTIF, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(ctx.getString(R.string.update_available_title, update.version))
            .setContentText(ctx.getString(R.string.update_tap_to_install))
            .setContentIntent(open).setAutoCancel(true).build()
        @Suppress("MissingPermission")
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF, n) }
    }
}

/** Daily background check (network required); posts one notification when a newer version exists. */
class UpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SaxApp).container
        val s = c.settings.current()
        if (!s.updateCheck) return Result.success()
        val update = runCatching { Updater.check(applicationContext) }.getOrNull() ?: return Result.success()
        if (update.version != s.updateSkip) Updater.notifyAvailable(applicationContext, update)
        return Result.success()
    }
}

/** Receives the installer's result (confirmation request, success or failure). */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Updater.onInstallResult(ctx, intent)
}
