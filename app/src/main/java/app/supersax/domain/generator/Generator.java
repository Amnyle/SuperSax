package app.supersax.domain.generator;

import app.supersax.domain.program.Block;
import app.supersax.domain.program.BlockKind;
import app.supersax.domain.program.Exercise;
import app.supersax.domain.program.ExerciseType;
import app.supersax.domain.program.Focus;
import app.supersax.domain.program.PhoneDrillSlot;
import app.supersax.domain.program.Pillar;
import app.supersax.domain.program.Program;
import app.supersax.domain.program.ProgramDay;
import app.supersax.domain.program.ProgramOrigin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.Nullable;

/**
 * Rule-based, deterministic program generator (pure functions).
 *
 * <p>A session is a priority-ordered set of blocks cut to the available minutes. Playing order (the
 * {@link BlockKind} declaration order) is separate from priority ({@code GeneratorConfig.dropOrder}).
 */
public final class Generator {
    public static final List<BlockKind> PLAYING_KINDS = Collections.unmodifiableList(
        Arrays.asList(BlockKind.LONG_TONES, BlockKind.SCALES, BlockKind.NEW_MATERIAL, BlockKind.PLAY_FOR_FUN));

    /** Note key added to {@code Program.notes} when the limiter is Consistency. */
    public static final String NOTE_CONSISTENCY_PREFIX = "consistency_shorter:";

    private static final int MIN_PHONE_MINUTES = 3;
    private static final double EPS = 1e-9;

    private Generator() {}

    /** What the generator needs to know. */
    public static final class Input {
        private final String programId, name;
        private final Set<Integer> days;
        private final int minutesPerSession, enduranceCapSec;
        private final List<Focus> focus;
        private final @Nullable Pillar limiter;
        /** Phone drill types that exist in this build; only these are scheduled. */
        private final List<ExerciseType> availablePhoneDrills;
        private final @Nullable Integer restSec;
        private final long createdAtEpochMs;

        public Input(String programId, String name, Set<Integer> days, int minutesPerSession, int enduranceCapSec, List<Focus> focus,
                     @Nullable Pillar limiter, List<ExerciseType> availablePhoneDrills, @Nullable Integer restSec, long createdAtEpochMs) {
            this.programId = programId; this.name = name; this.days = days; this.minutesPerSession = minutesPerSession;
            this.enduranceCapSec = enduranceCapSec; this.focus = focus; this.limiter = limiter;
            this.availablePhoneDrills = availablePhoneDrills; this.restSec = restSec; this.createdAtEpochMs = createdAtEpochMs;
        }

        public Input(String programId, String name, Set<Integer> days, int minutesPerSession, int enduranceCapSec, List<Focus> focus,
                     @Nullable Pillar limiter, List<ExerciseType> availablePhoneDrills) {
            this(programId, name, days, minutesPerSession, enduranceCapSec, focus, limiter, availablePhoneDrills, null, 0L);
        }

        public Input(String programId, String name, Set<Integer> days, int minutesPerSession, int enduranceCapSec) {
            this(programId, name, days, minutesPerSession, enduranceCapSec, Collections.emptyList(), null, Collections.emptyList());
        }

        public String getProgramId() { return programId; }
        public String getName() { return name; }
        public Set<Integer> getDays() { return days; }
        public int getMinutesPerSession() { return minutesPerSession; }
        public int getEnduranceCapSec() { return enduranceCapSec; }
        public List<Focus> getFocus() { return focus; }
        public @Nullable Pillar getLimiter() { return limiter; }
        public List<ExerciseType> getAvailablePhoneDrills() { return availablePhoneDrills; }
        public @Nullable Integer getRestSec() { return restSec; }
        public long getCreatedAtEpochMs() { return createdAtEpochMs; }
    }

