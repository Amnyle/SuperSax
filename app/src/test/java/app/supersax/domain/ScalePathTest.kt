package app.supersax.domain

import app.supersax.domain.audio.DetectedNote
import app.supersax.domain.audio.ScalePath
import app.supersax.domain.audio.ScalePath.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScalePathTest {
    // C major from C4 (concert), one octave up.
    private val scale = listOf(60, 62, 64, 65, 67, 69, 71, 72)
    private val names = mapOf("do" to 0, "re" to 2, "mi" to 4, "fa" to 5, "sol" to 7, "la" to 9, "si" to 11)

    /** "do re mi … do'" : a note name goes to the nearest octave of the previous note (going by steps). */
    private fun play(vararg ns: String, beat: Double = 0.5): List<DetectedNote> {
        var prev = 60
        return ns.mapIndexed { i, n ->
            val pc = names.getValue(n)
            var m = prev - Math.floorMod(prev, 12) + pc
            if (i > 0) { if (m - prev > 6) m -= 12; if (prev - m > 6) m += 12 } else m = 60 + pc
            prev = m
            DetectedNote(m, i * beat, i * beat + beat * 0.9)
        }
    }

    private fun check(vararg ns: String) = ScalePath.check(play(*ns), scale)

    @Test fun upAndDown() = assertEquals(Problem.NONE, check("do", "re", "mi", "fa", "sol", "la", "si", "do", "si", "la", "sol", "fa", "mi", "re", "do").problem)

    @Test fun furtherUpAndBackAndForth() {
        // do ré mi fa sol la si do ré mi … and back, then si do: the user's example.
        assertEquals(Problem.NONE, check("do", "re", "mi", "fa", "sol", "la", "si", "do", "re", "mi", "re", "do", "si", "la", "sol", "fa", "mi", "re", "do", "si", "do").problem)
        assertEquals(Problem.NONE, check("do", "si", "la", "sol", "fa", "mi", "re", "do", "re", "mi", "fa", "sol", "la", "si", "do").problem, "going down first")
    }

    @Test fun mistakes() {
        assertEquals(Problem.NO_OCTAVE, check("do", "re", "mi", "re", "do").problem)
        assertEquals(Problem.NOT_HOME, check("do", "re", "mi", "fa", "sol", "la", "si", "do", "si", "la").problem)
        val jump = check("do", "re", "mi", "fa", "sol", "la", "si", "do", "si", "sol", "fa", "mi", "re", "do")
        assertTrue(jump.problem == Problem.NONE || jump.problem == Problem.NOT_NEIGHBOUR, "one stray note may be forgiven")
        val twoJumps = check("do", "mi", "sol", "si", "do", "la", "fa", "re", "do")
        assertEquals(Problem.NOT_NEIGHBOUR, twoJumps.problem)
        assertEquals(Problem.WRONG_START, check("re", "mi", "fa", "sol", "la", "si", "do", "re").problem)
    }

    @Test fun tempoFromTheNotes() {
        val r = ScalePath.check(play("do", "re", "mi", "fa", "sol", "la", "si", "do", "si", "la", "sol", "fa", "mi", "re", "do", beat = 0.5), scale)
        assertEquals(120.0, r.bpm!!, 1.0)
    }
}
