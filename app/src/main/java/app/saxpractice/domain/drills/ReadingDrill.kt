package app.saxpractice.domain.drills

import app.saxpractice.domain.pitch.Letter
import app.saxpractice.domain.pitch.PitchClass
import app.saxpractice.domain.pitch.SpelledPitch
import kotlin.random.Random

/** Shared rules of the finite phone drills. */
object DrillRules {
    /** Questions per round: a fixed length, then "done for today". */
    const val READING_ROUND = 15
    const val MATCH_ROUND = 8
    const val SCALE_ROUND = 8
    const val FINGERING_ROUND = 10
    const val MAX_LEVEL = 5

    /**
     * Adapts the level after a round: up after at least 90 percent correct with a mean answer time
     * under [fastMs]; down below 60 percent; otherwise unchanged.
     */
    fun nextLevel(level: Int, correct: Int, total: Int, meanMs: Long, fastMs: Long = 4000): Int {
        if (total == 0) return level
        val acc = correct.toDouble() / total
        return when {
            acc >= 0.9 && meanMs <= fastMs -> (level + 1).coerceAtMost(MAX_LEVEL)
            acc < 0.6 -> (level - 1).coerceAtLeast(1)
            else -> level
        }
    }
}

/**
 * Note-reading drill: a written note on the treble staff, answered with a note-name keypad and an
 * accidental toggle. Levels widen the range, then add accidentals:
 * 1 staff only (E4–F5), 2 + one ledger line each side (C4–A5), 3 the whole alto range (B♭3–F♯6),
 * 4 + sharps and flats in the middle, 5 everything with accidentals.
 */
object ReadingDrill {
    data class Level(val lowWrittenMidi: Int, val highWrittenMidi: Int, val accidentals: Boolean)

    fun level(n: Int): Level = when (n.coerceIn(1, DrillRules.MAX_LEVEL)) {
        1 -> Level(64, 77, false)
        2 -> Level(60, 81, false)
        3 -> Level(58, 90, false)
        4 -> Level(60, 81, true)
        else -> Level(58, 90, true)
    }

    /** All spelled candidates in the level's range (naturals, plus single sharps and flats if allowed). */
    fun candidates(level: Level): List<SpelledPitch> {
        val out = mutableListOf<SpelledPitch>()
        for (octave in 3..6) for (letter in Letter.entries) {
            val alters = if (level.accidentals) listOf(-1, 0, 1) else listOf(0)
            for (a in alters) {
                val p = SpelledPitch(letter, a, octave)
                // Skip awkward spellings in a beginner drill (E♯, B♯, F♭, C♭).
                if (a == 1 && (letter == Letter.E || letter == Letter.B)) continue
                if (a == -1 && (letter == Letter.F || letter == Letter.C)) continue
                if (p.midi in level.lowWrittenMidi..level.highWrittenMidi) out += p
            }
        }
        return out
    }

    /** A round of written notes, deterministic for a seed, never the same note twice in a row. */
    fun round(levelNumber: Int, count: Int = DrillRules.READING_ROUND, seed: Long): List<SpelledPitch> {
        val pool = candidates(level(levelNumber))
        val rnd = Random(seed)
        val out = mutableListOf<SpelledPitch>()
        while (out.size < count) {
            val p = pool[rnd.nextInt(pool.size)]
            if (out.lastOrNull() != p) out += p
        }
        return out
    }

    /** The answer must name the written note with the right spelling (A♯ is not B♭ here). */
    fun isCorrect(question: SpelledPitch, answer: PitchClass): Boolean = question.pitchClass == answer
}

/** Match-the-note drill: written notes within the player's range; the tuner confirms the match. */
object MatchDrill {
    /** Cents within which a sounded note counts as matching, and how long it must be held. */
    const val TOLERANCE_CENTS = 25.0
    const val HOLD_SEC = 1.0

    fun round(lowWrittenMidi: Int, highWrittenMidi: Int, count: Int = DrillRules.MATCH_ROUND, seed: Long): List<Int> {
        val rnd = Random(seed)
        val out = mutableListOf<Int>()
        while (out.size < count) {
            val m = lowWrittenMidi + rnd.nextInt(highWrittenMidi - lowWrittenMidi + 1)
            if (out.lastOrNull() != m) out += m
        }
        return out
    }
}