    /**
     * The starting split for {@code minutes}, interpolated linearly between table rows (scaled from the
     * nearest row outside the table) and rounded with the largest-remainder method so the total is exact.
     */
    public static Map<BlockKind, Integer> baseSplit(int minutes, GeneratorConfig config) {
        if (minutes <= 0) throw new IllegalArgumentException("minutes must be positive");
        List<SplitRow> rows = new ArrayList<>(config.getSplits());
        rows.sort(Comparator.comparingInt(SplitRow::getMinutes));
        SplitRow first = rows.get(0), last = rows.get(rows.size() - 1);
        Map<BlockKind, Double> raw;
        if (minutes <= first.getMinutes()) raw = scaleRow(first, minutes);
        else if (minutes >= last.getMinutes()) raw = scaleRow(last, minutes);
        else {
            SplitRow hi = null, lo = null;
            for (SplitRow r : rows) if (hi == null && r.getMinutes() >= minutes) hi = r;
            for (SplitRow r : rows) if (r.getMinutes() <= minutes) lo = r;
            if (hi.getMinutes() == lo.getMinutes()) raw = scaleRow(lo, minutes);
            else {
                double t = (double) (minutes - lo.getMinutes()) / (hi.getMinutes() - lo.getMinutes());
                raw = new LinkedHashMap<>();
                for (BlockKind k : PLAYING_KINDS) raw.put(k, lo.of(k) + t * (hi.of(k) - lo.of(k)));
            }
        }
        return apportion(raw, minutes);
    }

    private static Map<BlockKind, Double> scaleRow(SplitRow row, int minutes) {
        Map<BlockKind, Double> m = new LinkedHashMap<>();
        for (BlockKind k : PLAYING_KINDS) m.put(k, row.of(k) * (double) minutes / row.getMinutes());
        return m;
    }

    private static int floorInt(double v) { return (int) Math.floor(v + EPS); }

    private static double frac(double v) { return v - Math.floor(v + EPS); }

    /** Largest-remainder rounding: integer parts, then the biggest fractions get the leftover minutes (ties: playing order). */
    public static Map<BlockKind, Integer> apportion(Map<BlockKind, Double> raw, int total) {
        double sum = 0;
        for (double v : raw.values()) sum += v;
        Map<BlockKind, Double> scaled = new LinkedHashMap<>();
        for (Map.Entry<BlockKind, Double> e : raw.entrySet()) scaled.put(e.getKey(), sum <= 0 ? e.getValue() : e.getValue() * total / sum);
        Map<BlockKind, Integer> floors = new LinkedHashMap<>();
        int left = total;
        for (Map.Entry<BlockKind, Double> e : scaled.entrySet()) { int f = floorInt(e.getValue()); floors.put(e.getKey(), f); left -= f; }
        List<Map.Entry<BlockKind, Double>> order = new ArrayList<>(scaled.entrySet());
        order.sort((a, b) -> {
            int c = Double.compare(frac(b.getValue()), frac(a.getValue()));
            return c != 0 ? c : Integer.compare(a.getKey().ordinal(), b.getKey().ordinal());
        });
        for (int i = 0; left > 0 && !order.isEmpty(); i++, left--) {
            BlockKind k = order.get(i % order.size()).getKey();
            floors.put(k, floors.get(k) + 1);
        }
        Map<BlockKind, Integer> out = new LinkedHashMap<>();
        for (BlockKind k : PLAYING_KINDS) out.put(k, floors.getOrDefault(k, 0));
        return out;
    }

    /** Block that receives extra minutes for a focus, or null if the focus is served by phone drills. */
    public static @Nullable BlockKind receiverFor(Focus focus, Map<BlockKind, Integer> split) {
        switch (focus) {
            case TONE: return BlockKind.LONG_TONES;
            case TECHNIQUE: return BlockKind.SCALES;
            case REPERTOIRE: return split.getOrDefault(BlockKind.NEW_MATERIAL, 0) > 0 ? BlockKind.NEW_MATERIAL : BlockKind.PLAY_FOR_FUN;
            default: return null;
        }
    }

    public static @Nullable BlockKind receiverFor(Pillar limiter, Map<BlockKind, Integer> split) {
        switch (limiter) {
            case SOUND: case RANGE: return BlockKind.LONG_TONES;
            case TECHNIQUE: return BlockKind.SCALES;
            case REPERTOIRE: return split.getOrDefault(BlockKind.NEW_MATERIAL, 0) > 0 ? BlockKind.NEW_MATERIAL : BlockKind.PLAY_FOR_FUN;
            default: return null;
        }
    }

    /**
     * Moves {@code round(minutes × share)} (at least one) minutes to {@code receiver}, taken from the other
     * blocks in drop order. Donors keep at least the minimum block length; protected blocks and blocks in
     * {@code exclude} (earlier receivers) never donate.
     */
    public static Map<BlockKind, Integer> shift(Map<BlockKind, Integer> split, BlockKind receiver, double share, GeneratorConfig config,
                                                Set<BlockKind> exclude) {
        int total = 0;
        for (int v : split.values()) total += v;
        int wanted = Math.max(1, (int) Math.round(total * share));
        Map<BlockKind, Integer> out = new LinkedHashMap<>(split);
        for (BlockKind donor : config.getDropOrder()) {
            if (donor == receiver || exclude.contains(donor) || wanted == 0) continue;
            int have = out.getOrDefault(donor, 0);
            if (have <= 0) continue;
            int give = Math.max(0, Math.min(wanted, have - config.getMinBlockMinutes()));
            out.put(donor, have - give);
            out.put(receiver, out.getOrDefault(receiver, 0) + give);
            wanted -= give;
        }
        return out;
    }

