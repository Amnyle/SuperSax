package app.saxpractice.notation

import app.saxpractice.domain.library.Fraction
import app.saxpractice.domain.library.Measure
import app.saxpractice.domain.library.Score
import app.saxpractice.domain.library.ScoreNote
import app.saxpractice.domain.pitch.KeySignature
import app.saxpractice.domain.pitch.Letter
import app.saxpractice.domain.pitch.SpelledPitch
import kotlin.math.sqrt

/** SMuFL code points used by the renderer (Bravura). */
object Smufl {
    const val G_CLEF = ""
    const val SHARP = ""
    const val FLAT = ""
    const val NATURAL = ""
    const val DOUBLE_SHARP = ""
    const val DOUBLE_FLAT = ""
    const val HEAD_WHOLE = ""
    const val HEAD_HALF = ""
    const val HEAD_BLACK = ""
    const val REST_WHOLE = ""
    const val REST_HALF = ""
    const val REST_QUARTER = ""
    const val REST_8TH = ""
    const val REST_16TH = ""
    const val FLAG_8TH_UP = ""
    const val FLAG_8TH_DOWN = ""
    const val FLAG_16TH_UP = ""
    const val FLAG_16TH_DOWN = ""
    const val DOT = ""
    fun timeDigit(d: Char) = (0xE080 + (d - '0')).toChar().toString()

    fun accidental(alter: Int) = when (alter) {
        1 -> SHARP; -1 -> FLAT; 2 -> DOUBLE_SHARP; -2 -> DOUBLE_FLAT; else -> NATURAL
    }
}

/**
 * Everything is in staff spaces: the five lines are at y = 0 (top, F5) .. 4 (bottom, E4); y grows
 * downwards. A pitch's staff step counts diatonic steps from E4 (step 0); y = 4 - step / 2.
 */
object StaffGeometry {
    private val E4 = SpelledPitch(Letter.E, 0, 4).diatonicIndex
    fun step(p: SpelledPitch): Int = p.diatonicIndex - E4
    fun y(step: Int): Float = 4f - step / 2f
    fun pitchAt(step: Int, alter: Int = 0): SpelledPitch {
        val idx = E4 + step
        return SpelledPitch(Letter.ofIndex(idx), alter, Math.floorDiv(idx, 7))
    }

    /** Treble-clef steps of sharps (F C G D A E B) and flats (B E A D G C F). */
    val SHARP_STEPS = listOf(8, 5, 9, 6, 3, 7, 4)
    val FLAT_STEPS = listOf(4, 7, 3, 6, 2, 5, 1)

    /** Ledger-line steps needed for a note at [step]. */
    fun ledgerSteps(step: Int): List<Int> = when {
        step <= -2 -> (-2 downTo step).filter { it % 2 == 0 }
        step >= 10 -> (10..step).filter { it % 2 == 0 }
        else -> emptyList()
    }
}

/** A drawable element, in staff spaces. [noteIndex] links glyphs to the score note (for highlighting). */
sealed interface StaffItem {
    data class Glyph(val text: String, val x: Float, val y: Float, val noteIndex: Int = -1) : StaffItem
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val thickness: Float, val noteIndex: Int = -1) : StaffItem
    data class Tie(val x1: Float, val x2: Float, val y: Float, val above: Boolean) : StaffItem
}

/** One line of music. [top] and [bottom] bound everything drawn (ledger notes may go beyond the staff). */
data class StaffSystem(val width: Float, val items: List<StaffItem>, val top: Float, val bottom: Float)

/**
 * Pure layout of a single-voice score into systems for a given width (treble clef, key signature,
 * time signature on the first system, accidentals with measure memory, ledger lines, stems and
 * flags, dots, rests, ties and bar lines).
 */
object StaffLayout {
    const val HEAD_WIDTH = 1.18f
    const val STEM_LENGTH = 3.5f
    private const val CLEF_ADVANCE = 3.6f
    private const val ACC_ADVANCE = 1.05f
    private const val TIME_ADVANCE = 2.6f

    private val representable = listOf(
        Fraction(1, 1), Fraction(3, 4), Fraction(1, 2), Fraction(3, 8), Fraction(1, 4),
        Fraction(3, 16), Fraction(1, 8), Fraction(1, 16), Fraction(1, 32),
    )

    /** Splits a duration into drawable values, tied together (5/8 = 1/2 + 1/8). */
    fun decompose(d: Fraction): List<Fraction> {
        val out = mutableListOf<Fraction>()
        var left = d
        while (left > Fraction.ZERO) {
            val v = representable.firstOrNull { it <= left } ?: break
            out += v; left -= v
        }
        return out
    }

