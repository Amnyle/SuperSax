package app.supersax.domain.drills;

import app.supersax.domain.common.DrillKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * The daily exercise (5, 10 or 15 minutes, chosen each time): a short mix of the phone drills, each at the player's open level.
 * Base mix: 4 notes to read, 3 scales, 3 fingerings, 2 ear questions, 2 theory questions (theory only once a lesson was
 * passed; otherwise one more note and one more scale). The drill with the lowest recent accuracy gets
 * 2 extra questions. The order alternates drills so the same kind never comes twice in a row when avoidable.
 */
public final class DailyMix {
    private DailyMix() {}

    public static final List<DrillKind> KINDS = List.of(DrillKind.READING, DrillKind.SCALE_RECOGNITION, DrillKind.FINGERING, DrillKind.THEORY_QUIZ,
        DrillKind.EAR);

    /** The lengths offered, in minutes. */
    public static final List<Integer> MINUTES = List.of(5, 10, 15);

    /** The 5-minute mix. */
    public static Map<DrillKind, Integer> counts(boolean theoryAvailable, Map<DrillKind, Double> accuracy) {
        return counts(theoryAvailable, accuracy, 5);
    }

    /**
     * How many questions of each drill for a session of [minutes] (5, 10 or 15: the 5-minute mix times 1, 2 or 3,
     * extra questions on the weakest drill included). {@code accuracy}: recent share of right answers per drill
     * (missing = never played).
     */
    public static Map<DrillKind, Integer> counts(boolean theoryAvailable, Map<DrillKind, Double> accuracy, int minutes) {
        int times = Math.max(1, Math.min(3, Math.round(minutes / 5f)));
        Map<DrillKind, Integer> n = new EnumMap<>(DrillKind.class);
        n.put(DrillKind.READING, 4);
        n.put(DrillKind.SCALE_RECOGNITION, 3);
        n.put(DrillKind.FINGERING, 3);
        n.put(DrillKind.EAR, 2);
        if (theoryAvailable) n.put(DrillKind.THEORY_QUIZ, 2);
        else { n.put(DrillKind.READING, 5); n.put(DrillKind.SCALE_RECOGNITION, 4); }
        DrillKind weakest = null;
        double low = Double.MAX_VALUE;
        for (DrillKind k : n.keySet()) {
            Double a = accuracy.get(k);
            if (a != null && a < low) { low = a; weakest = k; }
        }
        if (weakest != null && low < 0.9) n.put(weakest, n.get(weakest) + 2);
        if (times > 1) n.replaceAll((k, v) -> v * times);
        return n;
    }

    /** The order of the questions: drills alternate, the one with most questions left goes first (ties: seeded). */
    public static List<DrillKind> order(Map<DrillKind, Integer> counts, long seed) {
        Map<DrillKind, Integer> left = new EnumMap<>(counts);
        Random rnd = new Random(seed);
        List<DrillKind> out = new ArrayList<>();
        @Nullable DrillKind previous = null;
        while (true) {
            List<DrillKind> best = new ArrayList<>();
            int max = 0;
            for (Map.Entry<DrillKind, Integer> e : left.entrySet()) {
                if (e.getValue() <= 0) continue;
                if (e.getKey() == previous && left.values().stream().filter(v -> v > 0).count() > 1) continue;
                if (e.getValue() > max) { max = e.getValue(); best.clear(); best.add(e.getKey()); }
                else if (e.getValue() == max) best.add(e.getKey());
            }
            if (best.isEmpty()) break;
            DrillKind k = best.get(rnd.nextInt(best.size()));
            out.add(k);
            left.put(k, left.get(k) - 1);
            previous = k;
        }
        return out;
    }

    /**
     * Days in a row the daily exercise was done, ending today, or ending yesterday when today is not
     * done yet (the streak is still alive until the end of today).
     */
    public static int streak(Set<Long> doneEpochDays, long todayEpochDay) {
        long day = doneEpochDays.contains(todayEpochDay) ? todayEpochDay : todayEpochDay - 1;
        int n = 0;
        while (doneEpochDays.contains(day)) { n++; day--; }
        return n;
    }
}