    public static Map<BlockKind, Integer> shift(Map<BlockKind, Integer> split, BlockKind receiver, double share, GeneratorConfig config) {
        return shift(split, receiver, share, config, Collections.emptySet());
    }

    /** Applies focus (up to two) and the limiter to the base split. */
    public static Map<BlockKind, Integer> adjustedSplit(int minutes, List<Focus> focus, @Nullable Pillar limiter, GeneratorConfig config) {
        Map<BlockKind, Integer> split = baseSplit(minutes, config);
        Set<BlockKind> received = EnumSet.noneOf(BlockKind.class);
        for (int i = 0; i < Math.min(2, focus.size()); i++) {
            BlockKind r = receiverFor(focus.get(i), split);
            if (r != null) { split = shift(split, r, config.getFocusShare(), config, received); received.add(r); }
        }
        if (limiter != null) {
            BlockKind r = receiverFor(limiter, split);
            if (r != null) { split = shift(split, r, config.getLimiterShare(), config, received); received.add(r); }
        }
        return split;
    }

    private static int sumMinutes(List<Block> blocks) { int s = 0; for (Block b : blocks) s += b.getMinutes(); return s; }

    private static List<Block> without(List<Block> blocks, BlockKind kind) {
        List<Block> out = new ArrayList<>();
        for (Block b : blocks) if (b.getKind() != kind) out.add(b);
        return out;
    }

    /**
     * Cuts a day to {@code available} minutes. If over budget, the first kind in the drop order is removed
     * entirely; the rest is then shrunk proportionally (each block keeps at least one minute); if it still
     * does not fit, the next droppable kind goes. Protected kinds are never removed.
     */
    public static List<Block> fitToMinutes(List<Block> blocks, int available, GeneratorConfig config) {
        List<Block> current = new ArrayList<>();
        for (Block b : blocks) if (b.getMinutes() > 0) current.add(b);
        if (sumMinutes(current) <= available) return current;
        List<BlockKind> droppable = new ArrayList<>(config.getDropOrder());
        if (!droppable.isEmpty()) current = without(current, droppable.remove(0));
        while (true) {
            if (sumMinutes(current) <= available) return current;
            int min = config.getMinBlockMinutes();
            if (current.size() * min <= available) return shrink(current, available, min);
            if (droppable.isEmpty()) return shrink(current, Math.max(available, current.size()), min);
            current = without(current, droppable.remove(0));
        }
    }

    private static List<Block> shrink(List<Block> blocks, int target, int min) {
        double total = sumMinutes(blocks);
        int n = blocks.size();
        double[] raw = new double[n];
        int[] floors = new int[n];
        int left = target;
        for (int i = 0; i < n; i++) {
            raw[i] = blocks.get(i).getMinutes() * target / total;
            floors[i] = Math.max(min, floorInt(raw[i]));
            left -= floors[i];
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        // Stable sort, biggest fraction first (same as the original sortedByDescending).
        Arrays.sort(order, (a, b) -> Double.compare(frac(raw[b]), frac(raw[a])));
        for (int i = 0; left > 0; i++, left--) floors[order[i % n]]++;
        while (left < 0) {
            int idx = -1;
            for (int i = 0; i < n; i++) if (floors[i] > min && (idx == -1 || floors[i] > floors[idx])) idx = i;
            if (idx == -1) break;
            floors[idx]--; left++;
        }
        List<Block> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Block b = blocks.get(i);
            out.add(new Block(b.getId(), b.getKind(), floors[i], b.getExercises()));
        }
        return out;
    }

