package app.supersax.domain.generator;

import app.supersax.domain.program.PlayerState;
import app.supersax.domain.program.ScaleProgress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * The owner's own training rules, as pure functions over {@link PlayerState}.
 * <ul>
 *   <li>Two clean runs in a row add 5 BPM for that scale.</li>
 *   <li>A new scale unlocks when the last two unlocked scales are solid.</li>
 *   <li>Long tones grow only if the player did not tire early (and drift is under control).</li>
 * </ul>
 */
public final class Adaptation {
    private Adaptation() {}

    /** Outcome of one run: the new state, whether the tempo went up, the scale's tempo now, and any newly unlocked scale. */
    public static final class RunResult {
        private final PlayerState state;
        private final boolean bumped;
        private final int newBpm;
        private final @Nullable String unlocked;

        public RunResult(PlayerState state, boolean bumped, int newBpm, @Nullable String unlocked) {
            this.state = state; this.bumped = bumped; this.newBpm = newBpm; this.unlocked = unlocked;
        }

        public PlayerState getState() { return state; }
        public boolean getBumped() { return bumped; }
        public int getNewBpm() { return newBpm; }
        public @Nullable String getUnlocked() { return unlocked; }
    }

    private static ScaleProgress progress(ScaleProgress p, int bpm, int consecutiveClean, int bestCleanBpm, long lastPracticed) {
        return new ScaleProgress(p.getScaleKey(), bpm, consecutiveClean, bestCleanBpm, p.getUnlockedAtEpochMs(), lastPracticed);
    }

    /**
     * Records one run of {@code scaleKey} at {@code bpm}. A clean run extends the streak; a mistake resets it.
     * When the streak reaches the configured number of clean runs, the tempo rises by the configured step for
     * next time and the streak restarts. Then the unlock rule is applied.
     */
    public static RunResult recordRun(PlayerState state, String scaleKey, int bpm, boolean clean, long nowMs, AdaptationConfig config) {
        ScaleProgress existing = state.scale(scaleKey);
        ScaleProgress p = existing != null ? existing : new ScaleProgress(scaleKey, bpm, 0, 0, nowMs, 0);
        p = progress(p, p.getBpm(), p.getConsecutiveClean(), p.getBestCleanBpm(), nowMs);
        boolean bumped = false;
        if (clean) {
            int streak = p.getConsecutiveClean() + 1;
            if (streak >= config.getCleanRunsForBump()) {
                int newBpm = Math.min(config.getMaxBpm(), Math.max(p.getBpm(), bpm) + config.getBpmStep());
                p = progress(p, newBpm, 0, Math.max(p.getBestCleanBpm(), bpm), nowMs);
                bumped = true;
            } else {
                p = progress(p, p.getBpm(), streak, p.getBestCleanBpm(), nowMs);
            }
        } else {
            p = progress(p, p.getBpm(), 0, p.getBestCleanBpm(), nowMs);
        }
        List<ScaleProgress> scales = new ArrayList<>();
        for (ScaleProgress s : state.getScales()) scales.add(s.getScaleKey().equals(scaleKey) ? p : s);
        if (existing == null) scales.add(p);
        PlayerState newState = state.withScales(scales);
        String unlocked = nextUnlock(newState, config);
        if (unlocked != null) {
            List<ScaleProgress> more = new ArrayList<>(newState.getScales());
            more.add(new ScaleProgress(unlocked, startBpm(newState, config), 0, 0, nowMs, 0));
            newState = newState.withScales(more);
        }
        return new RunResult(newState, bumped, p.getBpm(), unlocked);
    }

    /** A scale is solid once a clean pair has been played at or above the configured tempo. */
    public static boolean isSolid(ScaleProgress p, AdaptationConfig config) { return p.getBestCleanBpm() >= config.getSolidMinBpm(); }

    /**
     * The next scale in the unlock order if the two most recently unlocked scales are both solid (or the
     * only one is, when there is one), else null.
     */
    public static @Nullable String nextUnlock(PlayerState state, AdaptationConfig config) {
        List<ScaleProgress> unlocked = state.getScales();
        String candidate = null;
        for (String key : config.getUnlockOrder()) {
            boolean has = false;
            for (ScaleProgress s : unlocked) if (s.getScaleKey().equals(key)) { has = true; break; }
            if (!has) { candidate = key; break; }
        }
        if (candidate == null) return null;
        if (unlocked.isEmpty()) return candidate;
        List<ScaleProgress> sorted = new ArrayList<>(unlocked);
        sorted.sort(Comparator.comparingLong(ScaleProgress::getUnlockedAtEpochMs));
        for (ScaleProgress s : sorted.subList(Math.max(0, sorted.size() - 2), sorted.size())) if (!isSolid(s, config)) return null;
        return candidate;
    }

    private static int startBpm(PlayerState state, AdaptationConfig config) {
        int min = Integer.MAX_VALUE;
        for (ScaleProgress s : state.getScales()) min = Math.min(min, s.getBpm());
        if (min == Integer.MAX_VALUE) min = config.getMinBpm();
        return Math.max(config.getMinBpm(), min);
    }

    /**
     * Long-tone hold target after a session (stamina ramp). It grows by one step only if the long-tone work
     * was completed without tiring early, the tiredness rating was 3 or less, and, when tuner data exists,
     * the average absolute drift stayed under the threshold. Tiring early lowers it by one step.
     */
    public static int nextHoldTarget(int currentSec, boolean tiredEarly, @Nullable Integer tirednessRating,
                                     @Nullable Double meanAbsDriftCents, AdaptationConfig config) {
        if (tiredEarly) return Math.max(config.getHoldMinSec(), currentSec - config.getHoldStepSec());
        if ((tirednessRating != null ? tirednessRating : 3) > 3) return currentSec;
        if (meanAbsDriftCents != null && meanAbsDriftCents > config.getMaxDriftForGrowthCents()) return currentSec;
        return Math.min(config.getHoldMaxSec(), currentSec + config.getHoldStepSec());
    }

    /**
     * Embouchure endurance cap after a session: after N sessions in a row without "lips tired" the cap
     * grows by one step; a tired session resets the counter.
     */
    public static PlayerState afterSession(PlayerState state, boolean lipsTired, AdaptationConfig config) {
        if (lipsTired) return state.withStamina(state.getEnduranceCapSec(), 0);
        int n = state.getSessionsWithoutFatigue() + 1;
        if (n >= config.getStaminaSessionsForStep()) {
            return state.withStamina(Math.min(config.getStaminaMaxSec(), state.getEnduranceCapSec() + config.getStaminaStepSec()), 0);
        }
        return state.withStamina(state.getEnduranceCapSec(), n);
    }

    /** Was a block ended before the configured share of its planned time? */
    public static boolean tiredEarly(int plannedSec, int actualSec, AdaptationConfig config) {
        return plannedSec > 0 && actualSec < plannedSec * config.getTiredEarlyShare();
    }
}
