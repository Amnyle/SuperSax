package app.supersax.domain.review

import app.supersax.domain.common.RankSnapshotRecord
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.common.TunerHoldRecord
import app.supersax.domain.goals.GoalEvaluator
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Program
import app.supersax.domain.rank.RankNames
import app.supersax.domain.scale.ScaleNames
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

data class BlockStat(val kind: BlockKind, val plannedMin: Int, val actualMin: Int)

/** Best clean-pair tempo of a scale before the week and during it (null = none). */
data class ScaleTempo(val scaleKey: String, val before: Int?, val during: Int?)

/** Facts of one week (Monday to Sunday). */
data class WeeklyReview(
    val weekStart: LocalDate,
    val plannedSessions: Int,
    val doneSessions: Int,
    val extraSessions: Int,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val perBlock: List<BlockStat>,
    val skippedDays: List<Int>,
    val tiredBlocks: Int,
    val scales: List<ScaleTempo>,
    val holdCount: Int,
    val avgInTune: Double?,
    val avgDrift: Double?,
    val rankBefore: RankSnapshotRecord?,
    val rankAfter: RankSnapshotRecord?,
    val phoneMinutes: Int,
)

/**
 * Builds the weekly review from the logs (pure), and its plain, direct text in English or French.
 * Planned = what the active program planned for each day of the week; days after [today] are not
 * counted as skipped yet.
 */
object WeeklyReviewBuilder {

    fun build(
        weekStart: LocalDate,
        today: LocalDate,
        zone: ZoneId,
        program: Program?,
        sessions: List<SessionRecord>,
        holds: List<TunerHoldRecord>,
        snapshots: List<RankSnapshotRecord>,
    ): WeeklyReview {
        val startMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val week = sessions.filter { it.startedAtMs in startMs until endMs }
        val sax = week.filter { !it.phone }
        val plannedDays = program?.days?.map { it.dayOfWeek }?.toSet() ?: emptySet()
        val lastCounted = if (today < weekStart.plusDays(6)) today else weekStart.plusDays(6)
        val elapsedDays = (1..7).filter { weekStart.plusDays(it - 1L) <= lastCounted }
        val plannedSoFar = plannedDays.filter { it in elapsedDays }
        val daysPlayed = sax.map { dayOf(it.startedAtMs).dayOfWeek.value }.toSet()
        val done = plannedSoFar.count { it in daysPlayed }
        val extra = sax.count { dayOf(it.startedAtMs).dayOfWeek.value !in plannedDays }

        val plannedPerBlock = BlockKind.entries.associateWith { k ->
            program?.days?.filter { it.dayOfWeek in plannedSoFar }?.sumOf { d -> d.blocks.filter { it.kind == k }.sumOf { it.minutes } } ?: 0
        }
        val actualPerBlock = BlockKind.entries.associateWith { k -> sax.flatMap { it.blocks }.filter { it.blockKind == k }.sumOf { it.actualSec } / 60 }
        val perBlock = BlockKind.entries.map { BlockStat(it, plannedPerBlock.getValue(it), actualPerBlock.getValue(it)) }
            .filter { it.plannedMin > 0 || it.actualMin > 0 }

        val before = sessions.filter { it.startedAtMs < startMs }.flatMap { GoalEvaluator.cleanPairs(it) }.groupBy({ it.first }, { it.second })
        val during = week.flatMap { GoalEvaluator.cleanPairs(it) }.groupBy({ it.first }, { it.second })
        val practisedKeys = (week.flatMap { s -> s.runs.map { it.scaleKey } } + during.keys).toSortedSet()
        val scales = practisedKeys.map { ScaleTempo(it, before[it]?.maxOrNull(), during[it]?.maxOrNull()) }

        val weekHolds = holds.filter { it.atMs in startMs until endMs }
        return WeeklyReview(
            weekStart = weekStart,
            plannedSessions = plannedSoFar.size,
            doneSessions = done,
            extraSessions = extra,
            plannedMinutes = plannedPerBlock.values.sum(),
            actualMinutes = sax.sumOf { it.playedSec } / 60,
            perBlock = perBlock,
            skippedDays = plannedSoFar.filter { it !in daysPlayed }.sorted(),
            tiredBlocks = sax.sumOf { s -> s.blocks.count { it.lipsTired } },
            scales = scales,
            holdCount = weekHolds.size,
            avgInTune = weekHolds.takeIf { it.isNotEmpty() }?.map { it.inTuneShare }?.average(),
            avgDrift = weekHolds.takeIf { it.isNotEmpty() }?.map { it.driftCents }?.average(),
            rankBefore = snapshots.filter { it.atMs < startMs }.maxByOrNull { it.atMs },
            rankAfter = snapshots.filter { it.atMs < endMs }.maxByOrNull { it.atMs },
            phoneMinutes = week.filter { it.phone }.sumOf { it.playedSec } / 60,
        )
    }