    /** Builds the blocks (with default exercises) for one day of {@code minutes}. */
    public static List<Block> blocksFor(String dayId, int minutes, List<Focus> focus, @Nullable Pillar limiter, GeneratorConfig config) {
        Map<BlockKind, Integer> split = adjustedSplit(minutes, focus, limiter, config);
        List<Block> out = new ArrayList<>();
        int i = 0;
        for (BlockKind kind : PLAYING_KINDS) {
            int m = split.getOrDefault(kind, 0);
            if (m <= 0) continue;
            String bid = dayId + "-b" + i++;
            List<Exercise> ex = new ArrayList<>();
            switch (kind) {
                case LONG_TONES:
                    ex.add(new Exercise(bid + "-e0", "long-tones-basic", ExerciseType.LONG_TONE));
                    if (limiter == Pillar.RANGE) ex.add(new Exercise(bid + "-e1", "range-extension", ExerciseType.RANGE_EXTENSION));
                    break;
                case SCALES:
                    ex.add(new Exercise(bid + "-e0", "scale-runs", ExerciseType.SCALE));
                    if (m >= 6) ex.add(new Exercise(bid + "-e1", "scale-review", ExerciseType.SCALE));
                    break;
                case NEW_MATERIAL: ex.add(new Exercise(bid + "-e0", "new-material", ExerciseType.NEW_MATERIAL)); break;
                case PLAY_FOR_FUN: ex.add(new Exercise(bid + "-e0", "play-for-fun", ExerciseType.PLAY_FOR_FUN)); break;
                default: ex.add(new Exercise(bid + "-e0", "breathing-pacer", ExerciseType.BREATHING_PACER));
            }
            out.add(new Block(bid, kind, m, ex));
        }
        return out;
    }

    /**
     * Schedules phone-drill sessions: the configured number per week (+1 if the limiter is Musicianship,
     * +1 if reading or theory is a focus), capped so the total never exceeds the configured share of the
     * weekly sax minutes. Only {@code available} drill types are used, focus-matching ones first, rotating.
     */
    public static List<PhoneDrillSlot> phoneDrills(int weeklySaxMinutes, List<Focus> focus, @Nullable Pillar limiter,
                                                   List<ExerciseType> available, GeneratorConfig config) {
        List<PhoneDrillSlot> out = new ArrayList<>();
        if (available.isEmpty()) return out;
        PhoneDrillConfig pc = config.getPhoneDrills();
        int wanted = pc.getSessionsPerWeek();
        if (limiter == Pillar.MUSICIANSHIP) wanted++;
        if (focus.contains(Focus.READING) || focus.contains(Focus.THEORY)) wanted++;
        int budget = floorInt(weeklySaxMinutes * pc.getMaxShareOfSaxMinutes());
        List<Integer> lengths = new ArrayList<>();
        while (lengths.size() < wanted && budget >= MIN_PHONE_MINUTES) {
            int m = Math.min(pc.getMinutes(), budget);
            lengths.add(m);
            budget -= m;
        }
        List<ExerciseType> ordered = new ArrayList<>();
        if (focus.contains(Focus.READING) && available.contains(ExerciseType.READING)) ordered.add(ExerciseType.READING);
        if (focus.contains(Focus.THEORY) && available.contains(ExerciseType.THEORY_LESSON)) ordered.add(ExerciseType.THEORY_LESSON);
        for (ExerciseType t : available) if (!ordered.contains(t)) ordered.add(t);
        for (int i = 0; i < lengths.size(); i++) {
            int day = 1 + (i * 7 / lengths.size() + 1) % 7;
            out.add(new PhoneDrillSlot(day, lengths.get(i), ordered.get(i % ordered.size())));
        }
        return out;
    }

    /** Generates a whole program. Same input, same output. */
    public static Program generate(Input input, GeneratorConfig config) {
        List<ProgramDay> days = new ArrayList<>();
        int weekly = 0;
        for (int dow : new TreeSet<>(input.getDays())) {
            String dayId = input.getProgramId() + "-d" + dow;
            ProgramDay d = new ProgramDay(dayId, dow, blocksFor(dayId, input.getMinutesPerSession(), input.getFocus(), input.getLimiter(), config));
            days.add(d);
            weekly += d.getMinutes();
        }
        List<String> notes = new ArrayList<>();
        if (input.getLimiter() == Pillar.CONSISTENCY) {
            int suggested = (int) Math.round(input.getMinutesPerSession() * config.getConsistencySuggestedShare() / 5.0) * 5;
            notes.add(NOTE_CONSISTENCY_PREFIX + Math.max(5, suggested));
        }
        return new Program(input.getProgramId(), input.getName(), ProgramOrigin.GENERATED, days,
            phoneDrills(weekly, input.getFocus(), input.getLimiter(), input.getAvailablePhoneDrills(), config),
            input.getEnduranceCapSec(), input.getRestSec() != null ? input.getRestSec() : config.getRestSec(), notes,
            input.getCreatedAtEpochMs());
    }
}
