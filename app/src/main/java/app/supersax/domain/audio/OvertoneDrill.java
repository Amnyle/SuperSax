package app.supersax.domain.audio;

import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.Tuning;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Overtone drill: finger a low note and overblow it to a higher partial (2 = octave, 3 = octave and a
 * fifth). The expected frequency is the fingered note's concert frequency × the partial number; a
 * sounding frequency within {@link #TOLERANCE_CENTS} held for {@link #HOLD_SEC} counts.
 */
public final class OvertoneDrill {
    public static final double TOLERANCE_CENTS = 35.0;
    public static final double HOLD_SEC = 1.0;

    /** A target: the written fingering (low note) and the partial to sound. */
    public static final class Target {
        private final int writtenFingeringMidi;
        private final int partial;

        public Target(int writtenFingeringMidi, int partial) {
            this.writtenFingeringMidi = writtenFingeringMidi;
            this.partial = partial;
        }

        public int getWrittenFingeringMidi() { return writtenFingeringMidi; }

        public int getPartial() { return partial; }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Target)) return false;
            Target t = (Target) o;
            return writtenFingeringMidi == t.writtenFingeringMidi && partial == t.partial;
        }

        @Override public int hashCode() { return Objects.hash(writtenFingeringMidi, partial); }
    }

    /** The milestone set: low D, C and B♭ fingerings, 2nd and 3rd partials. */
    public static final List<Target> milestone = Collections.unmodifiableList(Arrays.asList(
        new Target(62, 2), new Target(60, 2), new Target(58, 2),
        new Target(62, 3), new Target(60, 3), new Target(58, 3)));

    private OvertoneDrill() {}

    /** All milestone targets are needed. */
    public static boolean milestoneDone(Set<Target> done) { return done.containsAll(milestone); }

    public static double expectedHz(Target t, double a4, Instrument instrument) {
        return Tuning.midiToFrequency(instrument.writtenToConcertMidi(t.getWrittenFingeringMidi()), a4) * t.getPartial();
    }

    public static double expectedHz(Target t, double a4) { return expectedHz(t, a4, Instrument.ALTO_SAX); }

    /** Cents between a detected frequency and the target partial. */
    public static double centsOff(double detectedHz, Target t, double a4) { return Tuning.cents(detectedHz, expectedHz(t, a4)); }

    public static boolean matches(double detectedHz, Target t, double a4) { return Math.abs(centsOff(detectedHz, t, a4)) <= TOLERANCE_CENTS; }

    /** The written note that sounds when overblowing (for display), rounded to the nearest semitone. */
    public static int soundingWrittenMidi(Target t, double a4, Instrument instrument) {
        return instrument.concertToWrittenMidi((int) Math.round(Tuning.frequencyToMidi(expectedHz(t, a4, instrument), a4)));
    }

    public static int soundingWrittenMidi(Target t, double a4) { return soundingWrittenMidi(t, a4, Instrument.ALTO_SAX); }
}
