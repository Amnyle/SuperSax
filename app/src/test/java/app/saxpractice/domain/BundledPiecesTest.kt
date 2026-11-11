package app.saxpractice.domain

import app.saxpractice.TestAssets
import app.saxpractice.domain.library.Difficulty
import app.saxpractice.domain.library.Piece
import app.saxpractice.domain.library.PieceFile
import app.saxpractice.domain.pitch.Instrument
import app.saxpractice.notation.StaffLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every bundled piece parses, fits the alto, renders, and the set spans difficulty 1 to 10. */
class BundledPiecesTest {
    private val pieces = TestAssets.list("pieces").map { Piece.from(TestAssets.json.decodeFromString(PieceFile.serializer(), TestAssets.text("pieces/$it"))) }

    @Test fun allPiecesAreUsable() {
        val bundled = pieces.filter { it.file.bundled }
        assertTrue(bundled.size >= 28, "${bundled.size} bundled pieces")
        val alto = Instrument.ALTO_SAX
        bundled.forEach { p ->
            val s = p.score!!
            assertTrue(p.lowMidi!! >= alto.concertLowMidi && p.highMidi!! <= alto.concertHighMidi, p.id)
            assertTrue(p.file.licence in setOf("CC0", "public domain"), "${p.id}: ${p.file.licence}")
            assertTrue(!p.file.reviewed, p.id)
            assertTrue(p.file.sections.isNotEmpty(), p.id)
            assertTrue(StaffLayout.layout(s.toWritten(), 60f).isNotEmpty(), p.id)
            assertTrue(p.scaleKey != null, p.id)
        }
        val meta = pieces.single { !it.file.bundled }
        assertEquals("little-root-town", meta.id); assertEquals("copyrighted", meta.file.licence); assertTrue(meta.score == null)
        val spread = bundled.groupBy { it.difficulty }.mapValues { it.value.size }.toSortedMap()
        println("difficulty spread: $spread")
        bundled.sortedBy { it.difficulty }.forEach { println("${it.difficulty} ${it.id} ${Difficulty.features(it.score!!).mapValues { (_, v) -> "%.2f".format(v) }}") }
        assertTrue((1..10).all { it in spread }, "every level 1..10 present: $spread")
    }

    @Test fun knownEasierPiecesScoreLower() {
        fun d(id: String) = pieces.single { it.id == id }.difficulty
        assertTrue(d("mary-had-a-little-lamb") < d("minuet-in-g"))
        assertTrue(d("ode-to-joy") < d("blues-in-c"))
        assertTrue(d("long-tones-1") < d("perpetual-motion"))
    }
}
