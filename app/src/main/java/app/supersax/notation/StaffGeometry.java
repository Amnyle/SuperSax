package app.supersax.notation;

import app.supersax.domain.pitch.Letter;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Everything is in staff spaces: the five lines are at y = 0 (top, F5) .. 4 (bottom, E4); y grows
 * downwards. A pitch's staff step counts diatonic steps from E4 (step 0); y = 4 − step / 2.
 */
public final class StaffGeometry {
    private static final int E4 = new SpelledPitch(Letter.E, 0, 4).getDiatonicIndex();
    /** Treble-clef steps of sharps (F C G D A E B) and flats (B E A D G C F). */
    public static final List<Integer> SHARP_STEPS = Collections.unmodifiableList(Arrays.asList(8, 5, 9, 6, 3, 7, 4));
    public static final List<Integer> FLAT_STEPS = Collections.unmodifiableList(Arrays.asList(4, 7, 3, 6, 2, 5, 1));

    private StaffGeometry() {}

    public static int step(SpelledPitch p) { return p.getDiatonicIndex() - E4; }

    public static float y(int step) { return 4f - step / 2f; }

    public static SpelledPitch pitchAt(int step, int alter) {
        int idx = E4 + step;
        return new SpelledPitch(Letter.ofIndex(idx), alter, Math.floorDiv(idx, 7));
    }

    public static SpelledPitch pitchAt(int step) { return pitchAt(step, 0); }

    /** Ledger-line steps needed for a note at {@code step}. */
    public static List<Integer> ledgerSteps(int step) {
        List<Integer> out = new ArrayList<>();
        if (step <= -2) for (int s = -2; s >= step; s--) { if (s % 2 == 0) out.add(s); }
        else if (step >= 10) for (int s = 10; s <= step; s++) { if (s % 2 == 0) out.add(s); }
        return out;
    }
}
