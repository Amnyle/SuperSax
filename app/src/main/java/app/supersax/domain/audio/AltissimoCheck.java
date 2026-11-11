package app.supersax.domain.audio;

import app.supersax.domain.pitch.Instrument;
import app.supersax.domain.pitch.Tuning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Altissimo verification: the player holds a written note from G6 up for about two seconds; a pitch
 * within a generous tolerance writes a tuner-verified Range note.
 */
public final class AltissimoCheck {
    public static final double TOLERANCE_CENTS = 40.0;
    public static final double HOLD_SEC = 2.0;
    /** Written G6 .. D7. */
    public static final List<Integer> TARGETS;

    static {
        List<Integer> t = new ArrayList<>();
        for (int m = 91; m <= 98; m++) t.add(m);
        TARGETS = Collections.unmodifiableList(t);
    }

    private AltissimoCheck() {}

    public static boolean matches(double detectedHz, int writtenMidi, double a4, Instrument instrument) {
        double ref = Tuning.midiToFrequency(instrument.writtenToConcertMidi(writtenMidi), a4);
        return Math.abs(Tuning.cents(detectedHz, ref)) <= TOLERANCE_CENTS;
    }

    public static boolean matches(double detectedHz, int writtenMidi, double a4) { return matches(detectedHz, writtenMidi, a4, Instrument.ALTO_SAX); }
}
