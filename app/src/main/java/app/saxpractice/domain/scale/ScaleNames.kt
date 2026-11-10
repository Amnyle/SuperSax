package app.saxpractice.domain.scale

import app.saxpractice.domain.pitch.Instrument
import app.saxpractice.domain.pitch.NoteNaming

/** Scale names for text built in the domain (weekly review, summaries), in written pitch. */
object ScaleNames {
    fun typeName(t: ScaleType, language: String): String {
        val fr = language.startsWith("fr")
        return when (t) {
            ScaleType.MAJOR -> if (fr) "majeur" else "major"
            ScaleType.NATURAL_MINOR -> if (fr) "mineur" else "minor"
            ScaleType.DORIAN -> if (fr) "dorien" else "Dorian"
            ScaleType.PHRYGIAN -> if (fr) "phrygien" else "Phrygian"
            ScaleType.LYDIAN -> if (fr) "lydien" else "Lydian"
            ScaleType.MIXOLYDIAN -> if (fr) "mixolydien" else "Mixolydian"
            ScaleType.LOCRIAN -> if (fr) "locrien" else "Locrian"
            ScaleType.MAJOR_ARPEGGIO -> if (fr) "arpège majeur" else "major arpeggio"
            ScaleType.MINOR_ARPEGGIO -> if (fr) "arpège mineur" else "minor arpeggio"
        }
    }

    fun written(key: String, naming: NoteNaming, language: String, instrument: Instrument = Instrument.ALTO_SAX): String {
        val id = runCatching { ScaleId.parse(key) }.getOrNull() ?: return key
        return naming.format(id.writtenTonic(instrument)) + " " + typeName(id.type, language)
    }
}
