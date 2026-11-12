package app.supersax.domain.drills;

import app.supersax.domain.common.DrillKind;
import app.supersax.domain.common.DrillResultRecord;
import java.util.List;

/**
 * Star levels of the phone drills, with a high ceiling: 1 star for 80 % right; 2 stars for 90 % right at a good
 * speed; 3 stars only for a perfect round at expert speed (mean time per answer, or for rhythm the mean timing
 * error). Level 1 is always open; the next opens with 1 star. Stars come from the best round ever played.
 */
public final class Levels {
    private Levels() {}

    public static final int STARS_TO_UNLOCK = 1;

    /** Mean milliseconds per answer (rhythm: mean timing error) needed for 2 and 3 stars; null: accuracy only. */
    public static long[] speedLimits(DrillKind kind) {
        switch (kind) {
            case READING: return new long[]{2500, 1200};
            case SCALE_RECOGNITION: return new long[]{9000, 4500};
            case FINGERING: return new long[]{6000, 3000};
            case EAR: return new long[]{7000, 4000};
            case RHYTHM: return new long[]{70, 35};
            default: return null;
        }
    }

    /** Stars of a round of [kind]: accuracy, then speed for 2 and 3 stars. */
    public static int stars(DrillKind kind, int correct, int total, long meanMs) {
        if (total <= 0) return 0;
        double r = (double) correct / total;
        long[] lim = speedLimits(kind);
        if (r + 1e-9 < 0.8) return 0;
        boolean fast2 = lim == null || (meanMs > 0 && meanMs <= lim[0]);
        boolean fast3 = lim == null || (meanMs > 0 && meanMs <= lim[1]);
        if (correct >= total && fast3) return 3;
        if (r + 1e-9 >= 0.9 && fast2) return 2;
        return 1;
    }

    /** Timed reading (60 s): stars from notes named right, with at most one wrong in ten. */
    public static int speedStars(int right, int answered) {
        if (answered <= 0 || right < 0.9 * answered - 1e-9) return 0;
        if (right >= 45) return 3;
        if (right >= 28) return 2;
        if (right >= 15) return 1;
        return 0;
    }

    /** Accuracy only (theory lessons): 1 star at 80 %, 2 at 90 %, 3 at 100 %. */
    public static int stars(int correct, int total) {
        if (total <= 0) return 0;
        double r = (double) correct / total;
        if (correct >= total) return 3;
        if (r + 1e-9 >= 0.9) return 2;
        if (r + 1e-9 >= 0.8) return 1;
        return 0;
    }

    /** Number of levels of a drill ({@code lessons}: number of theory lessons). */
    public static int count(DrillKind kind, int lessons) {
        switch (kind) {
            case READING: return ReadingDrill.LEVELS;
            case SCALE_RECOGNITION: return ScaleRecognition.LEVELS;
            case FINGERING: return FingeringDrill.LEVELS;
            case THEORY_QUIZ: return lessons;
            case EAR: return EarDrill.LEVELS;
            case RHYTHM: return RhythmDrill.LEVELS;
            default: return 1;
        }
    }

    /** Best stars earned at a level (0 if never played). Daily-mix results never count. */
    public static int best(List<DrillResultRecord> results, DrillKind kind, int level) {
        int best = 0;
        for (DrillResultRecord r : results) {
            if (r.getKind() == kind && r.getLevel() == level) best = Math.max(best, kind == DrillKind.READING_SPEED
                ? speedStars(r.getCorrect(), r.getQuestions()) : stars(kind, r.getCorrect(), r.getQuestions(), r.getMeanResponseMs()));
        }
        return best;
    }

    /** Highest open level: 1, plus one for each level from 1 upwards with a star, capped at {@code max}. */
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
