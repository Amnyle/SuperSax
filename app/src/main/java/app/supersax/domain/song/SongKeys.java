package app.supersax.domain.song;

import app.supersax.domain.pitch.PitchClass;

/** Spelling of a detected key, transposition, and the written key for the alto saxophone. */
public final class SongKeys {
    private SongKeys() {}

    /** Alto sax in E♭: written notes sound a major sixth lower, so written = concert + 9 semitones. */
    public static final int ALTO_WRITTEN_UP = 9;

    private static final String[] MAJOR_BY_FIFTHS = {"Gb", "Db", "Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#"};
    private static final String[] MINOR_BY_FIFTHS = {"Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "C#", "G#", "D#"};

    /** Key signature (−6..+6 fifths; 6 accidentals spelled with flats) of a major or minor key on [tonic]. */
    public static int fifths(int tonic, boolean minor) {
        int major = Math.floorMod(minor ? tonic + 3 : tonic, 12);
        int f = Math.floorMod(major * 7, 12);
        if (f >= 6) f -= 12;
        return f;
    }

    /** The tonic spelled for its key signature (e.g. 10 major → B♭, 3 minor → E♭). */
    public static PitchClass tonicName(int tonic, boolean minor) {
        int f = fifths(tonic, minor);
        return PitchClass.parse((minor ? MINOR_BY_FIFTHS : MAJOR_BY_FIFTHS)[f + 6]);
    }

    public static int transpose(int tonic, int semitones) { return Math.floorMod(tonic + semitones, 12); }

    public static int altoWritten(int concertTonic) { return Math.floorMod(concertTonic + ALTO_WRITTEN_UP, 12); }

    /** Playback pitch factor for a shift of [semitones]. */
    public static double pitchFactor(int semitones) { return Math.pow(2, semitones / 12.0); }
}
