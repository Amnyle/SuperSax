package app.supersax.domain

import app.supersax.TestAssets
import app.supersax.domain.generator.Generator
import app.supersax.domain.planner.PieceChoice
import app.supersax.domain.planner.SessionPlanner
import app.supersax.domain.planner.StepKind
import app.supersax.domain.program.Block
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Exercise
import app.supersax.domain.program.ExerciseType
import app.supersax.domain.program.PlayerState
import app.supersax.domain.program.ProgramDay
import app.supersax.domain.program.ScaleProgress
import app.supersax.domain.program.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionPlannerTest {
    private val config = TestAssets.generatorConfig
    private val state = PlayerState(
        scales = listOf(
            ScaleProgress("MAJOR:Eb", 70, lastPracticedEpochMs = 500),
            ScaleProgress("MAJOR:Bb", 65, lastPracticedEpochMs = 100),
            ScaleProgress("MAJOR:Ab", 60, lastPracticedEpochMs = 300),
        ),
        holdTargetSec = 9,
        enduranceCapSec = 300,
    )

    private fun day(minutes: Int) =
        Generator.generate(Generator.Input("p", "P", setOf(1), minutes, 300), config).days.first()

    @Test fun resolvesTemplateIntoConcreteSession() {
        val plan = SessionPlanner.plan("p", day(20), state, config, pieces = PieceChoice("piece-new", "s1", "piece-fun"))
        val plays = plan.steps.filter { it.kind == StepKind.PLAY }
        // Playing time equals the program day.
        assertEquals(20 * 60, plan.playingSec)
        // Long tone gets the player's hold target.
        assertEquals(9, plays.first { it.exerciseType == ExerciseType.LONG_TONE }.target.holdSec)
        // Scales: least recently practiced first, with the player's tempo per scale.
        val scales = plays.filter { it.exerciseType == ExerciseType.SCALE }.map { it.target.scaleKey to it.target.bpm }.distinct()
        assertEquals(listOf("MAJOR:Bb" to 65, "MAJOR:Ab" to 60), scales)
        assertEquals("piece-new", plays.first { it.exerciseType == ExerciseType.NEW_MATERIAL }.target.pieceId)
        assertEquals("piece-fun", plays.first { it.exerciseType == ExerciseType.PLAY_FOR_FUN }.target.pieceId)
        // Rests between blocks, never two plays of different blocks back to back.
        plan.steps.zipWithNext().forEach { (a, b) ->
            if (a.kind == StepKind.PLAY && b.kind == StepKind.PLAY) assertEquals(a.blockId, b.blockId)
        }
        // 3 between blocks + 1 inside the 6-minute scale block (2 x 180 s > 300 s cap).
        assertEquals(4, plan.steps.count { it.kind == StepKind.REST })
        assertEquals(plan.steps.indices.toList(), plan.steps.map { it.index })
    }

    @Test fun enduranceCapChunksLongBlocks() {
        val d = ProgramDay("d", 1, listOf(Block("b", BlockKind.PLAY_FOR_FUN, 8, listOf(Exercise("e", "play-for-fun", ExerciseType.PLAY_FOR_FUN)))))
        val plan = SessionPlanner.plan("p", d, state.copy(enduranceCapSec = 180), config)
        val plays = plan.steps.filter { it.kind == StepKind.PLAY }
        assertEquals(3, plays.size)
        assertTrue(plays.all { it.durationSec <= 180 })
        assertEquals(480, plays.sumOf { it.durationSec })
        assertEquals(listOf(StepKind.PLAY, StepKind.REST, StepKind.PLAY, StepKind.REST, StepKind.PLAY), plan.steps.map { it.kind })
        assertEquals(60, plan.steps[1].durationSec)
    }

    @Test fun continuousPlayingAcrossExercisesRespectsCap() {
        val d = day(30)
        val plan = SessionPlanner.plan("p", d, state.copy(enduranceCapSec = 240), config)
        var continuous = 0
        plan.steps.forEach { s ->
            continuous = if (s.kind == StepKind.REST) 0 else continuous + s.durationSec
            assertTrue(continuous <= 240, "continuous playing $continuous")
        }
    }

    @Test fun pinnedTargetsOverrideProgression() {
        val d = ProgramDay("d", 1, listOf(Block("b", BlockKind.SCALES, 4, listOf(
            Exercise("e1", "scale-runs", ExerciseType.SCALE, Target(scaleKey = "MAJOR:Eb", bpm = 100, pinned = true)),
            Exercise("e2", "scale-runs", ExerciseType.SCALE, Target(scaleKey = "MAJOR:Eb", bpm = 100, pinned = false)),
        ))))
        val plays = SessionPlanner.plan("p", d, state, config).steps.filter { it.kind == StepKind.PLAY }
        assertEquals(100, plays[0].target.bpm)
        assertEquals(70, plays[1].target.bpm) // unpinned: player's current tempo for that scale
    }

    @Test fun shortOnTimeDropsNewMaterial() {
        val plan = SessionPlanner.plan("p", day(30), state, config, availableMinutes = 15)
        assertTrue(plan.steps.none { it.blockKind == BlockKind.NEW_MATERIAL })
        assertEquals(15 * 60, plan.playingSec)
    }
}
