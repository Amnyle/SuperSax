package app.supersax.domain.common

import kotlinx.serialization.Serializable

/** One update's notes, shown once after updating (ids only grow). */
@Serializable
data class WhatsNewEntry(val id: Int, val en: List<String>, val fr: List<String>) {
    fun lines(language: String) = if (language.startsWith("fr")) fr else en
}
