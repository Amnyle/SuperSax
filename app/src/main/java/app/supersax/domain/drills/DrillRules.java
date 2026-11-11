package app.supersax.domain.drills;

/** Shared rules of the finite phone drills. */
public final class DrillRules {
    /** Questions per round: a fixed length, then "done for today". */
    public static final int READING_ROUND = 15;
    public static final int MATCH_ROUND = 8;
    public static final int SCALE_ROUND = 8;
    public static final int FINGERING_ROUND = 10;
    public static final int MAX_LEVEL = 5;

    private DrillRules() {}

    /**
     * Adapts the level after a round: up after at least 90 percent correct with a mean answer time under
     * {@code fastMs}; down below 60 percent; otherwise unchanged.
     */
    public static int nextLevel(int level, int correct, int total, long meanMs, long fastMs) {
        if (total == 0) return level;
        double acc = (double) correct / total;
        if (acc >= 0.9 && meanMs <= fastMs) return Math.min(MAX_LEVEL, level + 1);
        if (acc < 0.6) return Math.max(1, level - 1);
        return level;
    }

    public static int nextLevel(int level, int correct, int total, long meanMs) { return nextLevel(level, correct, total, meanMs, 4000); }
}
