package app.supersax

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import app.supersax.audio.AudioEngine
import app.supersax.audio.MicCapture
import app.supersax.data.assets.ContentRepository
import app.supersax.domain.audio.Timbre
import app.supersax.domain.backup.SynthTimbre
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.supersax.data.backup.BackupRepository
import app.supersax.data.db.SaxDatabase
import app.supersax.data.repo.AppClock
import app.supersax.data.repo.LibraryRepository
import app.supersax.data.repo.MediaRepository
import app.supersax.data.repo.PracticeRepository
import app.supersax.data.repo.ProgressRepository
import app.supersax.data.repo.QuietMode
import app.supersax.data.repo.SessionManager
import app.supersax.data.repo.TodayService
import app.supersax.data.repo.PieceChooser
import app.supersax.data.repo.LibraryService
import app.supersax.data.repo.RankService
import app.supersax.data.settings.SettingsRepository
import app.supersax.domain.program.ExerciseType
import app.supersax.domain.program.Pillar
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
    val songs by lazy { app.supersax.data.repo.SongRepository(db) }
    val separationQueue by lazy { app.supersax.separation.SeparationQueue(this) }
    /** Recorded instruments for backing tracks and the ear trainer (loaded on first use, ~12 MB). */
    private val bandLazy = lazy { app.supersax.domain.audio.Band.load { context.assets.open("sounds/$it.bin") } }
    /** Blocks while the files load the first time: use off the main thread (see [preloadBand], [bandIfReady]). */
    val band: app.supersax.domain.audio.Band get() = bandLazy.value
    /** The band if already loaded (never blocks), for the main thread. */
    fun bandIfReady(): app.supersax.domain.audio.Band? = if (bandLazy.isInitialized()) bandLazy.value else null
    /** Starts loading the recorded instruments in the background. */
    fun preloadBand() { if (!bandLazy.isInitialized()) appScope.launch { runCatching { bandLazy.value } } }
    /** Songs downloaded from YouTube (app-private). */
    val songsDir: File get() = File(context.filesDir, "songs").apply { mkdirs() }
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

    /** One-time data fixes after an update (each runs once, marked in the key-value table). */
    private suspend fun migrate() {
        val kv = db.kv()
        if (kv.get("migr:onboarding_range") == null) {
            // Onboarding used to store the registers answer as two self-reported range notes at the same
            // instant; they are not notes actually played, so they no longer count for the Range rank.
            db.rank().events().filter { it.kind == app.supersax.domain.common.RankEventKind.RANGE_NOTE && !it.verified }
                .groupBy { it.atMs }.values.filter { it.size == 2 }.flatten().forEach { db.rank().deleteEvent(it.id) }
            kv.put(app.supersax.data.db.KeyValueEntity("migr:onboarding_range", "1"))
        }
        if (kv.get("migr:no_breathing") == null) {
            // Generated programs no longer have a breathing block: its minutes go to long tones.
            practice.programs.first().filter { it.origin == app.supersax.domain.program.ProgramOrigin.GENERATED }.forEach { p ->
                var changed = false
                val days = p.days.map { d ->
                    val breathing = d.blocks.filter { it.kind == app.supersax.domain.program.BlockKind.BREATHING }.sumOf { it.minutes }
                    if (breathing == 0 && d.blocks.none { it.kind == app.supersax.domain.program.BlockKind.BREATHING }) return@map d
                    changed = true
                    val rest = d.blocks.filter { it.kind != app.supersax.domain.program.BlockKind.BREATHING }
                    val target = rest.indexOfFirst { it.kind == app.supersax.domain.program.BlockKind.LONG_TONES }.let { if (it < 0) 0 else it }
                    d.copy(blocks = rest.mapIndexed { i, b -> if (i == target) b.copy(minutes = b.minutes + breathing) else b })
                }
                if (changed) practice.saveProgram(p.copy(days = days), makeActive = false, nowMs = clock.nowMs())
            }
            kv.put(app.supersax.data.db.KeyValueEntity("migr:no_breathing", "1"))
        }
    }

    init {
        appScope.launch { runCatching { migrate() } }
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
    val rankConfig: app.supersax.domain.rank.RankConfig by lazy {
        load("rank/rank_config.json", app.supersax.domain.rank.RankConfig.serializer())
    }
    val lessons: List<app.supersax.domain.drills.Lesson> by lazy {
        list("lessons").map { load("lessons/$it", app.supersax.domain.drills.Lesson.serializer()) }.sortedBy { it.order }
    }
    /** Lessons before each level of the phone drills, by DrillKind name. */
    val drillLessons: Map<String, List<app.supersax.domain.drills.DrillLesson>> by lazy {
        load("drill_lessons/lessons.json", kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(),
            kotlinx.serialization.builtins.ListSerializer(app.supersax.domain.drills.DrillLesson.serializer())))
    }
    /** Release notes, newest last.*/
    val whatsNew: List<app.supersax.domain.common.WhatsNewEntry> by lazy {
        load("whatsnew/whatsnew.json", kotlinx.serialization.builtins.ListSerializer(app.supersax.domain.common.WhatsNewEntry.serializer())).sortedBy { it.id }
    }
    val fingerings: app.supersax.domain.drills.FingeringChart by lazy {
        load("fingerings/alto_fingerings.json", app.supersax.domain.drills.FingeringChart.serializer())
    }
    val bundledPieces: List<app.supersax.domain.library.Piece> by lazy {
        list("pieces").mapNotNull { f ->
            runCatching { app.supersax.domain.library.Piece.from(load("pieces/$f", app.supersax.domain.library.PieceFile.serializer())) }.getOrNull()
        }
    }
}
