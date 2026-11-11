package app.supersax.domain.drills;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Match-the-note drill: written notes within the player's range; the tuner confirms the match. */
public final class MatchDrill {
    /** Cents within which a sounded note counts as matching, and how long it must be held. */
    public static final double TOLERANCE_CENTS = 25.0;
    public static final double HOLD_SEC = 1.0;

    private MatchDrill() {}

    public static List<Integer> round(int lowWrittenMidi, int highWrittenMidi, int count, long seed) {
        Random rnd = new Random(seed);
        List<Integer> out = new ArrayList<>();
        while (out.size() < count) {
            int m = lowWrittenMidi + rnd.nextInt(highWrittenMidi - lowWrittenMidi + 1);
            if (out.isEmpty() || out.get(out.size() - 1) != m) out.add(m);
        }
        return out;
    }

    public static List<Integer> round(int lowWrittenMidi, int highWrittenMidi, long seed) {
        return round(lowWrittenMidi, highWrittenMidi, DrillRules.MATCH_ROUND, seed);
    }
}
