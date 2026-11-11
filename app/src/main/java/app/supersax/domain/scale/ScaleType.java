package app.supersax.domain.scale;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Scale (or arpeggio) definitions as interval patterns. Note sequences are always generated from these,
 * never typed by hand. {@code fifthsOffset} is the key-signature offset from the major key on the same
 * tonic (minor = -3).
 */
public enum ScaleType {
    MAJOR(steps7(), list(0, 2, 4, 5, 7, 9, 11), 0, Family.MAJOR),
    NATURAL_MINOR(steps7(), list(0, 2, 3, 5, 7, 8, 10), -3, Family.MINOR),
    DORIAN(steps7(), list(0, 2, 3, 5, 7, 9, 10), -2, Family.MODE),
    PHRYGIAN(steps7(), list(0, 1, 3, 5, 7, 8, 10), -4, Family.MODE),
    LYDIAN(steps7(), list(0, 2, 4, 6, 7, 9, 11), 1, Family.MODE),
    MIXOLYDIAN(steps7(), list(0, 2, 4, 5, 7, 9, 10), -1, Family.MODE),
    LOCRIAN(steps7(), list(0, 1, 3, 5, 6, 8, 10), -5, Family.MODE),
    MAJOR_ARPEGGIO(list(0, 2, 4), list(0, 4, 7), 0, Family.ARPEGGIO),
    MINOR_ARPEGGIO(list(0, 2, 4), list(0, 3, 7), -3, Family.ARPEGGIO),
    /** Five notes: 1 2 3 5 6. */
    MAJOR_PENTATONIC(list(0, 1, 2, 4, 5), list(0, 2, 4, 7, 9), 0, Family.PENTATONIC),
    /** Five notes: 1 ♭3 4 5 ♭7, the usual first scale for improvising. */
    MINOR_PENTATONIC(list(0, 2, 3, 4, 6), list(0, 3, 5, 7, 10), -3, Family.PENTATONIC);

    public enum Family { MAJOR, MINOR, MODE, ARPEGGIO, PENTATONIC }

    private final List<Integer> steps;
    private final List<Integer> semitones;
    private final int fifthsOffset;
    private final Family family;

    ScaleType(List<Integer> steps, List<Integer> semitones, int fifthsOffset, Family family) {
        this.steps = steps;
        this.semitones = semitones;
        this.fifthsOffset = fifthsOffset;
        this.family = family;
    }

    /** Diatonic steps from the tonic for each degree. */
    public List<Integer> getSteps() { return steps; }

    /** Semitones from the tonic for each degree. */
    public List<Integer> getSemitones() { return semitones; }

    public int getFifthsOffset() { return fifthsOffset; }

    public Family getFamily() { return family; }

    /** Whole (W), half (H) and minor-third (3) step pattern, e.g. "WWHWWWH" for major, "WW3W3" for major pentatonic. Null for arpeggios. */
    public String getStepPattern() {
        if (family == Family.ARPEGGIO) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < semitones.size(); i++) {
            int next = i + 1 < semitones.size() ? semitones.get(i + 1) : 12;
            int d = next - semitones.get(i);
            b.append(d == 2 ? 'W' : d == 1 ? 'H' : d == 3 ? '3' : '?');
        }
        return b.toString();
    }

    private static List<Integer> steps7() { return list(0, 1, 2, 3, 4, 5, 6); }

    private static List<Integer> list(Integer... v) { return Collections.unmodifiableList(Arrays.asList(v)); }
}
