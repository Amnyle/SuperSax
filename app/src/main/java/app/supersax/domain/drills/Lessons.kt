package app.supersax.domain.drills

import app.supersax.domain.common.LocalizedText
import app.supersax.domain.library.Score
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.pitch.PitchClass
import app.supersax.domain.pitch.SpelledPitch
import kotlinx.serialization.Serializable

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
