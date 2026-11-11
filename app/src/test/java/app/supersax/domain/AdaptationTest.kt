package app.supersax.domain

import app.supersax.TestAssets
import app.supersax.domain.generator.Adaptation
import app.supersax.domain.program.PlayerState
import app.supersax.domain.program.ScaleProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptationTest {
    private val config = TestAssets.generatorConfig.adaptation

    @Test fun twoCleanRunsAddFiveBpm() {
        var s = PlayerState(scales = listOf(ScaleProgress("MAJOR:Eb", 60, unlockedAtEpochMs = 1)))
        var r = Adaptation.recordRun(s, "MAJOR:Eb", 60, clean = true, nowMs = 10, config = config)
        assertFalse(r.bumped); assertEquals(60, r.newBpm)
        r = Adaptation.recordRun(r.state, "MAJOR:Eb", 60, clean = true, nowMs = 11, config = config)
        assertTrue(r.bumped); assertEquals(65, r.newBpm)
        assertEquals(0, r.state.scale("MAJOR:Eb")!!.consecutiveClean)
        // A mistake in between resets the streak.
        s = r.state
        r = Adaptation.recordRun(s, "MAJOR:Eb", 65, true, 12, config)
        r = Adaptation.recordRun(r.state, "MAJOR:Eb", 65, false, 13, config)
        r = Adaptation.recordRun(r.state, "MAJOR:Eb", 65, true, 14, config)
        assertFalse(r.bumped); assertEquals(65, r.newBpm)
    }

    @Test fun newScaleUnlocksWhenLastTwoAreSolid() {
        val order = config.unlockOrder
        var s = PlayerState(scales = listOf(
            ScaleProgress(order[0], 60, bestCleanBpm = 60, unlockedAtEpochMs = 1),
            ScaleProgress(order[1], 55, bestCleanBpm = 0, unlockedAtEpochMs = 2),
        ))
        assertNull(Adaptation.nextUnlock(s, config))
        // Making the second one solid (clean pair at 60) unlocks the third.
        var r = Adaptation.recordRun(s, order[1], 60, true, 3, config)
        assertNull(r.unlocked)
        r = Adaptation.recordRun(r.state, order[1], 60, true, 4, config)
        assertEquals(order[2], r.unlocked)
        assertEquals(3, r.state.scales.size)
        // The new one is not solid yet, so nothing else unlocks.
        assertNull(Adaptation.nextUnlock(r.state, config))
        s = PlayerState()
        assertEquals(order[0], Adaptation.nextUnlock(s, config))
    }

    @Test fun longToneGrowthRule() {
        assertEquals(9, Adaptation.nextHoldTarget(8, tiredEarly = false, tirednessRating = 2, meanAbsDriftCents = 5.0, config = config))
        assertEquals(7, Adaptation.nextHoldTarget(8, tiredEarly = true, tirednessRating = 2, meanAbsDriftCents = null, config = config))
        assertEquals(8, Adaptation.nextHoldTarget(8, tiredEarly = false, tirednessRating = 5, meanAbsDriftCents = null, config = config))
        assertEquals(8, Adaptation.nextHoldTarget(8, tiredEarly = false, tirednessRating = 1, meanAbsDriftCents = 35.0, config = config))
        assertEquals(config.holdMaxSec, Adaptation.nextHoldTarget(config.holdMaxSec, false, 1, null, config))
        assertEquals(config.holdMinSec, Adaptation.nextHoldTarget(config.holdMinSec, true, 1, null, config))
        assertTrue(Adaptation.tiredEarly(240, 100, config))
        assertFalse(Adaptation.tiredEarly(240, 230, config))
    }

    @Test fun staminaRamp() {
        var s = PlayerState(enduranceCapSec = 180)
        repeat(config.staminaSessionsForStep) { s = Adaptation.afterSession(s, lipsTired = false, config = config) }
        assertEquals(180 + config.staminaStepSec, s.enduranceCapSec)
        s = Adaptation.afterSession(s, false, config)
        s = Adaptation.afterSession(s, true, config)
        assertEquals(0, s.sessionsWithoutFatigue)
    }
}
