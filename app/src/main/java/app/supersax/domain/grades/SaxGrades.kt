package app.supersax.domain.grades

import app.supersax.domain.scale.ScaleType
import kotlinx.serialization.Serializable

/** The five parts of a grade exam, in the order they are taken, with their share of the 100 marks. */
enum class ExamSection(val marks: Int) { TONE(20), SCALES(25), SIGHT_READING(25), AURAL(15), THEORY(15) }

/** A scale asked in an exam, in written pitch (what the player reads). */
data class GradeScale(val writtenTonic: String, val type: ScaleType)

/**
 * One grade: what each part of its exam asks. Notes are written MIDI for alto saxophone; levels refer to the
 * app's own drills (sight-reading, ear training, scale degrees, scale recognition).
 */
data class Grade(
    val number: Int,
    val scales: List<GradeScale>,
    /** Quarter-note tempo a scale must reach for full marks (one note per beat). */
    val scaleBpm: Int,
    /** Two long tones: their written notes, how long, and the share of the hold that must be in tune. */
    val longToneNotes: List<Int>,
    val longToneSec: Int,
    val longToneInTune: Double,
    val readingLevel: Int,
    val earLevel: Int,
    val degreesLevel: Int,
    val recognitionLevel: Int,
)

/** Result of one exam: marks per part (each out of its share), the date. */
@Serializable
data class ExamResult(val grade: Int, val atMs: Long, val marks: Map<String, Int>) {
    val total: Int get() = marks.values.sum()
    val award: Award get() = SaxGrades.award(total)
}

enum class Award { FAIL, PASS, MERIT, DISTINCTION }

/**
 * Super Sax's own grade ladder, 1 to 8, built like the usual graded music exams (tone, scales, sight-reading,
 * aural, theory) but with the app's own requirements. Each exam is marked out of 100: 66 to pass, 80 for merit,
 * 90 for distinction. Passing a grade opens the next one.
 */
object SaxGrades {
    const val PASS = 66
    const val MERIT = 80
    const val DISTINCTION = 90
    /** Scales asked in one exam (picked from the grade's list), tries per scale, questions in aural and theory. */
    const val SCALES_ASKED = 3
    const val SCALE_TRIES = 2
    const val AURAL_QUESTIONS = 6
    const val THEORY_QUESTIONS = 6

    private fun maj(vararg t: String) = t.map { GradeScale(it, ScaleType.MAJOR) }
    private fun min(vararg t: String) = t.map { GradeScale(it, ScaleType.NATURAL_MINOR) }
    private fun of(type: ScaleType, vararg t: String) = t.map { GradeScale(it, type) }

    val grades: List<Grade> = listOf(
        Grade(1, maj("C", "G", "F"), 60, listOf(67, 74), 6, 0.6, 1, 1, 1, 1),
        Grade(2, maj("C", "G", "F", "D", "Bb") + min("A", "E"), 70, listOf(65, 76), 8, 0.65, 2, 2, 2, 2),
        Grade(3, maj("G", "F", "D", "Bb", "A", "Eb") + min("E", "D", "B") + of(ScaleType.MAJOR_PENTATONIC, "C", "G"), 80,
            listOf(62, 79), 10, 0.7, 2, 3, 3, 3),
        Grade(4, maj("D", "Bb", "A", "Eb", "E", "Ab") + min("B", "G", "F#", "C") + of(ScaleType.MINOR_PENTATONIC, "A", "D"), 90,
            listOf(60, 81), 12, 0.72, 3, 4, 3, 4),
        Grade(5, maj("A", "Eb", "E", "Ab", "B", "Db") + min("F#", "C", "C#", "F") + of(ScaleType.MAJOR_PENTATONIC, "F", "D") +
            of(ScaleType.MINOR_PENTATONIC, "E", "G"), 100, listOf(59, 83), 15, 0.75, 3, 5, 4, 5),
        Grade(6, maj("E", "Ab", "B", "Db", "F#") + min("C#", "F", "G#", "Bb") + of(ScaleType.DORIAN, "D", "G") +
            of(ScaleType.MIXOLYDIAN, "G", "C"), 110, listOf(58, 84), 18, 0.78, 4, 6, 5, 5),
        Grade(7, maj("B", "Db", "F#", "Gb") + min("G#", "Bb", "D#", "Eb") + of(ScaleType.DORIAN, "A", "E", "C") +
            of(ScaleType.MIXOLYDIAN, "D", "F", "Bb") + of(ScaleType.LYDIAN, "F", "C"), 120, listOf(58, 86), 22, 0.8, 5, 7, 6, 7),
        Grade(8, maj("B", "Db", "F#", "Gb", "E", "Ab") + min("G#", "D#", "Eb", "C#") + of(ScaleType.DORIAN, "F", "Bb") +
            of(ScaleType.MIXOLYDIAN, "E", "Ab") + of(ScaleType.LYDIAN, "G", "D") + of(ScaleType.PHRYGIAN, "E", "A"), 132,
            listOf(58, 88), 25, 0.85, 5, 8, 6, 8),
    )

    fun grade(n: Int): Grade = grades[(n - 1).coerceIn(0, grades.size - 1)]

    fun award(total: Int): Award = when {
        total >= DISTINCTION -> Award.DISTINCTION
        total >= MERIT -> Award.MERIT
        total >= PASS -> Award.PASS
        else -> Award.FAIL
    }

    /** Highest grade open: grade 1, plus one per grade passed in order. */
    fun open(results: List<ExamResult>): Int {
        var g = 1
        while (g < grades.size && results.any { it.grade == g && it.award != Award.FAIL }) g++
        return g
    }

    /** Best result of a grade, or null. */
    fun best(results: List<ExamResult>, grade: Int): ExamResult? = results.filter { it.grade == grade }.maxByOrNull { it.total }

    /** Marks of a part from its score 0..1. */
    fun marks(section: ExamSection, score: Double): Int = Math.round(section.marks * score.coerceIn(0.0, 1.0)).toInt()

    /**
     * Score of one long tone: held time against the target and in-tune share against the requirement; the held part
     * and the tuning part count half each.
     */
    fun toneScore(heldSec: Double, inTuneShare: Double, g: Grade): Double {
        val held = (heldSec / g.longToneSec).coerceIn(0.0, 1.0)
        val tune = (inTuneShare / g.longToneInTune).coerceIn(0.0, 1.0)
        return 0.5 * held + 0.5 * tune
    }

    /**
     * Score of one scale: 0 if no clean run; a clean run at the grade's tempo or faster scores 1, slower ones lose
     * marks in proportion (half the tempo scores 0.5); a clean run on the second try loses a fifth.
     */
    fun scaleScore(clean: Boolean, bpm: Double?, g: Grade, secondTry: Boolean): Double {
        if (!clean) return 0.0
        val speed = ((bpm ?: (g.scaleBpm * 0.5)) / g.scaleBpm).coerceIn(0.0, 1.0)
        return speed * if (secondTry) 0.8 else 1.0
    }

    /** The scales asked in one exam: [SCALES_ASKED] different ones from the grade's list. */
    fun pickScales(g: Grade, seed: Long): List<GradeScale> = g.scales.shuffled(java.util.Random(seed)).take(SCALES_ASKED)
}
