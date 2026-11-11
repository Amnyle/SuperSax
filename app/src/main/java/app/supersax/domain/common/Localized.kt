package app.supersax.domain.common

import kotlinx.serialization.Serializable

/** A bilingual text field used in content data files. */
@Serializable
data class LocalizedText(val en: String, val fr: String) {
    /** Text for a language code ("fr" gives French, anything else English). */
    fun get(language: String): String = if (language.startsWith("fr")) fr else en
}

/** An exercise description from `assets/exercises/exercises.json` (original text, not copied from any course). */
@Serializable
data class ExerciseInfo(
    val id: String,
    val type: app.supersax.domain.program.ExerciseType,
    val title: LocalizedText,
    val description: LocalizedText,
    val tips: List<LocalizedText> = emptyList(),
)

@Serializable
data class ExerciseCatalog(val exercises: List<ExerciseInfo>) {
    fun byId(id: String?): ExerciseInfo? = exercises.firstOrNull { it.id == id }
}
