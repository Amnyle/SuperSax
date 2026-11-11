package app.supersax.domain.rank

import app.supersax.domain.audio.HoldReport
import app.supersax.domain.common.DrillKind
import app.supersax.domain.common.DrillResultRecord
import app.supersax.domain.common.LessonProgressRecord
import app.supersax.domain.common.PieceProgressRecord
import app.supersax.domain.common.PieceStage
import app.supersax.domain.common.RankEventKind
import app.supersax.domain.common.RankEventRecord
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.common.TunerHoldRecord
import app.supersax.domain.pitch.AltoRegisters
import app.supersax.domain.pitch.Instrument
import app.supersax.domain.program.Pillar
import app.supersax.domain.scale.ScaleId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/** Everything the rank is computed from. All facts come from stored logs. */
data class RankEvents(
    val rankEvents: List<RankEventRecord> = emptyList(),
    val holds: List<TunerHoldRecord> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    /** ISO days of week planned by the active program. */
    val plannedDays: Set<Int> = emptySet(),
    val pieceProgress: List<PieceProgressRecord> = emptyList(),
    /** Difficulty of each piece id (from the library). */
    val pieceDifficulty: Map<String, Int> = emptyMap(),
    val drills: List<DrillResultRecord> = emptyList(),
    val lessons: List<LessonProgressRecord> = emptyList(),
    val zone: ZoneId = ZoneId.of("UTC"),
    val instrument: Instrument = Instrument.ALTO_SAX,
)

/** What a requirement is about; the UI turns it into a sentence. */
enum class ReqKind {
    SCALES_AT_BPM, LOWEST_NOTE, HIGHEST_NOTE, HIGHEST_NOTE_VERIFIED, OVERTONE_MILESTONE,
    LONG_TONE_BLOCKS, HOLD_NOTES, HOLD_REGISTERS, ADHERENCE, SESSIONS_PER_WEEK, PIECE_DIFFICULTY,
    READING_ROUND, SCALE_ROUND, LESSONS, ANY_ACTIVITY,
}

/**
 * One requirement of a tier, with where the player stands. [target] and [current] are in the
 * requirement's unit (count, BPM, MIDI, percent...). Extra parameters ([family], [a], [b], [c]) let the
 * UI explain it: for SCALES_AT_BPM a = minimum BPM; for HOLD_NOTES a = seconds, b = tolerance cents,
 * c = in-tune share; for READING_ROUND a = accuracy, b = mean ms; for SCALE_ROUND a = accuracy;
 * for LESSONS a = minimum score.
 */
data class Requirement(
    val kind: ReqKind,
    val target: Double,
    val current: Double,
    val met: Boolean,
    val family: ScaleFamily? = null,
    val a: Double? = null,
    val b: Double? = null,
    val c: Double? = null,
)

data class PillarResult(
    val pillar: Pillar,
    /** Tier counted in the overall rank (Musicianship after the cap). */
    val tier: Int,
    /** Tier before the Musicianship cap (same as [tier] for the others). */
    val rawTier: Int,
    /** Requirements of the next tier with progress; empty at Master. */
    val next: List<Requirement>,
)

data class RankResult(
    val pillars: Map<Pillar, PillarResult>,
    val average: Double,
    /** 0 = Unranked, 1 = Iron .. 8 = Master. */
    val overallTier: Int,
    /** 0 = IV (lowest) .. 3 = I (highest); -1 for Master and Unranked. */
    val division: Int,
    val limiter: Pillar,
    /** Cap applied to Musicianship: floor(average of the other five) + 1. */
    val musicianshipCap: Int,
)

/**
 * The rank calculation: a pure function of the logs, the config and the time.
 *
 * - A pillar's tier is the highest tier (1..8) whose requirements are all met; 0 = Unranked.
 * - Musicianship counts at most floor(average of the other five) + 1.
 * - Overall = floor(average of the six); the fractional part gives the division in four equal
 *   bands (IV..I); an average of 8 is Master without division.
 * - The limiter is the lowest pillar, ties broken in the order Sound, Technique, Range,
 *   Repertoire, Consistency, Musicianship.
 * - Only Consistency is rolling; skill pillars use all history, so they never decay.
 */
