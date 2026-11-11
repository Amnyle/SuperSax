package app.supersax.notation;

/** SMuFL code points used by the renderer (Bravura font). */
public final class Smufl {
    public static final String G_CLEF = "";
    public static final String SHARP = "";
    public static final String FLAT = "";
    public static final String NATURAL = "";
    public static final String DOUBLE_SHARP = "";
    public static final String DOUBLE_FLAT = "";
    public static final String HEAD_WHOLE = "";
    public static final String HEAD_HALF = "";
    public static final String HEAD_BLACK = "";
    public static final String REST_WHOLE = "";
    public static final String REST_HALF = "";
    public static final String REST_QUARTER = "";
    public static final String REST_8TH = "";
    public static final String REST_16TH = "";
    public static final String FLAG_8TH_UP = "";
    public static final String FLAG_8TH_DOWN = "";
    public static final String FLAG_16TH_UP = "";
    public static final String FLAG_16TH_DOWN = "";
    public static final String DOT = "";

    private Smufl() {}

    /** Time-signature digit glyph. */
    public static String timeDigit(char d) { return String.valueOf((char) (0xE080 + (d - '0'))); }

    public static String accidental(int alter) {
        switch (alter) {
            case 1: return SHARP;
            case -1: return FLAT;
            case 2: return DOUBLE_SHARP;
            case -2: return DOUBLE_FLAT;
            default: return NATURAL;
        }
    }
}
