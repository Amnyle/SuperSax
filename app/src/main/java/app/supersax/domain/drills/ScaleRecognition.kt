package app.supersax.domain.drills

import app.supersax.domain.pitch.KeySignature
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.scale.ScaleGenerator
import app.supersax.domain.scale.ScaleId
import app.supersax.domain.scale.ScaleType
import kotlin.math.abs
import kotlin.random.Random

/**
 * Scale-recognition drill (visual), all in written pitch:
 * - name the major or minor key of a key signature,
 * - name a scale from its notes (tonic and type),
 * - build a scale from a root by tapping its notes (whole/half-step pattern).
 * Levels widen the keys (1: up to 1 accidental, 2: 2, 3: 4, 4: 6) and add minors (3+) and modes (5).
 */
object ScaleRecognition {
    enum class Kind { NAME_FROM_SIGNATURE, NAME_FROM_NOTES, BUILD }

    data class Question(val kind: Kind, val scale: ScaleId, val signature: KeySignature) {
        /** Written notes, one octave up from the tonic in octave 4. */
        val notes: List<SpelledPitch> get() = ScaleGenerator.oneOctave(scale, 4)
    }

    fun maxAccidentals(level: Int) = when (level) { 1 -> 1; 2 -> 2; 3 -> 4; else -> 6 }

    fun types(level: Int): List<ScaleType> = when {
        level >= 5 -> listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR, ScaleType.DORIAN, ScaleType.MIXOLYDIAN, ScaleType.LYDIAN, ScaleType.PHRYGIAN)
        level >= 3 -> listOf(ScaleType.MAJOR, ScaleType.NATURAL_MINOR)
        else -> listOf(ScaleType.MAJOR)
    }

    /** Written scales allowed at a level: tonics whose key signature has at most the level's accidentals. */
    fun pool(level: Int): List<ScaleId> = types(level).flatMap { t ->
        (-6..6).filter { abs(it) <= maxAccidentals(level) }.map { f ->
            val sig = KeySignature(f)
            // Tonic of type t whose signature is f (written pitch, no normalisation needed).
            val major = SpelledPitch(sig.majorTonic.letter, sig.majorTonic.alter, 4)
            val degree = when (t) {
                ScaleType.MAJOR -> 0; ScaleType.DORIAN -> 1; ScaleType.PHRYGIAN -> 2; ScaleType.LYDIAN -> 3
                ScaleType.MIXOLYDIAN -> 4; ScaleType.NATURAL_MINOR -> 5; else -> 0
            }
            ScaleId(major.transpose(app.supersax.domain.pitch.Interval(degree, ScaleType.MAJOR.semitones[degree])).pitchClass, t)
        }
    }

    fun round(level: Int, count: Int = DrillRules.SCALE_ROUND, seed: Long): List<Question> {
        val rnd = Random(seed)
        val pool = pool(level)
        val kinds = Kind.entries
        return List(count) { i ->
            val s = pool[rnd.nextInt(pool.size)]
            // Key-signature questions only ask major or minor (modes need the notes).
            val kind = kinds[i % kinds.size].let { if (it == Kind.NAME_FROM_SIGNATURE && s.type.family == ScaleType.Family.MODE) Kind.NAME_FROM_NOTES else it }
            Question(kind, s, s.keySignature)
        }
    }

    /** Naming answer: tonic spelled exactly, and the type. */
    fun checkName(q: Question, tonic: PitchClass, type: ScaleType): Boolean = q.scale.tonic == tonic && q.scale.type == type

    /** Building answer: the tapped notes (pitch classes), tonic to tonic, in order. */
    fun checkBuild(q: Question, tapped: List<PitchClass>): Boolean = tapped == q.notes.map { it.pitchClass }
}