object RankCalculator {

    fun computeRank(events: RankEvents, config: RankConfig, now: Long): RankResult {
        val raw = Pillar.entries.associateWith { p -> evaluate(p, events, config, now) }
        val others = Pillar.entries.filter { it != Pillar.MUSICIANSHIP }.map { raw.getValue(it).first }
        val cap = floor(others.average()).toInt() + 1
        val pillars = raw.mapValues { (p, r) ->
            val effective = if (p == Pillar.MUSICIANSHIP) minOf(r.first, cap) else r.first
            PillarResult(p, effective, r.first, r.second)
        }
        val avg = pillars.values.map { it.tier }.average()
        val tier = floor(avg + 1e-9).toInt().coerceIn(0, 8)
        val division = if (tier == 8 || tier == 0) -1 else floor((avg - tier) * 4 + 1e-9).toInt().coerceIn(0, 3)
        val limiter = Pillar.entries.minBy { pillars.getValue(it).tier * 10 + it.ordinal }
        return RankResult(pillars, avg, tier, division, limiter, cap)
    }

    /** Tier and next-tier requirements of one pillar. */
    private fun evaluate(p: Pillar, e: RankEvents, c: RankConfig, now: Long): Pair<Int, List<Requirement>> {
        val reqsFor: (Int) -> List<Requirement> = when (p) {
            Pillar.TECHNIQUE -> { t -> technique(t, e, c) }
            Pillar.RANGE -> { t -> range(t, e, c) }
            Pillar.SOUND -> { t -> sound(t, e, c) }
            Pillar.CONSISTENCY -> { t -> consistency(t, e, c, now) }
            Pillar.REPERTOIRE -> { t -> repertoire(t, e, c) }
            Pillar.MUSICIANSHIP -> { t -> musicianship(t, e, c) }
        }
        val tier = (1..8).filter { t -> reqsFor(t).let { it.isNotEmpty() && it.all { r -> r.met } } }.maxOrNull() ?: 0
        return tier to if (tier >= 8) emptyList() else reqsFor(tier + 1)
    }

    // --- Technique ---------------------------------------------------------------------------

    /** Best clean-pair BPM per scale key. */
    fun bestCleanBpm(e: RankEvents): Map<String, Int> =
        e.rankEvents.filter { it.kind == RankEventKind.CLEAN_PAIR && it.scaleKey != null && it.bpm != null }
            .groupBy { it.scaleKey!! }.mapValues { (_, l) -> l.maxOf { it.bpm!! } }

    private fun technique(t: Int, e: RankEvents, c: RankConfig): List<Requirement> {
        val tierCfg = c.technique.firstOrNull { it.tier == t } ?: return emptyList()
        val best = bestCleanBpm(e)
        return tierCfg.requirements.map { r ->
            val n = best.count { (key, bpm) ->
                val id = runCatching { ScaleId.parse(key) }.getOrNull() ?: return@count false
                ScaleFamily.of(id.type) == r.family && bpm >= r.minBpm
            }
            Requirement(ReqKind.SCALES_AT_BPM, r.count.toDouble(), n.toDouble(), n >= r.count, r.family, r.minBpm.toDouble())
        }
    }

    // --- Range -------------------------------------------------------------------------------