    private fun blockName(k: BlockKind, fr: Boolean) = when (k) {
        BlockKind.BREATHING -> if (fr) "Respiration" else "Breathing"
        BlockKind.LONG_TONES -> if (fr) "Notes tenues" else "Long tones"
        BlockKind.SCALES -> if (fr) "Gammes" else "Scales"
        BlockKind.NEW_MATERIAL -> if (fr) "Nouveau morceau" else "New material"
        BlockKind.PLAY_FOR_FUN -> if (fr) "Plaisir" else "Play for fun"
    }

    private fun dayName(d: Int, fr: Boolean) =
        java.time.DayOfWeek.of(d).getDisplayName(java.time.format.TextStyle.FULL, if (fr) Locale.FRENCH else Locale.ENGLISH)

    private fun signed(v: Double): String { val c = v.roundToInt(); return if (c > 0) "+$c" else "$c" }

    /** Plain text, one fact per line. Meant to be pasted to an assistant or sent to a teacher. */
    fun text(r: WeeklyReview, language: String, naming: NoteNaming): String {
        val fr = language.startsWith("fr")
        val locale = if (fr) Locale.FRENCH else Locale.ENGLISH
        val date = r.weekStart.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
        val lines = mutableListOf<String>()
        lines += if (fr) "Super Sax – semaine du $date" else "Super Sax – week of $date"
        lines += if (fr) "Séances : ${r.doneSessions} sur ${r.plannedSessions} prévues" + (if (r.extraSessions > 0) ", plus ${r.extraSessions} hors plan." else ".")
        else "Sessions: ${r.doneSessions} of ${r.plannedSessions} planned" + (if (r.extraSessions > 0) ", plus ${r.extraSessions} unplanned." else ".")
        lines += if (fr) "Minutes de saxophone : ${r.actualMinutes} sur ${r.plannedMinutes} prévues."
        else "Sax minutes: ${r.actualMinutes} of ${r.plannedMinutes} planned."
        if (r.skippedDays.isNotEmpty()) {
            val days = r.skippedDays.joinToString(", ") { dayName(it, fr) }
            lines += if (fr) "Jours prévus sans séance : $days." else "Planned days with no session: $days."
        }
        r.perBlock.forEach { b ->
            lines += if (fr) "${blockName(b.kind, true)} : ${b.actualMin} sur ${b.plannedMin} min."
            else "${blockName(b.kind, false)}: ${b.actualMin} of ${b.plannedMin} min."
        }
        if (r.tiredBlocks > 0) lines += if (fr) "Blocs arrêtés pour lèvres fatiguées : ${r.tiredBlocks}." else "Blocks ended with tired lips: ${r.tiredBlocks}."
        r.scales.forEach { s ->
            val name = ScaleNames.written(s.scaleKey, naming, language)
            lines += when {
                s.during == null -> if (fr) "$name : aucune paire propre cette semaine." else "$name: no clean pair this week."
                s.before == null -> if (fr) "$name : première paire propre à ${s.during} BPM." else "$name: first clean pair at ${s.during} BPM."
                s.during > s.before -> if (fr) "$name : ${s.before} → ${s.during} BPM." else "$name: ${s.before} → ${s.during} BPM."
                else -> if (fr) "$name : ${s.during} BPM, pas plus rapide qu'avant (${s.before})." else "$name: ${s.during} BPM, not faster than before (${s.before})."
            }
        }
        if (r.holdCount == 0) lines += if (fr) "Accordeur : aucune tenue mesurée." else "Tuner: no holds measured."
        else {
            val pct = ((r.avgInTune ?: 0.0) * 100).roundToInt()
            val n = r.holdCount
            lines += if (fr) "Accordeur : $n ${if (n == 1) "tenue" else "tenues"}, juste ${pct} % du temps en moyenne, dérive typique ${signed(r.avgDrift ?: 0.0)}¢."
            else "Tuner: $n ${if (n == 1) "hold" else "holds"}, in tune $pct% of the time on average, typical drift ${signed(r.avgDrift ?: 0.0)}¢."
        }
        val after = r.rankAfter
        if (after != null) {
            val now = RankNames.full(after.overallTier, after.division, language)
            val before = r.rankBefore
            lines += when {
                before == null -> if (fr) "Rang : $now." else "Rank: $now."
                before.overallTier == after.overallTier && before.division == after.division -> if (fr) "Rang : inchangé ($now)." else "Rank: unchanged ($now)."
                else -> "${if (fr) "Rang" else "Rank"}: ${RankNames.full(before.overallTier, before.division, language)} → $now."
            }
        }
        if (r.phoneMinutes > 0) lines += if (fr) "Exercices sur téléphone : ${r.phoneMinutes} min." else "Phone drills: ${r.phoneMinutes} min."
        return lines.joinToString("\n")
    }
}
