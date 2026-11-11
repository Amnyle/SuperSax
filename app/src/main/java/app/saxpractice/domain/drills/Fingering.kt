package app.saxpractice.domain.drills

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

/** Fingering drill: tap the keys of a note, or name the note of a fingering. Standard range only. */
object FingeringDrill {
    fun round(chart: FingeringChart, count: Int = DrillRules.FINGERING_ROUND, seed: Long): List<Pair<FingeringEntry, Boolean>> {
        val pool = chart.entries.filter { !it.altissimo }
        val rnd = kotlin.random.Random(seed)
        val out = mutableListOf<Pair<FingeringEntry, Boolean>>()
        while (out.size < count) {
            val e = pool[rnd.nextInt(pool.size)]
            if (out.lastOrNull()?.first != e) out += e to rnd.nextBoolean() // true = tap the keys, false = name the note
        }
        return out
    }

    /** The tapped keys match the main fingering or one of the alternates. */
    fun checkKeys(e: FingeringEntry, tapped: Set<SaxKey>) = tapped == e.keys || e.alternates.any { it == tapped }
}