    /** Expands notes with non-drawable durations into tied drawable notes, keeping the source index. */
    private fun expand(m: Measure, startIndex: Int): List<Pair<ScoreNote, Int>> = m.notes.flatMapIndexed { i, n ->
        val parts = decompose(n.duration)
        parts.mapIndexed { k, d -> n.copy(duration = d, tie = if (k < parts.lastIndex) n.pitch != null else n.tie) to (startIndex + i) }
    }

    private fun noteWidth(n: ScoreNote, accidental: Boolean): Float =
        1.8f + 5.2f * sqrt(n.duration.value.toFloat()) + if (accidental) ACC_ADVANCE + 0.2f else 0f

    private fun headerWidth(keyFifths: Int, withTime: Boolean) =
        0.6f + CLEF_ADVANCE + kotlin.math.abs(keyFifths) * ACC_ADVANCE + 0.6f + if (withTime) TIME_ADVANCE else 0f

    /** Lays out [score] (draw it as given: pass a written score for the alto view). */
    fun layout(score: Score, width: Float, showTime: Boolean = true): List<StaffSystem> {
        val key = KeySignature(score.keyFifths)
        // Pre-compute per-measure note lists and accidental display.
        var idx = 0
        val measures = score.measures.map { m ->
            val notes = expand(m, idx); idx += m.notes.size
            val state = mutableMapOf<Pair<Letter, Int>, Int>()
            val acc = notes.map { (n, _) ->
                val p = n.pitch ?: return@map null
                val keyAlter = key.alterFor(p.letter)
                val cur = state[p.letter to p.octave] ?: keyAlter
                if (p.alter != cur) { state[p.letter to p.octave] = p.alter; p.alter } else null
            }
            Triple(notes, acc, notes.indices.sumOf { i -> noteWidth(notes[i].first, acc[i] != null).toDouble() }.toFloat() + 1.0f)
        }
        // Greedy line breaking.
        val systems = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var used = headerWidth(score.keyFifths, showTime)
        measures.forEachIndexed { i, (_, _, w) ->
            if (current.isNotEmpty() && used + w > width) {
                systems += current; current = mutableListOf(); used = headerWidth(score.keyFifths, false)
            }
            current += i; used += w
        }
        if (current.isNotEmpty()) systems += current
        if (systems.isEmpty()) systems += emptyList<Int>()

        return systems.mapIndexed { si, ms ->
            val items = mutableListOf<StaffItem>()
            val withTime = showTime && si == 0
            var x = 0.6f
            items += StaffItem.Glyph(Smufl.G_CLEF, x, 3f)
            x += CLEF_ADVANCE
            val steps = if (key.fifths >= 0) StaffGeometry.SHARP_STEPS else StaffGeometry.FLAT_STEPS
            repeat(kotlin.math.abs(key.fifths)) { k ->
                items += StaffItem.Glyph(if (key.fifths > 0) Smufl.SHARP else Smufl.FLAT, x, StaffGeometry.y(steps[k]))
                x += ACC_ADVANCE
            }
            x += 0.6f
            if (withTime) {
                items += StaffItem.Glyph(score.timeNum.toString().map { Smufl.timeDigit(it) }.joinToString(""), x, 1f)
                items += StaffItem.Glyph(score.timeDen.toString().map { Smufl.timeDigit(it) }.joinToString(""), x, 3f)
                x += TIME_ADVANCE
            }
            val natural = ms.sumOf { measures[it].third.toDouble() }.toFloat()
            val stretch = if (ms.isEmpty()) 1f else ((width - x) / natural).coerceIn(0.6f, if (si == systems.lastIndex) 1.25f else 3f)
            var top = -1f; var bottom = 5f
            val headX = mutableListOf<Pair<Float, Float>>() // x, y of each head, for ties
            val tieFrom = mutableListOf<Int>()
            ms.forEach { mi ->
                val (notes, acc, _) = measures[mi]
                notes.forEachIndexed { k, (n, srcIndex) ->
                    val w = noteWidth(n, acc[k] != null) * stretch
                    var nx = x + 0.4f * stretch
                    acc[k]?.let { a ->
                        items += StaffItem.Glyph(Smufl.accidental(a), nx, StaffGeometry.y(StaffGeometry.step(n.pitch!!)), srcIndex)
                        nx += ACC_ADVANCE + 0.2f
                    }
                    if (n.pitch == null) {
                        items += restGlyph(n.duration, nx, srcIndex)
                        headX += nx to 2f
                    } else {
                        val step = StaffGeometry.step(n.pitch)
                        val y = StaffGeometry.y(step)
                        top = minOf(top, y - 1.5f); bottom = maxOf(bottom, y + 1.5f)
                        StaffGeometry.ledgerSteps(step).forEach { ls ->
                            val ly = StaffGeometry.y(ls)
                            items += StaffItem.Line(nx - 0.4f, ly, nx + HEAD_WIDTH + 0.4f, ly, 0.16f, srcIndex)
                        }
                        val base = baseValue(n.duration)
                        items += StaffItem.Glyph(headFor(base), nx, y, srcIndex)
                        if (base < Fraction(1, 1)) {
                            val up = step < 4
                            if (up) {
                                val sx = nx + HEAD_WIDTH - 0.06f
                                items += StaffItem.Line(sx, y - 0.2f, sx, y - STEM_LENGTH, 0.12f, srcIndex)
                                flagFor(base, true)?.let { items += StaffItem.Glyph(it, sx - 0.06f, y - STEM_LENGTH, srcIndex) }
                                top = minOf(top, y - STEM_LENGTH - 0.5f)
                            } else {
                                val sx = nx + 0.06f
                                items += StaffItem.Line(sx, y + 0.2f, sx, y + STEM_LENGTH, 0.12f, srcIndex)
                                flagFor(base, false)?.let { items += StaffItem.Glyph(it, sx - 0.06f, y + STEM_LENGTH, srcIndex) }
                                bottom = maxOf(bottom, y + STEM_LENGTH + 0.5f)
                            }
                        }
                        if (isDotted(n.duration)) {
                            val dy = if (step % 2 == 0) y - 0.5f else y
                            items += StaffItem.Glyph(Smufl.DOT, nx + HEAD_WIDTH + 0.35f, dy, srcIndex)
                        }
                        if (tieFrom.isNotEmpty() && tieFrom.last() == headX.size - 1) {
                            val (px, py) = headX.last()
                            items += StaffItem.Tie(px + HEAD_WIDTH, nx, py, step >= 4)
                        }
                        headX += nx to y
                        if (n.tie) tieFrom += headX.size - 1
                    }
                    x += w
                }
                x += 1.0f * stretch
                items += StaffItem.Line(x, 0f, x, 4f, if (mi == score.measures.lastIndex) 0.5f else 0.16f)
            }
            StaffSystem(width, items, top, bottom)
        }
    }

