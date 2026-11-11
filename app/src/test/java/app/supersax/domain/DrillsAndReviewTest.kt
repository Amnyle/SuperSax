package app.supersax.domain

import app.supersax.domain.common.BlockLogRecord
import app.supersax.domain.common.RankSnapshotRecord
import app.supersax.domain.common.RunRecord
import app.supersax.domain.common.SessionRecord
import app.supersax.domain.common.TunerHoldRecord
import app.supersax.domain.drills.DrillRules
import app.supersax.domain.drills.MatchDrill
import app.supersax.domain.drills.ReadingDrill
import app.supersax.domain.pitch.Letter
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.program.Block
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.ExerciseType
import app.supersax.domain.program.Program
import app.supersax.domain.program.ProgramDay
import app.supersax.domain.program.ProgramOrigin
import app.supersax.domain.review.WeeklyReviewBuilder
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrillsAndReviewTest {
    @Test fun readingLevelsWidenThenAddAccidentals() {
        val l1 = ReadingDrill.candidates(ReadingDrill.level(1))
        assertTrue(l1.all { it.alter == 0 && it.midi in 64..77 })
        assertEquals("E4", l1.minBy { it.midi }.toString()); assertEquals("F5", l1.maxBy { it.midi }.toString())
        assertTrue(ReadingDrill.candidates(ReadingDrill.level(3)).any { it.toString() == "B3" })
        val l5 = ReadingDrill.candidates(ReadingDrill.level(5))
        assertTrue(l5.any { it.alter != 0 })
        assertTrue(l5.none { it.letter == Letter.E && it.alter == 1 })
        assertTrue(l5.all { it.midi in 58..90 })
        val round = ReadingDrill.round(2, 7L)
        assertEquals(DrillRules.READING_ROUND, round.size)
        round.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
        assertEquals(round, ReadingDrill.round(2, 7L))
        assertTrue(ReadingDrill.isCorrect(SpelledPitch.parse("Bb4"), PitchClass(Letter.B, -1)))
        assertFalse(ReadingDrill.isCorrect(SpelledPitch.parse("Bb4"), PitchClass(Letter.A, 1)))
    }

    @Test fun levelAdaptation() {
        assertEquals(3, DrillRules.nextLevel(2, 14, 15, 2500))
        assertEquals(2, DrillRules.nextLevel(2, 14, 15, 6000)) // accurate but slow: stay
        assertEquals(1, DrillRules.nextLevel(2, 7, 15, 2000))
        assertEquals(5, DrillRules.nextLevel(5, 15, 15, 1000))
        assertEquals(1, DrillRules.nextLevel(1, 0, 15, 1000))
    }

    @Test fun matchRound() {
        val r = MatchDrill.round(62, 74, 3L)
        assertEquals(DrillRules.MATCH_ROUND, r.size)
        assertTrue(r.all { it in 62..74 })
    }

    @Test fun weeklyReviewFactsAndText() {
        val zone = ZoneOffset.UTC
        val monday = LocalDate.of(2026, 11, 2)
        fun at(day: Int, h: Int = 18) = monday.plusDays(day - 1L).atTime(h, 0).toInstant(zone).toEpochMilli()
        val program = Program("p", "P", ProgramOrigin.GENERATED, listOf(
            ProgramDay("d1", 1, listOf(Block("b", BlockKind.LONG_TONES, 4, emptyList()), Block("c", BlockKind.SCALES, 6, emptyList()))),
            ProgramDay("d3", 3, listOf(Block("b", BlockKind.LONG_TONES, 4, emptyList()), Block("c", BlockKind.SCALES, 6, emptyList()))),
            ProgramDay("d5", 5, listOf(Block("b", BlockKind.LONG_TONES, 4, emptyList()), Block("c", BlockKind.SCALES, 6, emptyList()))),
        ))
        val old = SessionRecord("o", at(-6), at(-6), "p", "d1", 1, plannedSec = 600, playedSec = 600,
            runs = listOf(RunRecord("MAJOR:Eb", 70, true), RunRecord("MAJOR:Eb", 70, true)))
        val mon = SessionRecord("m", at(1), at(1), "p", "d1", 1, plannedSec = 600, playedSec = 540,
            blocks = listOf(BlockLogRecord(0, BlockKind.LONG_TONES, ExerciseType.LONG_TONE, null, 240, 240),
                BlockLogRecord(2, BlockKind.SCALES, ExerciseType.SCALE, null, 360, 300, lipsTired = true)),
            runs = listOf(RunRecord("MAJOR:Eb", 75, true), RunRecord("MAJOR:Eb", 75, true), RunRecord("MAJOR:Bb", 60, false)))
        val sat = SessionRecord("s", at(6), at(6), "p", null, null, plannedSec = 300, playedSec = 300)
        val phone = SessionRecord("ph", at(2), at(2), null, null, null, phone = true, plannedSec = 300, playedSec = 300)
        val holds = listOf(TunerHoldRecord("h", at(1), 63, 8.0, 0.75, 10.0, -4.0, -10.0, -1.0, -20.0, 5.0, emptyList()))
        val snaps = listOf(RankSnapshotRecord(at(-5), 2, 1, emptyMap()), RankSnapshotRecord(at(4), 2, 2, emptyMap()))
        val r = WeeklyReviewBuilder.build(monday, monday.plusDays(6), zone, program, listOf(old, mon, sat, phone), holds, snaps)
        assertEquals(3, r.plannedSessions); assertEquals(1, r.doneSessions); assertEquals(1, r.extraSessions)
        assertEquals(30, r.plannedMinutes); assertEquals(14, r.actualMinutes)
        assertEquals(listOf(3, 5), r.skippedDays)
        assertEquals(1, r.tiredBlocks)
        assertEquals(5, r.phoneMinutes)
        val eb = r.scales.first { it.scaleKey == "MAJOR:Eb" }
        assertEquals(70, eb.before); assertEquals(75, eb.during)
        assertEquals(null, r.scales.first { it.scaleKey == "MAJOR:Bb" }.during)
        val en = WeeklyReviewBuilder.text(r, "en", NoteNaming.LETTERS)
        assertTrue("Sessions: 1 of 3 planned, plus 1 unplanned." in en, en)
        assertTrue("Planned days with no session: Wednesday, Friday." in en, en)
        assertTrue("C major: 70 → 75 BPM." in en, en)
        assertTrue("G major: no clean pair this week." in en, en)
        assertTrue("Tuner: 1 hold, in tune 75% of the time on average, typical drift -10¢." in en, en)
        assertTrue("Rank: Bronze III → Bronze II." in en, en)
        val fr = WeeklyReviewBuilder.text(r, "fr", NoteNaming.SOLFEGE)
        assertTrue("Do majeur : 70 → 75 BPM." in fr, fr)
        assertTrue("Jours prévus sans séance : mercredi, vendredi." in fr, fr)
        // Mid-week: future planned days are not "skipped" yet.
        val midweek = WeeklyReviewBuilder.build(monday, monday.plusDays(2), zone, program, listOf(mon), emptyList(), emptyList())
        assertEquals(2, midweek.plannedSessions)
        assertEquals(listOf(3), midweek.skippedDays)
    }
}
