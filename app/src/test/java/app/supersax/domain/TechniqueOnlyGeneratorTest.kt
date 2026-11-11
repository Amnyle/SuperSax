package app.supersax.domain

import app.supersax.TestAssets
import app.supersax.domain.generator.Generator
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Focus
import app.supersax.domain.program.Pillar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shipped config: sessions hold breathing, long tones and scales only (no song blocks for now). */
class TechniqueOnlyGeneratorTest {
    private val config = TestAssets.generatorConfig
    private val songs = setOf(BlockKind.NEW_MATERIAL, BlockKind.PLAY_FOR_FUN)

    private fun input(minutes: Int, focus: List<Focus> = emptyList(), limiter: Pillar? = null) =
        Generator.Input("p", "P", setOf(1, 3, 5), minutes, 300, focus, limiter, emptyList())

    @Test fun noSongBlocksAtAnyLength() {
        for (m in 5..90) {
            val day = Generator.generate(input(m), config).days.first()
            assertEquals(m, day.minutes, "total for $m")
            assertTrue(day.blocks.none { it.kind in songs }, "songs at $m: ${day.blocks}")
            assertTrue(day.blocks.any { it.kind == BlockKind.LONG_TONES } && day.blocks.any { it.kind == BlockKind.SCALES }, "$m")
        }
    }

    @Test fun noBreathingBlock() {
        for (m in 5..90) assertTrue(Generator.generate(input(m), config).days.first().blocks.none { it.kind == BlockKind.BREATHING }, "$m")
    }

    @Test fun neverTheSameScaleTwiceInASession() {
        // A beginner with one unlocked scale and a 30-minute day (two scale exercises).
        val day = Generator.generate(input(30), config).days.first()
        val state = app.supersax.domain.program.PlayerState(scales = listOf(app.supersax.domain.program.ScaleProgress("MAJOR:Eb", 60)))
        val plan = app.supersax.domain.planner.SessionPlanner.plan("p", day, state, config, 300, 60,
            app.supersax.domain.planner.PieceChoice(), null)
        val keys = plan.steps.filter { it.kind == app.supersax.domain.planner.StepKind.PLAY && it.chunk == 1 }.mapNotNull { it.target.scaleKey }
        assertTrue(keys.size >= 2, "$keys")
        assertEquals(keys.size, keys.distinct().size, "$keys")
        assertEquals("MAJOR:Eb", keys.first())
    }

    @Test fun repertoireFocusOrLimiterNeverAddsSongs() {
        val day = Generator.generate(input(20, listOf(Focus.REPERTOIRE), Pillar.REPERTOIRE), config).days.first()
        assertEquals(20, day.minutes)
        assertTrue(day.blocks.none { it.kind in songs }, "${day.blocks}")
    }

    @Test fun shortOnTimeKeepsBothTechniqueBlocks() {
        val day = Generator.generate(input(20), config).days.first()
        val cut = Generator.fitToMinutes(day.blocks, 10, config).associate { it.kind to it.minutes }
        assertEquals(10, cut.values.sum())
        assertTrue((cut[BlockKind.LONG_TONES] ?: 0) > 0 && (cut[BlockKind.SCALES] ?: 0) > 0, "$cut")
    }
}
