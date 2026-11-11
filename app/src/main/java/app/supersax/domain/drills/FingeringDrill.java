package app.supersax.domain.drills;

import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import kotlin.Pair;

/** Fingering drill: tap the keys of a note, or name the note of a fingering. Standard range only. */
public final class FingeringDrill {
    private FingeringDrill() {}

    /** Number of levels, from the left hand alone to the full standard range. */
    public static final int LEVELS = 5;

    /**
     * Notes of a level (written): 1 left hand G A B C, 2 naturals D4–C5 (both hands), 3 naturals D4–D6
     * (octave key), 4 all notes D4–D6, 5 the full standard range low B♭ to high F♯. Altissimo is never asked.
     */
    public static List<FingeringEntry> pool(FingeringChart chart, int level) {
        List<FingeringEntry> out = new ArrayList<>();
        for (FingeringEntry e : chart.getEntries()) {
            if (e.getAltissimo()) continue;
            SpelledPitch p = SpelledPitch.parse(e.getNote());
            int m = p.getMidi();
            boolean natural = p.getAlter() == 0;
            boolean in;
            switch (level) {
                case 1: in = natural && m >= 67 && m <= 72; break;
                case 2: in = natural && m >= 62 && m <= 72; break;
                case 3: in = natural && m >= 62 && m <= 86; break;
                case 4: in = m >= 62 && m <= 86; break;
                default: in = true;
            }
            if (in) out.add(e);
        }
        return out;
    }

    /** Questions at a level: an entry and true = tap the keys, false = name the note. */
    public static List<Pair<FingeringEntry, Boolean>> round(FingeringChart chart, int level, int count, long seed) {
        return round(pool(chart, level), count, seed);
    }

    /** Questions over the whole standard range (no level). */
    public static List<Pair<FingeringEntry, Boolean>> round(FingeringChart chart, int count, long seed) {
        return round(pool(chart, LEVELS), count, seed);
    }

    private static List<Pair<FingeringEntry, Boolean>> round(List<FingeringEntry> pool, int count, long seed) {
        Random rnd = new Random(seed);
        List<Pair<FingeringEntry, Boolean>> out = new ArrayList<>();
        while (out.size() < count) {
            FingeringEntry e = pool.get(rnd.nextInt(pool.size()));
            if (pool.size() == 1 || out.isEmpty() || !out.get(out.size() - 1).getFirst().equals(e)) out.add(new Pair<>(e, rnd.nextBoolean()));
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
