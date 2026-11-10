package app.saxpractice.domain

import app.saxpractice.domain.planner.PlannedSession
import app.saxpractice.domain.planner.PlannedStep
import app.saxpractice.domain.planner.StepKind
import app.saxpractice.domain.program.BlockKind
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.domain.session.SessionRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionRunTest {
    private val session = PlannedSession("p", "d", listOf(
        PlannedStep(0, StepKind.PLAY, 120, "b0", BlockKind.LONG_TONES, "e0", ExerciseType.LONG_TONE),
        PlannedStep(1, StepKind.PLAY, 60, "b0", BlockKind.LONG_TONES, "e1", ExerciseType.LONG_TONE),
        PlannedStep(2, StepKind.REST, 60),
        PlannedStep(3, StepKind.PLAY, 180, "b1", BlockKind.SCALES, "e2", ExerciseType.SCALE),
    ))

    @Test fun countdownUsesMonotonicTimeAndPauses() {
        var r = SessionRun.start(session, nowMs = 1_000, wallMs = 0)
        assertEquals(120_000, r.remainingMs(1_000))
        assertEquals(90_000, r.remainingMs(31_000))
        r = r.pause(31_000)
        assertEquals(90_000, r.remainingMs(500_000)) // frozen while paused
        r = r.resume(41_000)
        assertEquals(80_000, r.remainingMs(51_000))
        r = r.addMinute()
        assertEquals(140_000, r.remainingMs(51_000))
    }

    @Test fun catchUpAfterInterruption() {
        val r = SessionRun.start(session, 0, 0).catchUp(200_000)
        // 120 s + 60 s elapsed, now 20 s into the rest.
        assertEquals(2, r.current)
        assertEquals(40_000, r.remainingMs(200_000))
        assertEquals(listOf(120, 60), r.logs.map { it.actualSec })
    }

    @Test fun lipsTiredInsertsEnforcedRest() {
        var r = SessionRun.start(session, 0, 0)
        r = r.next(30_000, lipsTired = true, restSec = 90)
        assertTrue(r.logs.single().lipsTired)
        assertEquals(30, r.logs.single().actualSec)
        val rest = r.step!!
        assertEquals(StepKind.REST, rest.kind)
        assertTrue(rest.enforced)
        assertEquals(90, rest.durationSec)
        assertFalse(r.canSkip(40_000))
        assertEquals(r, r.next(40_000)) // cannot skip an enforced rest
        r = r.next(120_000)
        assertEquals(ExerciseType.LONG_TONE, r.step!!.exerciseType)
        assertEquals(5, r.steps.size)
    }

    @Test fun lipsTiredBeforeExistingRestMakesItEnforced() {
        var r = SessionRun.start(session, 0, 0).next(120_000)
        r = r.next(130_000, lipsTired = true, restSec = 90)
        assertEquals(4, r.steps.size)
        assertTrue(r.step!!.enforced)
        assertEquals(90, r.step!!.durationSec)
    }

    @Test fun finishLogsEverything() {
        val r = SessionRun.start(session, 0, 0).next(100_000).finish(150_000)
        assertTrue(r.finished)
        assertEquals(4, r.logs.size)
        assertTrue(r.logs[0].skipped)
        assertEquals(50, r.logs[1].actualSec)
        assertEquals(150, r.totalPlayedSec())
    }

    @Test fun cleanStreak() {
        var r = SessionRun.start(session, 0, 0)
        r = r.recordRun("MAJOR:Eb", 60, true).recordRun("MAJOR:Eb", 60, false).recordRun("MAJOR:Eb", 60, true).recordRun("MAJOR:Eb", 60, true)
        assertEquals(2, r.cleanStreak("MAJOR:Eb"))
    }
}
