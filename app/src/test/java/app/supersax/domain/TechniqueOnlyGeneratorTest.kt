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

    @Test fun breathingWarmUpComesFirst() {
        listOf(10, 20, 30).forEach { m ->
            val day = Generator.generate(input(m), config).days.first()
            assertEquals(BlockKind.BREATHING, day.blocks.first().kind, "$m")
        }
    }

    @Test fun repertoireFocusOrLimiterNeverAddsSongs() {
        val day = Generator.generate(input(20, listOf(Focus.REPERTOIRE), Pillar.REPERTOIRE), config).days.first()
        assertEquals(20, day.minutes)
        assertTrue(day.blocks.none { it.kind in songs }, "${day.blocks}")
    }

    @Test fun shortOnTimeDropsBreathingBeforeTechnique() {
        val day = Generator.generate(input(20), config).days.first()
        val cut = Generator.fitToMinutes(day.blocks, 10, config).associate { it.kind to it.minutes }
        assertEquals(10, cut.values.sum())
        assertTrue((cut[BlockKind.BREATHING] ?: 0) <= (day.blocks.first { it.kind == BlockKind.BREATHING }.minutes), "$cut")
        assertTrue((cut[BlockKind.LONG_TONES] ?: 0) > 0 && (cut[BlockKind.SCALES] ?: 0) > 0, "$cut")
    }
}
