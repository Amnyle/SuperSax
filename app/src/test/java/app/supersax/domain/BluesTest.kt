package app.supersax.domain

import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.solo.Blues
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BluesTest {
    private fun p(s: String) = PitchClass.parse(s)

    @Test fun formChordsAndSpelling() {
        val bb = p("Bb")
        assertEquals(listOf("Bb", "Bb", "Bb", "Bb", "Eb", "Eb", "Bb", "Bb", "F", "Eb", "Bb", "F"), (0 until 12).map { Blues.chordRoot(bb, it).toString() })
        assertEquals(listOf(p("Bb"), p("D"), p("F"), p("Ab")), Blues.chordTones(bb))
        assertEquals(p("G"), Blues.target(bb, 4), "3rd of E♭7")
        // B♭ blues scale: B♭ D♭ E♭ F♭(E) F A♭, spelled from the letters.
        assertEquals(listOf("Bb", "Db", "Eb", "Fb", "F", "Ab"), Blues.scale(bb).map { it.toString() })
        // Written for alto: concert B♭ → G, F → D, E♭ → C.
        assertEquals(p("G"), Blues.altoWritten(bb))
        assertEquals(p("D"), Blues.altoWritten(p("F")))
        assertEquals(p("C"), Blues.altoWritten(p("Eb")))
        // Written G blues scale: G B♭ C D♭ D F.
        assertEquals(listOf("G", "Bb", "C", "Db", "D", "F"), Blues.scale(p("G")).map { it.toString() })
        assertEquals(listOf("G", "Bb", "C"), Blues.threeNotes(p("G")).map { it.toString() })
        assertEquals(listOf("G", "Bb", "C", "D", "F"), Blues.minorPentatonic(p("G")).map { it.toString() })
        assertEquals(listOf("G", "A", "B", "D", "E"), Blues.majorPentatonic(p("G")).map { it.toString() })
    }

    @Test fun callPhrasesFitTwoBarsAndStayInTheScale() {
        val allowed = setOf(0, 3, 5, 6, 7, 10, 12)
        for (seed in 1L..200L) {
            val ph = Blues.callPhrase(seed)
            assertTrue(ph.size in 2..6, "$seed: ${ph.size}")
            ph.forEach { n ->
                assertTrue(n.startBeat >= 0 && n.startBeat + n.beats <= 7.0, "$seed")
                assertTrue(n.semitones in allowed, "$seed: ${n.semitones}")
            }
            assertTrue(ph.last().semitones in setOf(0, 3, 7, 12))
            assertTrue(ph.zipWithNext().all { (a, b) -> b.startBeat >= a.startBeat + a.beats - 1e-9 })
        }
    }

    @Test fun everyStyleRendersOneLoop() {
        app.supersax.domain.solo.Groove.Style.entries.forEach { st ->
            val calls = (0 until st.bars() / 4).map { Blues.callPhrase(it.toLong()) }
            val x = app.supersax.domain.solo.Groove.render(st, 46, st.defaultBpm.toDouble(), 16000, calls, 70)
            assertEquals(16000 * app.supersax.domain.solo.Groove.loopSeconds(st, st.defaultBpm.toDouble()) * app.supersax.domain.solo.Groove.choruses(st), x.size.toDouble(), 2.0, "$st")
            assertTrue(x.all { abs(it) <= 0.91f && !it.isNaN() }, "$st clipped")
            assertTrue(x.maxOf { abs(it) } > 0.2f, "$st silent")
        }
    }

    @Test fun styleChordsSpelledForAlto() {
        val g = p("G") // written key for concert B♭
        val minor = app.supersax.domain.solo.Groove.Style.MINOR_BLUES.form.map { it.root(g).toString() + it.quality.suffix }
        assertEquals(listOf("Gm7", "Gm7", "Gm7", "Gm7", "Cm7", "Cm7", "Gm7", "Gm7", "Eb7", "D7", "Gm7", "D7"), minor)
        val jazz = app.supersax.domain.solo.Groove.Style.JAZZ_251.form
        assertEquals(listOf("Am7", "D7", "Gmaj7", "Gmaj7"), jazz.map { it.root(g).toString() + it.quality.suffix })
        assertEquals(listOf("C", "F#", "B", "B"), jazz.map { it.third(g).toString() })
        assertEquals(listOf("G", "B", "D", "F#"), jazz[2].tones(g).map { it.toString() })
    }
}

class SoloCueTest {
    @Test fun cuesAlternateEveryTwoBars() {
        val call = (0 until 12).map { app.supersax.ui.solo.cueOf(app.supersax.ui.solo.SoloStep.CALL, it) }
        assertEquals(listOf("LISTEN", "LISTEN", "ANSWER", "ANSWER"), call.take(4).map { it.name })
        val space = (0 until 12).map { app.supersax.ui.solo.cueOf(app.supersax.ui.solo.SoloStep.SPACE, it) }
        assertEquals(6, space.count { it.name == "PLAY" })
        assertTrue((0 until 12).all { app.supersax.ui.solo.cueOf(app.supersax.ui.solo.SoloStep.TARGET, it).name == "NONE" })
    }
}

class PentatonicScaleTest {
    @Test fun pentatonicsAreFiveNotesSpelledFromTheKey() {
        val major = app.supersax.domain.scale.ScaleGenerator.oneOctave(app.supersax.domain.scale.ScaleId.parse("MAJOR_PENTATONIC:Eb"), 4)
        assertEquals(listOf("Eb", "F", "G", "Bb", "C", "Eb"), major.map { it.pitchClass.toString() })
        val minor = app.supersax.domain.scale.ScaleGenerator.oneOctave(app.supersax.domain.scale.ScaleId.parse("MINOR_PENTATONIC:C"), 4)
        assertEquals(listOf("C", "Eb", "F", "G", "Bb", "C"), minor.map { it.pitchClass.toString() })
        assertEquals("WW3W3", app.supersax.domain.scale.ScaleType.MAJOR_PENTATONIC.stepPattern)
        assertEquals("C minor pentatonic", app.supersax.domain.scale.ScaleNames.typeName(app.supersax.domain.scale.ScaleType.MINOR_PENTATONIC, "en").let { "C $it" })
    }
}
