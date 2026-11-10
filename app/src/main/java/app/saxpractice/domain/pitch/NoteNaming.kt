package app.saxpractice.domain.pitch

import kotlinx.serialization.Serializable

/** Note-name system, independent of the UI language. */
@Serializable
enum class NoteNaming {
    LETTERS, SOLFEGE;

    fun letterName(letter: Letter): String = when (this) {
        LETTERS -> letter.name
        SOLFEGE -> SOLFEGE_NAMES[letter.index]
    }

    /** "E♭", "Mi♭", optionally with octave: "E♭4". */
    fun format(pc: PitchClass): String = letterName(pc.letter) + accidentalSymbol(pc.alter)
    fun format(p: SpelledPitch, withOctave: Boolean = true): String =
        format(p.pitchClass) + if (withOctave) p.octave.toString() else ""

    companion object {
        val SOLFEGE_NAMES = listOf("Do", "Ré", "Mi", "Fa", "Sol", "La", "Si")

        fun accidentalSymbol(alter: Int): String = when (alter) {
            0 -> ""
            1 -> "♯"; 2 -> "𝄪"; -1 -> "♭"; -2 -> "𝄫"
            else -> if (alter > 0) "♯".repeat(alter) else "♭".repeat(-alter)
        }

        /**
         * Parses a typed answer in either naming system (letters or solfège, with b/#/♭/♯),
         * ignoring case and accents. Returns null if it is not a note name.
         */
        fun parseAny(input: String): PitchClass? {
            val s = input.trim().lowercase()
                .replace("é", "e").replace("♭", "b").replace("♯", "#").replace(" ", "")
            if (s.isEmpty()) return null
            val solfege = listOf("do" to Letter.C, "re" to Letter.D, "mi" to Letter.E, "fa" to Letter.F,
                "sol" to Letter.G, "la" to Letter.A, "si" to Letter.B, "ut" to Letter.C)
            val (letter, rest) = solfege.firstOrNull { s.startsWith(it.first) }?.let { it.second to s.removePrefix(it.first) }
                ?: run {
                    val c = s[0].uppercaseChar()
                    if (c !in 'A'..'G') return null
                    Letter.valueOf(c.toString()) to s.substring(1)
                }
            val accidental = when (rest.replace("diese", "#").replace("dièse", "#").replace("bemol", "b").replace("bémol", "b")) {
                "" -> 0; "#" -> 1; "##", "x" -> 2; "b" -> -1; "bb" -> -2
                else -> return null
            }
            return PitchClass(letter, accidental)
        }
    }
}
