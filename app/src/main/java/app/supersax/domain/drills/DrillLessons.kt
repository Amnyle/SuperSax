package app.supersax.domain.drills

import app.supersax.domain.common.LocalizedText
import app.supersax.domain.pitch.NoteNaming
import app.supersax.domain.pitch.PitchClass
import kotlinx.serialization.Serializable

/** The short lesson shown before a level of a phone drill (assets/drill_lessons/lessons.json). */
@Serializable
data class DrillLesson(
    val title: LocalizedText,
    val points: List<LocalizedText>,
    /** Example bars in the score format, one string per bar (written pitch). */
    val example: List<String>? = null,
    val fifths: Int = 0,
    val tip: LocalizedText? = null,
) {
    companion object {
        private val NOTE = Regex("\\{([A-G](?:#|b)?)\\}")

        /** Replaces {C}, {F#}, {Bb}… with note names in the player's naming. */
        fun notes(text: String, naming: NoteNaming): String = NOTE.replace(text) { naming.format(PitchClass.parse(it.groupValues[1])) }
    }
}
