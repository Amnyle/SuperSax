package app.supersax.domain

import app.supersax.TestAssets
import app.supersax.domain.common.DrillKind
import app.supersax.domain.drills.ScaleRecognition
import app.supersax.domain.common.DrillResultRecord
import app.supersax.domain.common.LessonProgressRecord
import app.supersax.domain.common.PieceProgressRecord
import app.supersax.domain.common.PieceStage
import app.supersax.domain.common.RankEventKind
import app.supersax.domain.common.RankEventRecord
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.common.TunerHoldRecord
import app.supersax.domain.program.Pillar
import app.supersax.domain.rank.RankCalculator
import app.supersax.domain.rank.RankConfig
import app.supersax.domain.rank.RankEvents
import app.supersax.domain.rank.ReqKind
import app.supersax.domain.scale.ScaleId
import app.supersax.domain.scale.ScaleType
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RankTest {
    private val config: RankConfig = TestAssets.json.decodeFromString(RankConfig.serializer(), TestAssets.text("rank/rank_config.json"))
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 10) // a Tuesday
    private val now = today.atTime(20, 0).toInstant(zone).toEpochMilli()
    private var n = 0
    private fun id() = "e${n++}"

    private fun rank(e: RankEvents) = RankCalculator.computeRank(e.copy(zone = zone), config, now)
    private fun tier(e: RankEvents, p: Pillar) = rank(e).pillars.getValue(p).rawTier

    private fun pairs(type: ScaleType, count: Int, bpm: Int) = ScaleId.all(type).take(count).map {
        RankEventRecord(id(), 0, RankEventKind.CLEAN_PAIR, scaleKey = it.key, bpm = bpm)
    }
    private fun note(written: Int, verified: Boolean) = RankEventRecord(id(), 0, RankEventKind.RANGE_NOTE, writtenMidi = written, verified = verified)
    private fun hold(concert: Int, sec: Double, trace: List<Float>) =
        TunerHoldRecord(id(), 0, concert, sec, 1.0, 10.0, 0.0, 0.0, 0.0, 0.0, 0.0, trace)
    private fun steady(c: Float = 3f) = List(100) { if (it % 2 == 0) c else -c }

    @Test fun unranked() {
        val r = rank(RankEvents())
        assertTrue(r.pillars.values.all { it.tier == 0 })
        assertEquals(0, r.overallTier); assertEquals(-1, r.division)
        assertEquals(Pillar.SOUND, r.limiter) // tie-break order starts with Sound
        assertTrue(r.pillars.values.all { it.next.isNotEmpty() })
    }

    @Test fun technique() {
        assertEquals(1, tier(RankEvents(pairs(ScaleType.MAJOR, 1, 50)), Pillar.TECHNIQUE))
        assertEquals(2, tier(RankEvents(pairs(ScaleType.MAJOR, 3, 70)), Pillar.TECHNIQUE))
        assertEquals(1, tier(RankEvents(pairs(ScaleType.MAJOR, 3, 65)), Pillar.TECHNIQUE))
        assertEquals(4, tier(RankEvents(pairs(ScaleType.MAJOR, 12, 90)), Pillar.TECHNIQUE))
        assertEquals(5, tier(RankEvents(pairs(ScaleType.MAJOR, 12, 90) + pairs(ScaleType.NATURAL_MINOR, 12, 100)), Pillar.TECHNIQUE))
        val all150 = ScaleType.entries.flatMap { pairs(it, 12, 150) }
        assertEquals(8, tier(RankEvents(all150), Pillar.TECHNIQUE))
        // Next-tier transparency: 4 of 6 majors at 80.
        val next = rank(RankEvents(pairs(ScaleType.MAJOR, 4, 80))).pillars.getValue(Pillar.TECHNIQUE).next.single()
        assertEquals(ReqKind.SCALES_AT_BPM, next.kind); assertEquals(6.0, next.target); assertEquals(4.0, next.current); assertFalse(next.met)
    }

    @Test fun rangeAndVerifiedAltissimo() {
        assertEquals(1, tier(RankEvents(listOf(note(64, false), note(74, false))), Pillar.RANGE))
        assertEquals(3, tier(RankEvents(listOf(note(58, false), note(86, false))), Pillar.RANGE))
        // Self-reported altissimo G does not count.
        assertEquals(4, tier(RankEvents(listOf(note(58, false), note(91, false))), Pillar.RANGE))
        // Verified G but no overtone milestone: still Gold.
        assertEquals(4, tier(RankEvents(listOf(note(58, false), note(90, false), note(91, true))), Pillar.RANGE))
        val overtone = RankEventRecord(id(), 0, RankEventKind.OVERTONE_MILESTONE, verified = true)
        assertEquals(5, tier(RankEvents(listOf(note(58, false), note(90, false), note(91, true), overtone)), Pillar.RANGE))
        assertEquals(6, tier(RankEvents(listOf(note(58, false), note(93, true), overtone)), Pillar.RANGE))
    }

    @Test fun soundTiersAndRegisters() {
        val block = RankEventRecord(id(), 0, RankEventKind.LONG_TONE_BLOCK)
        assertEquals(1, tier(RankEvents(listOf(block)), Pillar.SOUND))
        // Three notes held 8 s within ±25: Bronze.
        val three = listOf(63, 65, 67).map { hold(it, 9.0, steady(10f)) }
        assertEquals(2, tier(RankEvents(listOf(block), holds = three), Pillar.SOUND))
        // Only two distinct notes: not Bronze.
        assertEquals(1, tier(RankEvents(listOf(block), holds = three.take(2) + hold(63, 9.0, steady())), Pillar.SOUND))
        // 16 s within ±15 on three middle notes: Silver only, Gold needs all registers.
        val middle = listOf(66, 68, 70).map { hold(it, 17.0, steady(5f)) } // written D♯5..G5
        assertEquals(3, tier(RankEvents(listOf(block), holds = middle), Pillar.SOUND))
        val spread = listOf(52, 66, 78).map { hold(it, 17.0, steady(5f)) } // written C♯4, D♯5, E♭6
        assertEquals(4, tier(RankEvents(listOf(block), holds = spread), Pillar.SOUND))
    }

    @Test fun recomputedFromStoredTracesAfterConfigChange() {
        val holds = listOf(63, 65, 67).map { hold(it, 9.0, steady(12f)) } // ±12 cents wobble
        val e = RankEvents(listOf(RankEventRecord(id(), 0, RankEventKind.LONG_TONE_BLOCK)), holds = holds)
        assertEquals(2, tier(e, Pillar.SOUND))
        val stricter = config.copy(sound = config.sound.map { if (it.tier == 2) it.copy(toleranceCents = 10.0) else it })
        assertEquals(1, RankCalculator.computeRank(e.copy(zone = zone), stricter, now).pillars.getValue(Pillar.SOUND).rawTier)
    }

    private fun session(date: LocalDate, phone: Boolean = false) = SessionRecord(id(), date.atTime(18, 0).toInstant(zone).toEpochMilli(),
        date.atTime(18, 20).toInstant(zone).toEpochMilli(), "p", null, date.dayOfWeek.value, phone, 1200, 1200)

    @Test fun consistencyWindowAndFloor() {
        val days = (0 until 56).map { today.minusDays(it.toLong()) }
        val mwf = setOf(1, 3, 5)
        val all = days.filter { it.dayOfWeek.value in mwf }.map { session(it) }
        assertEquals(8, tier(RankEvents(sessions = all, plannedDays = mwf), Pillar.CONSISTENCY))
        // Two of three planned days: 67 percent and 2 sessions a week → capped below Gold by the floor.
        val two = days.filter { it.dayOfWeek.value in setOf(1, 3) }.map { session(it) }
        val r = rank(RankEvents(sessions = two, plannedDays = mwf)).pillars.getValue(Pillar.CONSISTENCY)
        assertEquals(3, r.rawTier)
        assertTrue(r.next.any { it.kind == ReqKind.SESSIONS_PER_WEEK && !it.met })
        // Planning less cannot game it: one planned day a week, always played → 100 percent but 1/week.
        val mondays = days.filter { it.dayOfWeek.value == 1 }.map { session(it) }
        assertEquals(3, tier(RankEvents(sessions = mondays, plannedDays = setOf(1)), Pillar.CONSISTENCY))
        // Rolling: sessions older than 56 days do not count.
        val old = (60 until 120).map { today.minusDays(it.toLong()) }.filter { it.dayOfWeek.value in mwf }.map { session(it) }
        val recent = listOf(session(today.minusDays(6)))
        val (adh, _) = RankCalculator.adherence(RankEvents(sessions = old + recent, plannedDays = mwf, zone = zone), config, now)
        assertTrue(adh < 0.1, "adherence $adh")
        // Phone sessions do not count.
        assertEquals(0, tier(RankEvents(sessions = days.map { session(it, phone = true) }, plannedDays = mwf), Pillar.CONSISTENCY))
    }

    @Test fun repertoireBands() {
        fun rep(d: Int) = tier(RankEvents(pieceProgress = listOf(PieceProgressRecord("x", "", PieceStage.CLEAN_AT_TEMPO, 0)), pieceDifficulty = mapOf("x" to d)), Pillar.REPERTOIRE)
        assertEquals(listOf(1, 2, 3, 3, 4, 5, 6, 7, 7, 8), (1..10).map { rep(it) })
        val learning = RankEvents(pieceProgress = listOf(PieceProgressRecord("x", "", PieceStage.PLAYABLE, 0)), pieceDifficulty = mapOf("x" to 9))
        assertEquals(0, tier(learning, Pillar.REPERTOIRE))
    }

    @Test fun musicianshipAndCap() {
        val drills = listOf(
            DrillResultRecord(id(), 0, DrillKind.READING, 5, 20, 20, 1500),
            DrillResultRecord(id(), 0, DrillKind.SCALE_RECOGNITION, ScaleRecognition.LEVELS, 10, 10, 3000),
        )
        val lessons = (1..13).map { LessonProgressRecord("l$it", 0, 1.0) }
        val e = RankEvents(drills = drills, lessons = lessons)
        val r = rank(e)
        val m = r.pillars.getValue(Pillar.MUSICIANSHIP)
        assertEquals(8, m.rawTier)
        // Others are all 0 → cap 1.
        assertEquals(1, r.musicianshipCap); assertEquals(1, m.tier)
        assertEquals(1, tier(RankEvents(drills = listOf(DrillResultRecord(id(), 0, DrillKind.READING, 1, 15, 5, 9000))), Pillar.MUSICIANSHIP))
    }

    @Test fun overallAverageDivisionsAndLimiter() {
        // Technique 2, Range 3, Sound 1, Repertoire 2, Consistency 0, Musicianship 1 → avg 9/6 = 1.5 → Iron II.
        val e = RankEvents(
            rankEvents = pairs(ScaleType.MAJOR, 3, 70) + listOf(note(58, false), note(86, false), RankEventRecord(id(), 0, RankEventKind.LONG_TONE_BLOCK)),
            pieceProgress = listOf(PieceProgressRecord("x", "", PieceStage.CLEAN_AT_TEMPO, 0)), pieceDifficulty = mapOf("x" to 2),
            drills = listOf(DrillResultRecord(id(), 0, DrillKind.READING, 1, 15, 10, 5000)),
        )
        val r = rank(e)
        assertEquals(listOf(1, 2, 3, 2, 0, 1), Pillar.entries.map { r.pillars.getValue(it).tier })
        assertEquals(1.5, r.average, 1e-9)
        assertEquals(1, r.overallTier); assertEquals(2, r.division) // II
        assertEquals(Pillar.CONSISTENCY, r.limiter)
        // Tie between Sound and Musicianship (both 1, nothing lower): Sound wins.
        val tie = rank(e.copy(sessions = (0 until 56).map { today.minusDays(it.toLong()) }.map { session(it) }, plannedDays = (1..7).toSet()))
        assertEquals(Pillar.SOUND, tie.limiter)
        // Average exactly 8 → Master without division.
        val max = RankEvents(
            rankEvents = ScaleType.entries.flatMap { pairs(it, 12, 150) } + listOf(note(58, false), note(98, true),
                RankEventRecord(id(), 0, RankEventKind.OVERTONE_MILESTONE, verified = true)),
            holds = listOf(52, 66, 78).map { hold(it, 40.0, steady(2f)) },
            sessions = (0 until 56).map { today.minusDays(it.toLong()) }.map { session(it) }, plannedDays = (1..7).toSet(),
            pieceProgress = listOf(PieceProgressRecord("x", "", PieceStage.CLEAN_AT_TEMPO, 0)), pieceDifficulty = mapOf("x" to 10),
            drills = listOf(DrillResultRecord(id(), 0, DrillKind.READING, 5, 20, 20, 1500), DrillResultRecord(id(), 0, DrillKind.SCALE_RECOGNITION, ScaleRecognition.LEVELS, 10, 10, 3000)),
            lessons = (1..13).map { LessonProgressRecord("l$it", 0, 1.0) },
        )
        val m = rank(max)
        assertEquals(8, m.overallTier); assertEquals(-1, m.division)
        assertTrue(m.pillars.values.all { it.next.isEmpty() })
    }
}
