package app.supersax.domain.drills;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import kotlin.Pair;

/** Fingering drill: tap the keys of a note, or name the note of a fingering. Standard range only. */
public final class FingeringDrill {
    private FingeringDrill() {}

    /** Questions: an entry and true = tap the keys, false = name the note. */
    public static List<Pair<FingeringEntry, Boolean>> round(FingeringChart chart, int count, long seed) {
        List<FingeringEntry> pool = new ArrayList<>();
        for (FingeringEntry e : chart.getEntries()) if (!e.getAltissimo()) pool.add(e);
        Random rnd = new Random(seed);
        List<Pair<FingeringEntry, Boolean>> out = new ArrayList<>();
        while (out.size() < count) {
            FingeringEntry e = pool.get(rnd.nextInt(pool.size()));
            if (out.isEmpty() || !out.get(out.size() - 1).getFirst().equals(e)) out.add(new Pair<>(e, rnd.nextBoolean()));
        }
        return out;
    }

    public static List<Pair<FingeringEntry, Boolean>> round(FingeringChart chart, long seed) { return round(chart, DrillRules.FINGERING_ROUND, seed); }

    /** The tapped keys match the main fingering or one of the alternates. */
    public static boolean checkKeys(FingeringEntry e, Set<SaxKey> tapped) {
        if (tapped.equals(e.getKeys())) return true;
        for (Set<SaxKey> alt : e.getAlternates()) if (tapped.equals(alt)) return true;
        return false;
    }
}
