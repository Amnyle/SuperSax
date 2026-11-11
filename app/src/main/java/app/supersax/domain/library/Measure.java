package app.supersax.domain.library;

import java.util.List;

/** One bar of a single-voice score. */
public final class Measure {
    private final List<ScoreNote> notes;

    public Measure(List<ScoreNote> notes) { this.notes = notes; }

    public List<ScoreNote> getNotes() { return notes; }

    /** Total length as a fraction of a whole note. */
    public Fraction getLength() {
        Fraction f = Fraction.ZERO;
        for (ScoreNote n : notes) f = f.plus(n.getDuration());
        return f;
    }

    @Override public boolean equals(Object o) { return o instanceof Measure && ((Measure) o).notes.equals(notes); }

    @Override public int hashCode() { return notes.hashCode(); }
}
