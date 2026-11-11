package app.supersax.domain.goals;

import app.supersax.domain.common.GoalRecord;
import app.supersax.domain.common.PieceProgressRecord;
import app.supersax.domain.common.PieceStage;
import app.supersax.domain.common.RecordingRecord;
import app.supersax.domain.common.RunRecord;
import app.supersax.domain.common.SessionRecord;
import app.supersax.domain.program.ScaleProgress;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kotlin.Pair;

/** Pure goal progress, computed from logs. */
public final class GoalEvaluator {
    private GoalEvaluator() {}

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static GoalProgress progress(GoalRecord goal, GoalInputs inputs, long nowMs, ZoneId zone) {
        switch (goal.getType()) {
            case TIME: {
                LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
                long start = weekStart(today).atStartOfDay(zone).toInstant().toEpochMilli();
                int sec = 0;
                for (SessionRecord s : inputs.getSessions()) {
                    if (!s.getPhone() && s.getStartedAtMs() >= start && s.getStartedAtMs() <= nowMs) sec += s.getPlayedSec();
                }
                return ratio(sec / 60, orZero(goal.getWeeklyMinutes()));
            }
            case TEMPO: {
                String key = goal.getScaleKey();
                ScaleProgress sp = inputs.getPlayer().scale(key == null ? "" : key);
                int best = sp == null ? 0 : sp.getBestCleanBpm();
                for (SessionRecord s : inputs.getSessions()) {
                    for (Pair<String, Integer> p : cleanPairs(s)) {
                        if (Objects.equals(p.getFirst(), key)) best = Math.max(best, p.getSecond());
                    }
                }
                return ratio(best, orZero(goal.getBpm()));
            }
            case SONG_MILESTONE: {
                PieceStage target = goal.getStage() == null ? PieceStage.CLEAN_AT_TEMPO : PieceStage.valueOf(goal.getStage());
                String section = goal.getSectionId() == null ? "" : goal.getSectionId();
                int stage = 0;
                for (PieceProgressRecord p : inputs.getPieceProgress()) {
                    if (Objects.equals(p.getPieceId(), goal.getPieceId()) && p.getSectionId().equals(section)) {
                        stage = Math.max(stage, p.getStage().ordinal());
                    }
                }
                return ratio(stage, target.ordinal());
            }
            case RECORDING:
            default: {
                Long deadline = goal.getDeadlineEpochDay() == null ? null
                        : LocalDate.ofEpochDay(goal.getDeadlineEpochDay()).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                int n = 0;
                for (RecordingRecord r : inputs.getRecordings()) {
                    if (r.getBenchmark()
                            && (Objects.equals(r.getExerciseKey(), goal.getBenchmarkKey()) || Objects.equals(r.getPieceId(), goal.getBenchmarkKey()))
                            && r.getAtMs() >= goal.getCreatedAtMs() && (deadline == null || r.getAtMs() < deadline)) n++;
                }
                return ratio(Math.min(n, 1), 1);
            }
        }
    }

    private static int orZero(Integer v) { return v == null ? 0 : v; }

    private static GoalProgress ratio(int current, int target) {
        if (target <= 0) return new GoalProgress(0.0, false, current, target);
        double f = Math.max(0.0, Math.min(1.0, (double) current / target));
        return new GoalProgress(f, current >= target, current, target);
    }

    /** (scaleKey, bpm) for each pair of consecutive clean runs of the same scale in a session. */
    public static List<Pair<String, Integer>> cleanPairs(SessionRecord s) {
        Map<String, List<RunRecord>> byKey = new LinkedHashMap<>();
        for (RunRecord r : s.getRuns()) byKey.computeIfAbsent(r.getScaleKey(), k -> new ArrayList<>()).add(r);
        List<Pair<String, Integer>> out = new ArrayList<>();
        for (Map.Entry<String, List<RunRecord>> e : byKey.entrySet()) {
            int streak = 0;
            for (RunRecord r : e.getValue()) {
                streak = r.getClean() ? streak + 1 : 0;
                if (streak >= 2) { out.add(new Pair<>(e.getKey(), r.getBpm())); streak = 0; }
            }
        }
        return out;
    }
}
