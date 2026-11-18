package app.supersax.domain.grades

import app.supersax.domain.scale.ScaleType
import kotlinx.serialization.Serializable

/** The five parts of a grade exam, in the order they are taken, with their share of the 100 marks. */
enum class ExamSection(val marks: Int) { TONE(20), SCALES(25), SIGHT_READING(25), AURAL(15), THEORY(15) }

/**
 * A scale or arpeggio asked in an exam, in written pitch (what the player reads), with the range to cover in
 * semitones: 12 one octave, 19 a twelfth, 24 two octaves, 30 two and a half octaves.
 */
data class GradeScale(val writtenTonic: String, val type: ScaleType, val range: Int = 12) {
    val arpeggio: Boolean get() = type.family == ScaleType.Family.ARPEGGIO
}

/**
 * One grade: what each part of its exam asks. Notes are written MIDI for alto saxophone; tempos are notes per
 * minute (the syllabus's minimum crotchet tempo for scales played in quavers is twice the crotchet number);
 * levels refer to the app's own drills (sight-reading, ear training, scale degrees, scale recognition).
 */
data class Grade(
    val number: Int,
    val scales: List<GradeScale>,
    /** Minimum crotchet tempo of the scales (played in quavers) and notes per minute of the arpeggios. */
    val scaleCrotchet: Int,
    val arpeggioNotesPerMin: Int,
    /** Two long tones: their written notes, how long, and the share of the hold that must be in tune. */
    val longToneNotes: List<Int>,
    val longToneSec: Int,
    val longToneInTune: Double,
    val readingLevel: Int,
    val earLevel: Int,
    val degreesLevel: Int,
    val recognitionLevel: Int,
) {
    /** Notes per minute a scale needs for full marks: two quavers per crotchet. */
    val scaleNotesPerMin: Int get() = scaleCrotchet * 2
    fun notesPerMin(s: GradeScale): Int = if (s.arpeggio) arpeggioNotesPerMin else scaleNotesPerMin
}

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
    /** Same attainment bands as the usual graded exams: 60 pass, 75 merit, 87 distinction. */
    const val PASS = 60
    const val MERIT = 75
    const val DISTINCTION = 87
    /** Asked in one exam: two scales and one arpeggio from the grade's list; tries per item; aural and theory questions. */
    const val SCALES_ASKED = 3
    const val SCALE_TRIES = 2
    const val AURAL_QUESTIONS = 6
    const val THEORY_QUESTIONS = 6

    private const val OCT = 12
    private const val TWELFTH = 19
    private const val TWO = 24
    private const val TWO_HALF = 30
    private fun maj(t: String, r: Int) = GradeScale(t, ScaleType.MAJOR, r)
    private fun min(t: String, r: Int, harmonic: Boolean = true) = GradeScale(t, if (harmonic) ScaleType.HARMONIC_MINOR else ScaleType.NATURAL_MINOR, r)
    private fun pent(t: String, r: Int) = GradeScale(t, ScaleType.MAJOR_PENTATONIC, r)
    private fun majArp(t: String, r: Int) = GradeScale(t, ScaleType.MAJOR_ARPEGGIO, r)
    private fun minArp(t: String, r: Int) = GradeScale(t, ScaleType.MINOR_ARPEGGIO, r)

    /**
     * The keys, ranges and minimum tempos follow the saxophone technical work of Trinity College London's
     * Woodwind syllabus (graded exams from 2022), sets A and B together, written pitch. Left out because the app
     * cannot check them yet: chromatic and whole-tone scales, dominant/diminished 7ths, augmented arpeggios and
     * melodic minors (harmonic minor is asked instead; natural minor is accepted at grades 1 and 2 as in the
     * syllabus). Long tones, sight-reading, aural and theory levels are the app's own.
     */
    val grades: List<Grade> = listOf(
        Grade(1, listOf(maj("F", OCT), maj("G", OCT), min("E", OCT, harmonic = false), majArp("G", OCT), majArp("F", OCT)),
            72, 120, listOf(67, 74), 6, 0.6, 1, 1, 1, 1),
        Grade(2, listOf(maj("D", TWO), min("A", OCT, harmonic = false), maj("C", OCT), min("B", OCT, harmonic = false),
            majArp("C", OCT), minArp("B", OCT), majArp("D", TWO), minArp("A", OCT)),
            72, 120, listOf(65, 76), 8, 0.65, 2, 2, 2, 2),
        Grade(3, listOf(maj("C", TWO), min("G", TWELFTH), min("D", TWO), maj("F", TWELFTH),
            minArp("D", TWO), majArp("F", TWELFTH), minArp("E", TWELFTH), majArp("C", TWO), minArp("G", TWELFTH)),
            84, 132, listOf(62, 79), 10, 0.7, 2, 3, 3, 3),
        Grade(4, listOf(maj("Eb", TWO), maj("G", TWELFTH), min("F#", TWELFTH), min("C", TWO), maj("A", TWELFTH), pent("G", OCT),
            minArp("C", TWO), majArp("A", TWELFTH), majArp("Eb", TWO), minArp("A", TWELFTH)),
            96, 138, listOf(60, 81), 12, 0.72, 3, 4, 3, 4),
        Grade(5, listOf(maj("Bb", TWO), maj("E", TWO), min("C#", TWO), min("F", TWELFTH), maj("Eb", TWO), min("E", TWO), maj("Ab", TWELFTH), pent("D", TWO),
            minArp("E", TWO), majArp("Ab", TWELFTH), majArp("E", TWO), minArp("C#", TWO)),
            116, 152, listOf(59, 83), 15, 0.75, 3, 5, 4, 5),
        Grade(6, listOf(maj("C", TWO), maj("Ab", TWELFTH), min("G#", TWELFTH), maj("E", TWO), min("C", TWO), pent("Ab", TWELFTH),
            majArp("C", TWO), majArp("Ab", TWELFTH), minArp("G#", TWELFTH), majArp("E", TWO), minArp("C", TWO)),
            120, 189, listOf(58, 84), 18, 0.78, 4, 6, 5, 5),
        Grade(7, listOf(maj("B", TWO_HALF), min("Eb", TWO), min("A", TWELFTH), maj("Eb", TWO), min("G", TWELFTH), pent("Eb", TWO),
            majArp("B", TWO_HALF), minArp("Eb", TWO), minArp("A", TWELFTH), majArp("Eb", TWO), minArp("G", TWELFTH)),
            132, 207, listOf(58, 86), 22, 0.8, 5, 7, 6, 7),
        Grade(8, listOf(maj("Bb", TWO_HALF), maj("Db", TWO), maj("F#", TWELFTH), min("F#", TWELFTH), maj("D", TWO), maj("F", TWO), min("C#", TWO), pent("F#", TWELFTH),
            majArp("Bb", TWO_HALF), majArp("Db", TWO), majArp("F#", TWELFTH), minArp("F#", TWELFTH), majArp("D", TWO), minArp("C#", TWO)),
            132, 207, listOf(58, 88), 25, 0.85, 5, 8, 6, 8),
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
     * Score of one scale or arpeggio: 0 if no clean run; a clean run over the whole range at the required notes per
     * minute or faster scores 1, slower ones lose marks in proportion (half the speed scores 0.5), a run short of
     * the range is worth half, and a clean run on the second try loses a fifth.
     */
    fun scaleScore(clean: Boolean, notesPerMin: Double?, span: Int, s: GradeScale, g: Grade, secondTry: Boolean): Double {
        if (!clean) return 0.0
        val need = g.notesPerMin(s).toDouble()
        val speed = ((notesPerMin ?: (need * 0.5)) / need).coerceIn(0.0, 1.0)
        val range = if (span >= s.range) 1.0 else 0.5
        return speed * range * if (secondTry) 0.8 else 1.0
    }

    /** What one exam asks: two scales and one arpeggio, different keys where possible, from the grade's list. */
    fun pickScales(g: Grade, seed: Long): List<GradeScale> {
        val rnd = java.util.Random(seed)
        val scales = g.scales.filter { !it.arpeggio }.shuffled(rnd).take(SCALES_ASKED - 1)
        val arps = g.scales.filter { it.arpeggio }.shuffled(rnd)
        val arp = arps.firstOrNull { a -> scales.none { it.writtenTonic == a.writtenTonic } } ?: arps.firstOrNull()
        return scales + listOfNotNull(arp)
    }
}
