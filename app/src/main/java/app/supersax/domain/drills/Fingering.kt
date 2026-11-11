package app.supersax.domain.drills

import kotlinx.serialization.Serializable

/** Alto keys drawn in the diagram and used in the fingering data file. */
@Serializable
enum class SaxKey {
    OCT, FRONT_F, PALM_D, PALM_EB, PALM_F, L1, BIS, L2, L3, G_SHARP, LOW_C_SHARP, LOW_B, LOW_BB,
    SIDE_E, SIDE_C, SIDE_BB, HIGH_FSHARP, R1, R2, R3, LOW_EB, LOW_C,
}

/** One fingering for a written note. [verified] is false for anything instrument-dependent (altissimo). */
@Serializable
data class FingeringEntry(
    val note: String,
    val keys: Set<SaxKey>,
    val alternates: List<Set<SaxKey>> = emptyList(),
    val verified: Boolean = true,
    val altissimo: Boolean = false,
)

@Serializable
data class FingeringChart(val instrument: String, val entries: List<FingeringEntry>) {
    fun forNote(note: String) = entries.firstOrNull { it.note == note }
}
