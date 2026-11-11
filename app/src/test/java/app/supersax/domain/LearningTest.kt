package app.supersax.domain

import app.supersax.TestAssets
import app.supersax.domain.drills.Answer
import app.supersax.domain.drills.FingeringChart
import app.supersax.domain.drills.FingeringDrill
import app.supersax.domain.drills.Lesson
import app.supersax.domain.drills.QuestionType
import app.supersax.domain.drills.Quiz
import app.supersax.domain.drills.Quiz.intAnswer
import app.supersax.domain.drills.Quiz.noteAnswer
import app.supersax.domain.drills.Quiz.notesAnswer
import app.supersax.domain.drills.Quiz.staffAnswer
import app.supersax.domain.drills.SaxKey
import app.supersax.domain.drills.ScaleRecognition
import app.supersax.domain.pitch.Letter
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.pitch.SpelledPitch
import app.supersax.domain.scale.ScaleType
import app.supersax.notation.StaffLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LearningTest {
    private val lessons = TestAssets.list("lessons").map { TestAssets.json.decodeFromString(Lesson.serializer(), TestAssets.text("lessons/$it")) }

    @Test fun lessonsLoadAndEveryAnswerIsUsable() {
        assertTrue(lessons.size >= 12, "${lessons.size} lessons")
        assertEquals(lessons.size, lessons.map { it.order }.toSet().size)
        lessons.forEach { l ->
            assertTrue(l.quiz.size in 3..5, l.id)
            l.sections.forEach { s -> s.staff?.let { StaffLayout.layout(it.toScore(), 60f) } }
            l.quiz.forEach { q ->
                q.staff?.let { StaffLayout.layout(it.toScore(), 60f) }
                when (q.type) {
                    QuestionType.TEXT -> {
                        val accept = q.accept!!
                        // The first accepted answer in each language is itself accepted.
                        assertTrue(Quiz.check(q, Answer.Text(accept.en.first()), "en"), "${l.id}/${q.id}")
                        assertTrue(Quiz.check(q, Answer.Text(accept.fr.first().uppercase() + " !"), "fr"), "${l.id}/${q.id}")
                        assertFalse(Quiz.check(q, Answer.Text("zzz"), "en"))
                    }
                    QuestionType.NUMBER -> assertTrue(Quiz.check(q, Answer.Number(q.intAnswer()!!), "en"))
                    QuestionType.NOTE -> assertTrue(Quiz.check(q, Answer.Note(q.noteAnswer()!!), "en"))
                    QuestionType.NOTES -> assertTrue(Quiz.check(q, Answer.Notes(q.notesAnswer()), "en"))
                    QuestionType.STAFF_NOTE -> assertNotNull(q.staffAnswer()).also { assertTrue(Quiz.check(q, Answer.StaffNote(it), "en")) }
                }
            }
        }
    }

    @Test fun normalisation() {
        assertEquals("croche pointee", Quiz.normalise("  Croche  pointée ! "))
        assertEquals("f# major", Quiz.normalise("F♯ major"))
    }

    @Test fun scaleRecognitionQuestionsAreConsistent() {
        for (level in 1..5) {
            val pool = ScaleRecognition.pool(level)
            assertTrue(pool.isNotEmpty())
            pool.forEach { s ->
                assertTrue(kotlin.math.abs(s.keySignature.fifths) <= ScaleRecognition.maxAccidentals(level), "$s at $level")
                assertTrue(s.type in ScaleRecognition.types(level))
            }
            val round = ScaleRecognition.round(level, seed = 42)
            round.forEach { q ->
                assertTrue(ScaleRecognition.checkName(q, q.scale.tonic, q.scale.type))
                assertTrue(ScaleRecognition.checkBuild(q, q.notes.map { it.pitchClass }))
            }
        }
        val dMajor = ScaleRecognition.pool(2).first { it.tonic == PitchClass(Letter.D) && it.type == ScaleType.MAJOR }
        val q = ScaleRecognition.Question(ScaleRecognition.Kind.BUILD, dMajor, dMajor.keySignature)
        assertTrue(ScaleRecognition.checkBuild(q, "D E F# G A B C# D".split(" ").map { PitchClass.parse(it) }))
        assertFalse(ScaleRecognition.checkBuild(q, "D E Gb G A B Db D".split(" ").map { PitchClass.parse(it) }), "enharmonic spelling is wrong")
        assertTrue(ScaleRecognition.pool(5).any { it.type == ScaleType.DORIAN })
    }

    @Test fun fingeringChartCoversTheStandardRange() {
        val chart = TestAssets.json.decodeFromString(FingeringChart.serializer(), TestAssets.text("fingerings/alto_fingerings.json"))
        for (w in 58..90) assertNotNull(chart.forNote(SpelledPitch.fromMidi(w).toString()), "missing ${SpelledPitch.fromMidi(w)}")
        assertTrue(chart.entries.filter { it.altissimo }.all { !it.verified })
        assertTrue(chart.entries.filter { !it.altissimo }.all { it.verified })
        // Spot checks of standard fingerings.
        assertEquals(setOf(SaxKey.L1, SaxKey.L2, SaxKey.L3), chart.forNote("G4")!!.keys)
        assertEquals(setOf(SaxKey.OCT) + chart.forNote("D4")!!.keys, chart.forNote("D5")!!.keys)
        assertEquals(emptySet(), chart.forNote("C#5")!!.keys)
        assertTrue(FingeringDrill.checkKeys(chart.forNote("Bb4")!!, setOf(SaxKey.L1, SaxKey.R1)))
        val round = FingeringDrill.round(chart, seed = 1)
        assertTrue(round.none { it.first.altissimo })
    }
}
