package app.supersax.domain.drills;

import app.supersax.domain.common.DrillKind;
import app.supersax.domain.common.DrillResultRecord;
import java.util.List;

/**
 * Star levels of the phone drills. A round scores 1 star at 60 %, 2 at 80 %, 3 at 100 %. Level 1 is always
 * open; the next level opens once a level has 2 stars. Stars come from the best round ever played at a level.
 */
public final class Levels {
    private Levels() {}

    public static final int STARS_TO_UNLOCK = 2;

    public static int stars(int correct, int total) {
        if (total <= 0) return 0;
        double r = (double) correct / total;
        if (correct >= total) return 3;
        if (r + 1e-9 >= 0.8) return 2;
        if (r + 1e-9 >= 0.6) return 1;
        return 0;
    }

    /** Number of levels of a drill ({@code lessons}: number of theory lessons). */
    public static int count(DrillKind kind, int lessons) {
        switch (kind) {
            case READING: return ReadingDrill.LEVELS;
            case SCALE_RECOGNITION: return ScaleRecognition.LEVELS;
            case FINGERING: return FingeringDrill.LEVELS;
            case THEORY_QUIZ: return lessons;
            default: return 1;
        }
    }

    /** Best stars earned at a level (0 if never played). Daily-mix results never count. */
    public static int best(List<DrillResultRecord> results, DrillKind kind, int level) {
        int best = 0;
        for (DrillResultRecord r : results) {
            if (r.getKind() == kind && r.getLevel() == level) best = Math.max(best, stars(r.getCorrect(), r.getQuestions()));
        }
        return best;
    }

    /** Highest open level: 1, plus one for each level from 1 upwards with at least 2 stars, capped at {@code max}. */
    public static int unlocked(List<DrillResultRecord> results, DrillKind kind, int max) {
        int level = 1;
        while (level < max && best(results, kind, level) >= STARS_TO_UNLOCK) level++;
        return level;
    }

    public static boolean isOpen(List<DrillResultRecord> results, DrillKind kind, int level, int max) {
        return level >= 1 && level <= unlocked(results, kind, max);
    }

    /** Total stars of a drill and the maximum possible. */
    public static int totalStars(List<DrillResultRecord> results, DrillKind kind, int max) {
        int n = 0;
        for (int l = 1; l <= max; l++) n += best(results, kind, l);
        return n;
    }

    /** Share of right answers over the last {@code last} rounds of a drill (null when never played). */
    public static Double recentAccuracy(List<DrillResultRecord> results, DrillKind kind, int last) {
        java.util.List<DrillResultRecord> mine = new java.util.ArrayList<>();
        for (DrillResultRecord r : results) if (r.getKind() == kind && r.getQuestions() > 0) mine.add(r);
        if (mine.isEmpty()) return null;
        mine.sort((a, b) -> Long.compare(b.getAtMs(), a.getAtMs()));
        int q = 0, c = 0;
        for (int i = 0; i < Math.min(last, mine.size()); i++) { q += mine.get(i).getQuestions(); c += mine.get(i).getCorrect(); }
        return (double) c / q;
    }
}
