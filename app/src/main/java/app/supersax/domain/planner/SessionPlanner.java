package app.supersax.domain.planner;

import app.supersax.domain.generator.Generator;
import app.supersax.domain.generator.GeneratorConfig;
import app.supersax.domain.program.Block;
import app.supersax.domain.program.Exercise;
import app.supersax.domain.program.ExerciseType;
import app.supersax.domain.program.PlayerState;
import app.supersax.domain.program.ProgramDay;
import app.supersax.domain.program.ScaleProgress;
import app.supersax.domain.program.Target;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a program template into a concrete session using the player's current state: scale rotation
 * (least recently practiced unlocked scales first), tempo per scale, long-tone hold target, library
 * pieces, the embouchure endurance cap (chunks + rests) and rests between blocks.
 */
public final class SessionPlanner {
    private SessionPlanner() {}

    public static PlannedSession plan(@Nullable String programId, ProgramDay day, PlayerState state, GeneratorConfig config,
                                      int enduranceCapSec, int restSec, PieceChoice pieces, @Nullable Integer availableMinutes) {
        List<Block> blocks = availableMinutes != null ? Generator.fitToMinutes(day.getBlocks(), availableMinutes, config) : day.getBlocks();
        List<String> scaleQueue = scaleRotation(state, config);
        List<Block> ordered = new ArrayList<>(blocks);
        ordered.sort(Comparator.comparingInt(b -> b.getKind().ordinal()));
        List<List<PlannedStep>> raw = new ArrayList<>();
        for (Block b : ordered) if (b.getMinutes() > 0) raw.add(blockSteps(b, state, scaleQueue, pieces, config, enduranceCapSec));
        List<PlannedStep> steps = new ArrayList<>();
        for (int bi = 0; bi < raw.size(); bi++) {
            List<PlannedStep> blockSteps = raw.get(bi);
            PlannedStep prev = steps.isEmpty() ? null : steps.get(steps.size() - 1);
            boolean prevPlays = prev != null && prev.getKind() == StepKind.PLAY && prev.getExerciseType() != null && prev.getExerciseType().isPlaying();
            boolean firstPlays = !blockSteps.isEmpty() && blockSteps.get(0).getExerciseType() != null && blockSteps.get(0).getExerciseType().isPlaying();
            if (bi > 0 && prevPlays && firstPlays && restSec > 0) steps.add(rest(restSec));
            steps.addAll(blockSteps);
        }
        List<PlannedStep> indexed = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) indexed.add(withIndex(steps.get(i), i));
        return new PlannedSession(programId, day.getId(), indexed);
    }

    /** Defaults: the player's endurance cap, the configured rest, no pieces, the whole day. */
    public static PlannedSession plan(@Nullable String programId, ProgramDay day, PlayerState state, GeneratorConfig config) {
        return plan(programId, day, state, config, state.getEnduranceCapSec(), config.getRestSec(), new PieceChoice(), null);
    }

    private static PlannedStep rest(int sec) { return new PlannedStep(0, StepKind.REST, sec); }

    private static PlannedStep withIndex(PlannedStep s, int i) {
        return new PlannedStep(i, s.getKind(), s.getDurationSec(), s.getBlockId(), s.getBlockKind(), s.getExerciseId(), s.getExerciseType(),
            s.getCatalogId(), s.getTarget(), s.getChunk(), s.getChunks(), s.getEnforced());
    }

    /** Unlocked scales, least recently practiced first, then by key for determinism. */
    public static List<String> scaleRotation(PlayerState state, GeneratorConfig config) {
        List<ScaleProgress> sorted = new ArrayList<>(state.getScales());
        sorted.sort(Comparator.comparingLong(ScaleProgress::getLastPracticedEpochMs).thenComparing(ScaleProgress::getScaleKey));
        List<String> out = new ArrayList<>();
        for (ScaleProgress p : sorted) out.add(p.getScaleKey());
        if (out.isEmpty() && !config.getAdaptation().getUnlockOrder().isEmpty()) out.add(config.getAdaptation().getUnlockOrder().get(0));
        return out;
    }

    private static List<PlannedStep> blockSteps(Block block, PlayerState state, List<String> scaleQueue, PieceChoice pieces,
                                                GeneratorConfig config, int capSec) {
        List<PlannedStep> steps = new ArrayList<>();
        List<Exercise> ex = block.getExercises();
        if (ex.isEmpty()) return steps;
        int totalSec = block.getMinutes() * 60;
        int per = totalSec / ex.size();
        int extra = totalSec - per * ex.size();
        int continuous = 0;
        for (int i = 0; i < ex.size(); i++) {
            Exercise e = ex.get(i);
            int dur = per + (i == 0 ? extra : 0);
            ResolvedTarget target = resolve(e, state, scaleQueue, pieces, config);
            boolean playing = e.getType().isPlaying();
            int chunks = playing && dur > capSec ? (int) Math.ceil((double) dur / capSec) : 1;
            int chunkLen = dur / chunks;
            for (int c = 1; c <= chunks; c++) {
                int d = c == chunks ? dur - chunkLen * (chunks - 1) : chunkLen;
                if (playing && continuous > 0 && continuous + d > capSec) {
                    steps.add(rest(config.getRestSec()));
                    continuous = 0;
                }
                steps.add(new PlannedStep(0, StepKind.PLAY, d, block.getId(), block.getKind(), e.getId(), e.getType(), e.getCatalogId(),
                    target, c, chunks, false));
                continuous = playing ? continuous + d : 0;
            }
        }
        return steps;
    }

    /** Fills unset target fields from the player state. Pinned values are kept as they are. */
    public static ResolvedTarget resolve(Exercise e, PlayerState state, List<String> scaleQueue, PieceChoice pieces, GeneratorConfig config) {
        Target t = e.getTarget();
        boolean pinned = t.getPinned();
        switch (e.getType()) {
            case LONG_TONE:
            case RANGE_EXTENSION: {
                Integer hold = pinned && t.getHoldSec() != null ? t.getHoldSec() : Integer.valueOf(state.getHoldTargetSec());
                return new ResolvedTarget(null, hold, null, null, null, t.getTargetMidi(), t.getLowMidi(), t.getHighMidi(), pinned);
            }
            case SCALE: {
                String key = t.getScaleKey();
                if (key == null && !scaleQueue.isEmpty()) {
                    key = scaleQueue.remove(0);
                    scaleQueue.add(key);
                }
                ScaleProgress sp = key != null ? state.scale(key) : null;
                int stateBpm = sp != null ? sp.getBpm() : config.getAdaptation().getMinBpm();
                Integer bpm = pinned && t.getBpm() != null ? t.getBpm() : Integer.valueOf(stateBpm);
                return new ResolvedTarget(bpm, null, key, null, null, null, t.getLowMidi(), t.getHighMidi(), pinned);
            }
            case NEW_MATERIAL:
                return new ResolvedTarget(t.getBpm(), null, null, t.getPieceId() != null ? t.getPieceId() : pieces.getNewPieceId(),
                    t.getSectionId() != null ? t.getSectionId() : pieces.getNewSectionId(), null, null, null, pinned);
            case PLAY_FOR_FUN:
                return new ResolvedTarget(null, null, null, t.getPieceId() != null ? t.getPieceId() : pieces.getFunPieceId(),
                    null, null, null, null, pinned);
            default:
                return new ResolvedTarget(t.getBpm(), null, null, null, null, null, null, null, pinned);
        }
    }
}
