package app.supersax.domain.pitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A key signature as a count of fifths: positive sharps, negative flats (-7..7). */
public final class KeySignature {
    public static final List<Letter> SHARPS = Collections.unmodifiableList(
        Arrays.asList(Letter.F, Letter.C, Letter.G, Letter.D, Letter.A, Letter.E, Letter.B));
    public static final List<Letter> FLATS;
    private static final Map<Letter, Integer> NATURAL_MAJOR_FIFTHS = new EnumMap<>(Letter.class);

    static {
        List<Letter> f = new ArrayList<>(SHARPS);
        Collections.reverse(f);
        FLATS = Collections.unmodifiableList(f);
        NATURAL_MAJOR_FIFTHS.put(Letter.F, -1);
        NATURAL_MAJOR_FIFTHS.put(Letter.C, 0);
        NATURAL_MAJOR_FIFTHS.put(Letter.G, 1);
        NATURAL_MAJOR_FIFTHS.put(Letter.D, 2);
        NATURAL_MAJOR_FIFTHS.put(Letter.A, 3);
        NATURAL_MAJOR_FIFTHS.put(Letter.E, 4);
        NATURAL_MAJOR_FIFTHS.put(Letter.B, 5);
    }

    private final int fifths;

    public KeySignature(int fifths) { this.fifths = fifths; }

    public int getFifths() { return fifths; }

    /** Major tonic of this signature (C for 0, G for 1, F for -1...). */
    public PitchClass getMajorTonic() {
        PitchClass pc = new PitchClass(Letter.C);
        Interval step = fifths >= 0 ? new Interval(4, 7) : new Interval(3, 5);
        for (int i = 0; i < Math.abs(fifths); i++) pc = pc.transpose(step);
        return pc;
    }

    public PitchClass getMinorTonic() { return getMajorTonic().transpose(new Interval(-2, -3)); }

    /** The altered letters, in order of appearance on the staff. */
    public List<PitchClass> getAccidentals() {
        List<PitchClass> out = new ArrayList<>();
        if (fifths >= 0) for (int i = 0; i < fifths; i++) out.add(new PitchClass(SHARPS.get(i), 1));
        else for (int i = 0; i < -fifths; i++) out.add(new PitchClass(FLATS.get(i), -1));
        return out;
    }

    /** Alteration this key signature applies to {@code letter}. */
    public int alterFor(Letter letter) {
        for (PitchClass pc : getAccidentals()) if (pc.getLetter() == letter) return pc.getAlter();
        return 0;
    }

    /** Fifths of the major key on {@code tonic} (E♭ → -3, F♯ → 6). */
    public static int majorFifths(PitchClass tonic) {
        return NATURAL_MAJOR_FIFTHS.get(tonic.getLetter()) + 7 * tonic.getAlter();
    }

    @Override public boolean equals(Object o) { return o instanceof KeySignature && ((KeySignature) o).fifths == fifths; }

    @Override public int hashCode() { return fifths; }

    @Override public String toString() { return "KeySignature(" + fifths + ")"; }
}