    private fun baseValue(d: Fraction): Fraction = if (isDotted(d)) Fraction(d.num * 2 / 3, d.den).reduced() else d
    private fun isDotted(d: Fraction) = d.num == 3
    private fun headFor(base: Fraction) = when {
        base >= Fraction(1, 1) -> Smufl.HEAD_WHOLE
        base >= Fraction(1, 2) -> Smufl.HEAD_HALF
        else -> Smufl.HEAD_BLACK
    }
    private fun flagFor(base: Fraction, up: Boolean) = when (base) {
        Fraction(1, 8) -> if (up) Smufl.FLAG_8TH_UP else Smufl.FLAG_8TH_DOWN
        Fraction(1, 16), Fraction(1, 32) -> if (up) Smufl.FLAG_16TH_UP else Smufl.FLAG_16TH_DOWN
        else -> null
    }
    private fun restGlyph(d: Fraction, x: Float, index: Int): StaffItem.Glyph {
        val base = baseValue(d)
        return when {
            base >= Fraction(1, 1) -> StaffItem.Glyph(Smufl.REST_WHOLE, x, 1f, index)
            base >= Fraction(1, 2) -> StaffItem.Glyph(Smufl.REST_HALF, x, 2f, index)
            base >= Fraction(1, 4) -> StaffItem.Glyph(Smufl.REST_QUARTER, x, 2f, index)
            base >= Fraction(1, 8) -> StaffItem.Glyph(Smufl.REST_8TH, x, 2f, index)
            else -> StaffItem.Glyph(Smufl.REST_16TH, x, 2f, index)
        }
    }

    /** A one-note "score" for drills: one whole note (or none) in the given key. */
    fun singleNote(pitch: SpelledPitch?, keyFifths: Int = 0): Score =
        Score(keyFifths, 4, 4, 60, listOf(Measure(listOfNotNull(pitch?.let { ScoreNote(it, Fraction(1, 1)) }))))
}
