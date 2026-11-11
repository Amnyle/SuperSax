package app.supersax.domain.drills

import app.supersax.domain.common.LocalizedText
import app.supersax.domain.library.Score
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.pitch.SpelledPitch
import kotlinx.serialization.Serializable
import java.text.Normalizer

/** A staff example in a lesson (written pitch). */
@Serializable
data class LessonStaff(val key: Int = 0, val time: String = "4/4", val measures: List<String>, val free: Boolean = false) {
    fun toScore(): Score {
        val (n, d) = time.split("/").map { it.toInt() }
        return Score(key, n, d, 80, measures.map { Score.parseMeasure(it) })
    }
}

@Serializable
data class LessonSection(val heading: LocalizedText, val body: LocalizedText, val staff: LessonStaff? = null)

@Serializable
enum class QuestionType { TEXT, NUMBER, NOTE, NOTES, STAFF_NOTE }

@Serializable
data class LocalizedList(val en: List<String> = emptyList(), val fr: List<String> = emptyList())

/** A free-form quiz question (never multiple choice). */
@Serializable
data class QuizQuestion(
    val id: String,
    val type: QuestionType,
    val prompt: LocalizedText,
    val explanation: LocalizedText,
    val staff: LessonStaff? = null,
    val accept: LocalizedList? = null,
    /** NUMBER: integer; NOTE: pitch class; STAFF_NOTE: written pitch. Kept as JSON primitives. */
    val answer: kotlinx.serialization.json.JsonElement? = null,
    val ordered: Boolean = true,
)

@Serializable
data class Lesson(
    val id: String,
    val order: Int,
    val minutes: Int = 3,
    val title: LocalizedText,
    val sections: List<LessonSection>,
    val quiz: List<QuizQuestion>,
)

/** A player's answer to a question. */
sealed interface Answer {
    data class Text(val value: String) : Answer
    data class Number(val value: Int) : Answer
    data class Note(val value: PitchClass) : Answer
    data class Notes(val values: List<PitchClass>) : Answer
    data class StaffNote(val value: SpelledPitch) : Answer
}

/** Pure answer checking for lessons. */
object Quiz {
    /** Lower case, accents removed, punctuation dropped, spaces collapsed. */
    fun normalise(s: String): String {
        val noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.lowercase().replace("♯", "#").replace("♭", "b")
            .replace(Regex("[^a-z0-9#]+"), " ").trim().replace(Regex("\\s+"), " ")
    }

    fun check(q: QuizQuestion, a: Answer, language: String): Boolean = when (q.type) {
        QuestionType.TEXT -> {
            val text = (a as? Answer.Text)?.value ?: return false
            val accepted = (q.accept?.en.orEmpty() + q.accept?.fr.orEmpty()).map { normalise(it) }
            normalise(text) in accepted
        }
        QuestionType.NUMBER -> (a as? Answer.Number)?.value == q.intAnswer()
        QuestionType.NOTE -> (a as? Answer.Note)?.value == q.noteAnswer()
        QuestionType.NOTES -> {
            val given = (a as? Answer.Notes)?.values ?: return false
            val expected = q.notesAnswer()
            if (q.ordered) given == expected else given.toSet() == expected.toSet() && given.size == expected.size
        }
        QuestionType.STAFF_NOTE -> (a as? Answer.StaffNote)?.value == q.staffAnswer()
    }

    fun QuizQuestion.intAnswer(): Int? = (answer as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
    fun QuizQuestion.noteAnswer(): PitchClass? = (answer as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { PitchClass.parse(it) }
    fun QuizQuestion.notesAnswer(): List<PitchClass> =
        (answer as? kotlinx.serialization.json.JsonArray)?.map { PitchClass.parse((it as kotlinx.serialization.json.JsonPrimitive).content) } ?: emptyList()
    fun QuizQuestion.staffAnswer(): SpelledPitch? = (answer as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { SpelledPitch.parse(it) }

    /** The expected answer as text for the "it was …" feedback. */
    fun expectedText(q: QuizQuestion, language: String, naming: NoteNaming): String = when (q.type) {
        QuestionType.TEXT -> (if (language.startsWith("fr")) q.accept?.fr else q.accept?.en)?.firstOrNull() ?: ""
        QuestionType.NUMBER -> q.intAnswer()?.toString() ?: ""
        QuestionType.NOTE -> q.noteAnswer()?.let { naming.format(it) } ?: ""
        QuestionType.NOTES -> q.notesAnswer().joinToString(" ") { naming.format(it) }
        QuestionType.STAFF_NOTE -> q.staffAnswer()?.let { naming.format(it) } ?: ""
    }

}