    private fun range(t: Int, e: RankEvents, c: RankConfig): List<Requirement> {
        val tierCfg = c.range.firstOrNull { it.tier == t } ?: return emptyList()
        val claims = e.rankEvents.filter { it.kind == RankEventKind.RANGE_NOTE && it.writtenMidi != null }
        val lowest = claims.minOfOrNull { it.writtenMidi!! }
        val highestAny = claims.maxOfOrNull { it.writtenMidi!! }
        val highestVerified = claims.filter { it.verified }.maxOfOrNull { it.writtenMidi!! }
        val reqs = mutableListOf(
            Requirement(ReqKind.LOWEST_NOTE, tierCfg.lowAtMost.toDouble(), (lowest ?: 0).toDouble(), lowest != null && lowest <= tierCfg.lowAtMost),
        )
        if (t >= c.rangeVerifiedFromTier) {
            val overtone = e.rankEvents.any { it.kind == RankEventKind.OVERTONE_MILESTONE && it.verified }
            reqs += Requirement(ReqKind.OVERTONE_MILESTONE, 1.0, if (overtone) 1.0 else 0.0, overtone)
            reqs += Requirement(ReqKind.HIGHEST_NOTE_VERIFIED, tierCfg.highAtLeast.toDouble(), (highestVerified ?: 0).toDouble(),
                highestVerified != null && highestVerified >= tierCfg.highAtLeast)
        } else {
            reqs += Requirement(ReqKind.HIGHEST_NOTE, tierCfg.highAtLeast.toDouble(), (highestAny ?: 0).toDouble(),
                highestAny != null && highestAny >= tierCfg.highAtLeast)
        }
        return reqs
    }

    // --- Sound -------------------------------------------------------------------------------

    private fun sound(t: Int, e: RankEvents, c: RankConfig): List<Requirement> {
        val s = c.sound.firstOrNull { it.tier == t } ?: return emptyList()
        if (s.longToneBlocks > 0) {
            val n = e.rankEvents.count { it.kind == RankEventKind.LONG_TONE_BLOCK }
            return listOf(Requirement(ReqKind.LONG_TONE_BLOCKS, s.longToneBlocks.toDouble(), n.toDouble(), n >= s.longToneBlocks))
        }
        // Re-evaluated from the stored traces at this tier's tolerance (thresholds can change later).
        val good = e.holds.filter { h ->
            h.stableSec >= s.seconds && HoldReport.inTuneShareAt(h.trace, s.toleranceCents) >= s.inTuneShare
        }
        val notes = good.map { it.refMidi }.toSet()
        val reqs = mutableListOf(
            Requirement(ReqKind.HOLD_NOTES, s.distinctNotes.toDouble(), notes.size.toDouble(), notes.size >= s.distinctNotes,
                a = s.seconds, b = s.toleranceCents, c = s.inTuneShare),
        )
        if (s.allRegisters) {
            val regs = notes.map {
                when (AltoRegisters.registerOf(e.instrument.concertToWrittenMidi(it))) {
                    AltoRegisters.Register.LOW -> 0
                    AltoRegisters.Register.MIDDLE -> 1
                    else -> 2
                }
            }.toSet()
            reqs += Requirement(ReqKind.HOLD_REGISTERS, 3.0, regs.size.toDouble(), regs.size >= 3, a = s.seconds, b = s.toleranceCents)
        }
        return reqs
    }

    // --- Consistency -------------------------------------------------------------------------

    /** Adherence (0..1) and sessions per week over the rolling window. */
    fun adherence(e: RankEvents, c: RankConfig, now: Long): Pair<Double, Double> {
        val zone = e.zone
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val windowStart = today.minusDays(c.consistency.windowDays - 1L)
        val sax = e.sessions.filter { !it.phone }
        val first = sax.minOfOrNull { it.startedAtMs }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return 0.0 to 0.0
        val start = if (first.isAfter(windowStart)) first else windowStart
        val playedDates: Set<LocalDate> = sax.map { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() }.toSet()
        var planned = 0
        var done = 0
        var d = start
        while (!d.isAfter(today)) {
            val isPlanned = d.dayOfWeek.value in e.plannedDays
            val played = d in playedDates
            // Today counts only once it has been played (the day is not over).
            if (isPlanned && (d != today || played)) {
                planned++
                if (played) done++
            }
            d = d.plusDays(1)
        }
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1
        val weeks = maxOf(1.0, days / 7.0)
        val inWindow = sax.count { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate().let { x -> !x.isBefore(start) && !x.isAfter(today) } }
        val adherence = if (planned == 0) 0.0 else (done.toDouble() / planned).coerceAtMost(1.0)
        return adherence to inWindow / weeks
    }

