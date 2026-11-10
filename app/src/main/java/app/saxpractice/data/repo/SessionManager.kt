package app.saxpractice.data.repo

import app.saxpractice.data.assets.ContentRepository
import app.saxpractice.data.settings.SettingsRepository
import app.saxpractice.domain.common.BlockLogRecord
import app.saxpractice.domain.common.RankEventKind
import app.saxpractice.domain.common.RankEventRecord
import app.saxpractice.domain.common.RunRecord
import app.saxpractice.domain.common.SessionRecord
import app.saxpractice.domain.generator.Adaptation
import app.saxpractice.domain.planner.PlannedSession
import app.saxpractice.domain.planner.StepKind
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.domain.session.SessionRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

/** Wall clock and monotonic clock. */
interface AppClock {
    fun nowMs(): Long
    fun elapsedMs(): Long
}

/** Silences notifications during a session (Do Not Disturb), restoring the previous state afterwards. */
interface QuietMode {
    fun hasAccess(): Boolean
    /** Returns the previous interruption filter, or null if nothing was changed. */
    fun silence(): Int?
    fun restore(previous: Int)
}

/**
 * Holds the running session outside any screen, persists it after every change (so the timer, based
 * on monotonic time, survives the app being killed), and writes the log and applies the adaptation
 * rules when it ends.
 */
class SessionManager(
    private val scope: CoroutineScope,
    private val clock: AppClock,
    private val settings: SettingsRepository,
    private val practice: PracticeRepository,
    private val progress: ProgressRepository,
    private val content: ContentRepository,
    private val quiet: QuietMode,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _run = MutableStateFlow<SessionRun?>(null)
    val run: StateFlow<SessionRun?> = _run.asStateFlow()

    /** Planned day of week of the running session (for the log's plan adherence). */
    private var plannedDay: Int? = null

    /** Restores a session persisted before the app was killed, and the Do Not Disturb state if needed. */
    suspend fun restore() {
        val saved = settings.activeSession.first()
        if (saved != null) {
            val stored = runCatching { json.decodeFromString(StoredRun.serializer(), saved) }.getOrNull()
            if (stored != null) {
                plannedDay = stored.plannedDay
                _run.value = stored.run.catchUp(clock.elapsedMs())
                return
            }
        }
        // No session running: if a previous one was killed while silencing, restore notifications now.
        settings.dndPrevious()?.let { quiet.restore(it); settings.setDndPrevious(null) }
    }

    suspend fun start(session: PlannedSession, plannedDayOfWeek: Int?) {
        plannedDay = plannedDayOfWeek
        val s = settings.current()
        if (s.silenceNotifications && quiet.hasAccess()) {
            quiet.silence()?.let { settings.setDndPrevious(it) }
        }
        set(SessionRun.start(session, clock.elapsedMs(), clock.nowMs()))
    }

    fun update(transform: (SessionRun, Long) -> SessionRun) {
        val current = _run.value ?: return
        set(transform(current, clock.elapsedMs()))
    }

    private fun set(r: SessionRun?) {
        _run.value = r
        scope.launch {
            settings.saveActiveSession(r?.let { json.encodeToString(StoredRun.serializer(), StoredRun(it, plannedDay)) })
        }
    }

    /**
     * Saves the finished session with the tiredness rating and note, applies the adaptation rules
     * (scale tempo, unlocks, long-tone growth, stamina) and records rank events. Returns the session id.
     */
    suspend fun complete(tiredness: Int?, note: String): String? {
        val r0 = _run.value ?: return null
        val r = if (r0.finished) r0 else r0.finish(clock.elapsedMs())
        val now = clock.nowMs()
        val s = settings.current()
        val steps = r.steps
        val blocks = r.logs.mapNotNull { log ->
            val st = steps.getOrNull(log.stepIndex) ?: return@mapNotNull null
            if (st.kind == StepKind.REST) return@mapNotNull null
            BlockLogRecord(
                stepIndex = log.stepIndex, blockKind = st.blockKind, exerciseType = st.exerciseType, catalogId = st.catalogId,
                plannedSec = log.plannedSec, actualSec = log.actualSec, lipsTired = log.lipsTired, skipped = log.skipped,
                scaleKey = st.target.scaleKey, bpm = st.target.bpm, pieceId = st.target.pieceId, holdSec = st.target.holdSec,
            )
        }
        val record = SessionRecord(
            id = UUID.randomUUID().toString(),
            startedAtMs = r.startedWallMs,
            endedAtMs = now,
            programId = r.session.programId,
            dayId = r.session.dayId,
            plannedDayOfWeek = plannedDay,
            plannedSec = steps.filter { it.kind == StepKind.PLAY }.sumOf { it.durationSec },
            playedSec = blocks.sumOf { it.actualSec },
            tiredness = tiredness,
            note = note,
            reedId = s.currentReedId,
            blocks = blocks,
            runs = r.runs.map { RunRecord(it.scaleKey, it.bpm, it.clean) },
        )
        practice.saveSession(record)
        applyAdaptation(record, now)
        if (s.silenceNotifications) settings.dndPrevious()?.let { quiet.restore(it); settings.setDndPrevious(null) }
        set(null)
        return record.id
    }

    /** Abandons the running session without saving. */
    suspend fun discard() {
        settings.dndPrevious()?.let { quiet.restore(it); settings.setDndPrevious(null) }
        set(null)
    }

    private suspend fun applyAdaptation(record: SessionRecord, now: Long) {
        val cfg = content.generatorConfig.adaptation
        var player = practice.playerNow()
        record.runs.forEach { run ->
            val before = player.scale(run.scaleKey)?.consecutiveClean ?: 0
            val result = Adaptation.recordRun(player, run.scaleKey, run.bpm, run.clean, now, cfg)
            if (run.clean && before + 1 >= cfg.cleanRunsForBump) {
                progress.addRankEvent(RankEventRecord(UUID.randomUUID().toString(), now, RankEventKind.CLEAN_PAIR,
                    scaleKey = run.scaleKey, bpm = run.bpm))
            }
            player = result.state
        }
        val longTones = record.blocks.filter { it.exerciseType == ExerciseType.LONG_TONE || it.exerciseType == ExerciseType.RANGE_EXTENSION }
        if (longTones.isNotEmpty()) {
            val planned = longTones.sumOf { it.plannedSec }
            val actual = longTones.sumOf { it.actualSec }
            val tiredEarly = longTones.any { it.lipsTired } && Adaptation.tiredEarly(planned, actual, cfg)
            player = player.copy(holdTargetSec = Adaptation.nextHoldTarget(player.holdTargetSec, tiredEarly, record.tiredness, null, cfg))
            if (actual >= planned * cfg.tiredEarlyShare) {
                progress.addRankEvent(RankEventRecord(UUID.randomUUID().toString(), now, RankEventKind.LONG_TONE_BLOCK))
            }
        }
        player = Adaptation.afterSession(player, record.blocks.any { it.lipsTired }, cfg)
        practice.savePlayer(player)
    }

    @kotlinx.serialization.Serializable
    private data class StoredRun(val run: SessionRun, val plannedDay: Int?)
}
