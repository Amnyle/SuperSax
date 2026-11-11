package app.saxpractice

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import app.saxpractice.audio.AudioEngine
import app.saxpractice.audio.MicCapture
import app.saxpractice.data.assets.ContentRepository
import app.saxpractice.domain.audio.Timbre
import app.saxpractice.domain.backup.SynthTimbre
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import app.saxpractice.data.repo.PieceChooser
import app.saxpractice.data.repo.LibraryService
import app.saxpractice.data.repo.RankService
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
    val libraryService by lazy { LibraryService(content, library, practice, appScope) }
    val rank by lazy { RankService(this) }
    val today by lazy { TodayService(practice, content, settings, PieceChooser { libraryService.choose(it) }) }

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
    val availablePhoneDrills: List<ExerciseType> = listOf(
        ExerciseType.READING, ExerciseType.SCALE_RECOGNITION, ExerciseType.THEORY_LESSON, ExerciseType.FINGERING,
    )

    val audio = AudioEngine()
    val mic = MicCapture()

    init {
        // Rank snapshots on every change (used by the weekly review; never notified).
        appScope.launch { rank.result.collect { runCatching { rank.snapshotIfChanged(it) } } }
        appScope.launch {
            // Pieces played in a session become "last played" for the "play for fun" rotation.
            sessions.afterSave = { s -> s.blocks.mapNotNull { it.pieceId }.distinct().forEach { libraryService.markPlayed(it, s.endedAtMs) } }
        }
        // Keep the synth's reference pitch and timbre in step with the settings.
        appScope.launch {
            settings.settings.collect { s ->
                audio.a4 = s.a4Hz
                audio.timbre = if (s.synthTimbre == SynthTimbre.SOFT) Timbre.SOFT else Timbre.SINE
            }
        }
    }

    /** Starts today's planned session if none is running (used by the home-screen widget). */
    suspend fun startTodaySession(): Boolean {
        if (sessions.run.value != null) return true
        val plan = today.plan(practice.activeProgram.first(), practice.playerNow(), clock.nowMs())
        val session = plan.session ?: return false
        if (session.steps.isEmpty()) return false
        sessions.start(session, plan.day?.dayOfWeek)
        return true
    }

    /** The rank system's weakest pillar, fed to the generator. */
    suspend fun currentLimiter(): Pillar? = rank.current().limiter
}

/** The app's content loader: config, exercises, rank thresholds, pieces (lessons and fingerings later). */
class AppContent(assets: android.content.res.AssetManager) : ContentRepository(assets) {
    val rankConfig: app.saxpractice.domain.rank.RankConfig by lazy {
        load("rank/rank_config.json", app.saxpractice.domain.rank.RankConfig.serializer())
    }
    val lessons: List<app.saxpractice.domain.drills.Lesson> by lazy {
        list("lessons").map { load("lessons/$it", app.saxpractice.domain.drills.Lesson.serializer()) }.sortedBy { it.order }
    }
    val fingerings: app.saxpractice.domain.drills.FingeringChart by lazy {
        load("fingerings/alto_fingerings.json", app.saxpractice.domain.drills.FingeringChart.serializer())
    }
    val bundledPieces: List<app.saxpractice.domain.library.Piece> by lazy {
        list("pieces").mapNotNull { f ->
            runCatching { app.saxpractice.domain.library.Piece.from(load("pieces/$f", app.saxpractice.domain.library.PieceFile.serializer())) }.getOrNull()
        }
    }
}