    private fun consistency(t: Int, e: RankEvents, c: RankConfig, now: Long): List<Requirement> {
        val threshold = c.consistency.thresholds.getOrNull(t - 1) ?: return emptyList()
        val (adh, perWeek) = adherence(e, c, now)
        val pct = adh * 100
        val reqs = mutableListOf(Requirement(ReqKind.ADHERENCE, threshold.toDouble(), pct, pct + 1e-9 >= threshold))
        if (t >= c.consistency.sessionsFloorFromTier) {
            reqs += Requirement(ReqKind.SESSIONS_PER_WEEK, c.consistency.minSessionsPerWeek, perWeek, perWeek + 1e-9 >= c.consistency.minSessionsPerWeek)
        }
        return reqs
    }

    // --- Repertoire --------------------------------------------------------------------------

    fun hardestCleanPiece(e: RankEvents): Int =
        e.pieceProgress.filter { it.stage == PieceStage.CLEAN_AT_TEMPO && it.sectionId.isEmpty() }
            .maxOfOrNull { e.pieceDifficulty[it.pieceId] ?: 0 } ?: 0

    private fun repertoire(t: Int, e: RankEvents, c: RankConfig): List<Requirement> {
        val r = c.repertoire.firstOrNull { it.tier == t } ?: return emptyList()
        val hardest = hardestCleanPiece(e)
        return listOf(Requirement(ReqKind.PIECE_DIFFICULTY, r.minDifficulty.toDouble(), hardest.toDouble(), hardest >= r.minDifficulty))
    }

    // --- Musicianship ------------------------------------------------------------------------

    private fun musicianship(t: Int, e: RankEvents, c: RankConfig): List<Requirement> {
        val m = c.musicianship.firstOrNull { it.tier == t } ?: return emptyList()
        val reqs = mutableListOf<Requirement>()
        if (m.anyActivity) {
            val n = e.drills.size + e.lessons.size
            reqs += Requirement(ReqKind.ANY_ACTIVITY, 1.0, n.toDouble(), n >= 1)
        }
        if (m.readingLevel > 0) {
            val rounds = e.drills.filter { it.kind == DrillKind.READING && it.level >= m.readingLevel && it.questions > 0 }
            val best = rounds.filter { it.meanResponseMs <= m.readingMeanMs }.maxOfOrNull { it.correct.toDouble() / it.questions } ?: 0.0
            reqs += Requirement(ReqKind.READING_ROUND, m.readingLevel.toDouble(), best * 100, best + 1e-9 >= m.readingAccuracy,
                a = m.readingAccuracy * 100, b = m.readingMeanMs.takeIf { it != Long.MAX_VALUE }?.toDouble())
        }
        if (m.scaleLevel > 0 || m.scaleAccuracy > 0) {
            val best = e.drills.filter { it.kind == DrillKind.SCALE_RECOGNITION && it.level >= m.scaleLevel && it.questions > 0 }
                .maxOfOrNull { it.correct.toDouble() / it.questions } ?: 0.0
            reqs += Requirement(ReqKind.SCALE_ROUND, m.scaleLevel.toDouble(), best * 100, best + 1e-9 >= m.scaleAccuracy, a = m.scaleAccuracy * 100)
        }
        if (m.lessons > 0) {
            val n = e.lessons.count { it.quizScore + 1e-9 >= m.lessonMinScore }
            reqs += Requirement(ReqKind.LESSONS, m.lessons.toDouble(), n.toDouble(), n >= m.lessons, a = m.lessonMinScore * 100)
        }
        return reqs
    }
}
