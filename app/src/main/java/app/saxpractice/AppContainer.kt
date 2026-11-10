package app.saxpractice

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import app.saxpractice.data.assets.ContentRepository
import app.saxpractice.data.backup.BackupRepository
import app.saxpractice.data.db.SaxDatabase
import app.saxpractice.data.repo.AppClock
import app.saxpractice.data.repo.LibraryRepository
import app.saxpractice.data.repo.MediaRepository
import app.saxpractice.data.repo.PracticeRepository
import app.saxpractice.data.repo.ProgressRepository
import app.saxpractice.data.repo.QuietMode
import app.saxpractice.data.repo.SessionManager
import app.saxpractice.data.repo.TodayService
import app.saxpractice.data.settings.SettingsRepository
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.domain.program.Pillar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Manual dependency injection: one instance per process, created by [SaxApp]. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val clock = object : AppClock {
        override fun nowMs() = System.currentTimeMillis()
        override fun elapsedMs() = SystemClock.elapsedRealtime()
    }

    val db: SaxDatabase by lazy { SaxDatabase.create(context) }
    val settings by lazy { SettingsRepository(context) }
    val content by lazy { AppContent(context.assets) }
    val practice by lazy { PracticeRepository(db) }
    val progress by lazy { ProgressRepository(db) }
    val library by lazy { LibraryRepository(db) }

    val recordingsDir: File get() = File(context.filesDir, "recordings").apply { mkdirs() }
    val importsDir: File get() = File(context.filesDir, "imports").apply { mkdirs() }
    val media by lazy { MediaRepository(db, recordingsDir) }
    val backup by lazy { BackupRepository(db, settings, recordingsDir, importsDir) { clock.nowMs() } }
    val today by lazy { TodayService(practice, content, settings) }

    val quietMode = object : QuietMode {
        private val nm get() = context.getSystemService(NotificationManager::class.java)
        override fun hasAccess() = nm.isNotificationPolicyAccessGranted
        override fun silence(): Int? {
            if (!hasAccess()) return null
            val previous = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            return previous
        }
        override fun restore(previous: Int) {
            if (hasAccess()) nm.setInterruptionFilter(previous)
        }
    }

    val sessions by lazy { SessionManager(appScope, clock, settings, practice, progress, content, quietMode) }

    /** Phone drill types that exist in this build (the generator only schedules these). */
    val availablePhoneDrills: List<ExerciseType> = emptyList()

    /** The rank system's weakest pillar, fed to the generator (null until the rank system exists). */
    suspend fun currentLimiter(): Pillar? = null
}

/** The app's content loader (extended in later phases with rank, pieces, lessons and fingerings). */
class AppContent(assets: android.content.res.AssetManager) : ContentRepository(assets)
