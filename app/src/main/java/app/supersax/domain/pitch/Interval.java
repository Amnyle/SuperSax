package app.supersax.domain.pitch;

import java.util.Objects;

/**
 * An interval measured both in diatonic steps (letter distance) and semitones, so that transposing keeps
 * correct spelling (C up a major sixth is A, never B𝄫).
 */
public final class Interval {
    public static final Interval UNISON = new Interval(0, 0);
    public static final Interval MAJOR_SIXTH = new Interval(5, 9);
    /** Enharmonic twin of the major sixth, used to avoid keys with more than six sharps. */
    public static final Interval DIMINISHED_SEVENTH = new Interval(6, 9);
    public static final Interval OCTAVE = new Interval(7, 12);

    private final int diatonicSteps;
    private final int semitones;

    public Interval(int diatonicSteps, int semitones) {
        this.diatonicSteps = diatonicSteps;
        this.semitones = semitones;
    }

    public int getDiatonicSteps() { return diatonicSteps; }

    public int getSemitones() { return semitones; }

    /** The same interval downwards (Kotlin: {@code -interval}). */
    public Interval unaryMinus() { return new Interval(-diatonicSteps, -semitones); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Interval)) return false;
        Interval i = (Interval) o;
        return diatonicSteps == i.diatonicSteps && semitones == i.semitones;
    }

    @Override public int hashCode() { return Objects.hash(diatonicSteps, semitones); }

    @Override public String toString() { return "Interval(" + diatonicSteps + ", " + semitones + ")"; }
}
