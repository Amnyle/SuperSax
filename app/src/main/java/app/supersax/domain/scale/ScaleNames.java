package app.supersax.domain.scale;

import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.NoteNaming;

/** Scale names for text built in the domain (weekly review, summaries), in written pitch. */
public final class ScaleNames {
    private ScaleNames() {}

    public static String typeName(ScaleType t, String language) {
        boolean fr = language.startsWith("fr");
        switch (t) {
            case MAJOR: return fr ? "majeur" : "major";
            case NATURAL_MINOR: return fr ? "mineur" : "minor";
            case DORIAN: return fr ? "dorien" : "Dorian";
            case PHRYGIAN: return fr ? "phrygien" : "Phrygian";
            case LYDIAN: return fr ? "lydien" : "Lydian";
            case MIXOLYDIAN: return fr ? "mixolydien" : "Mixolydian";
            case LOCRIAN: return fr ? "locrien" : "Locrian";
            case MAJOR_ARPEGGIO: return fr ? "arpège majeur" : "major arpeggio";
            default: return fr ? "arpège mineur" : "minor arpeggio";
        }
    }

    public static String written(String key, NoteNaming naming, String language, Instrument instrument) {
        ScaleId id;
        try { id = ScaleId.parse(key); } catch (RuntimeException e) { return key; }
        return naming.format(id.writtenTonic(instrument)) + " " + typeName(id.getType(), language);
    }

    public static String written(String key, NoteNaming naming, String language) { return written(key, naming, language, Instrument.ALTO_SAX); }
}
