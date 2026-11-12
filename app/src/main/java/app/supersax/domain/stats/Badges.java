package app.supersax.domain.stats;

import app.supersax.domain.common.DrillKind;
import app.supersax.domain.common.DrillResultRecord;
import app.supersax.domain.common.RecordingRecord;
import app.supersax.domain.common.SessionRecord;
import app.supersax.domain.common.TunerHoldRecord;
import app.supersax.domain.drills.Levels;
import app.supersax.domain.program.PlayerState;
import app.supersax.domain.program.ScaleProgress;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Achievements computed from the logs: each has a target and the current value. */
public final class Badges {
    private Badges() {}

    public enum Id {
        FIRST_SESSION, SESSIONS_10, SESSIONS_50, SESSIONS_100,
        STREAK_3, STREAK_7, STREAK_30,
        HOURS_5, HOURS_20,
        SCALES_5, MAJOR_12, BPM_100,
        HOLD_20, IN_TUNE_90,
        THREE_STARS, MASTER_DRILL, DAILY_7,
        SPEED_30, PERFECT_PLAY_ALONG, FIRST_TAKE,
    }

    /** One badge: earned when current reaches target. */
    public static final class Badge {
        public final Id id;
        public final int current, target;
        Badge(Id id, int current, int target) { this.id = id; this.current = Math.min(current, target); this.target = target; }
        public Id getId() { return id; }
        public int getCurrent() { return current; }
        public int getTarget() { return target; }
        public boolean getEarned() { return current >= target; }
        public float getProgress() { return target <= 0 ? 1f : (float) current / target; }
    }

    /** Inputs: everything the badges look at. */
    public static List<Badge> compute(List<SessionRecord> sessions, List<DrillResultRecord> drills, List<TunerHoldRecord> holds,
                                      List<RecordingRecord> recordings, PlayerState player, ZoneId zone, LocalDate today, int lessons) {
        List<Badge> out = new ArrayList<>();
        int sax = 0;
        for (SessionRecord s : sessions) if (!s.getPhone() && s.getPlayedSec() >= 60) sax++;
        out.add(new Badge(Id.FIRST_SESSION, sax, 1));
        out.add(new Badge(Id.SESSIONS_10, sax, 10));
        out.add(new Badge(Id.SESSIONS_50, sax, 50));
        out.add(new Badge(Id.SESSIONS_100, sax, 100));
        int bestStreak = Stats.streaks(Stats.saxDays(sessions, zone), today)[1];
        out.add(new Badge(Id.STREAK_3, bestStreak, 3));
        out.add(new Badge(Id.STREAK_7, bestStreak, 7));
        out.add(new Badge(Id.STREAK_30, bestStreak, 30));
        int hours = Stats.totalSaxMinutes(sessions) / 60;
        out.add(new Badge(Id.HOURS_5, hours, 5));
        out.add(new Badge(Id.HOURS_20, hours, 20));
        int scales = player.getScales().size(), majors = 0, best = 0;
        for (ScaleProgress p : player.getScales()) {
            if (p.getScaleKey().startsWith("MAJOR:") && p.getBestCleanBpm() > 0) majors++;
            best = Math.max(best, p.getBestCleanBpm());
        }
        out.add(new Badge(Id.SCALES_5, scales, 5));
        out.add(new Badge(Id.MAJOR_12, majors, 12));
        out.add(new Badge(Id.BPM_100, best, 100));
        int longest = 0, inTune = 0;
        for (TunerHoldRecord h : holds) {
            longest = Math.max(longest, (int) Math.floor(h.getStableSec()));
            if (h.getStableSec() >= 5 && h.getInTuneShare() >= 0.9) inTune = 1;
        }
        out.add(new Badge(Id.HOLD_20, longest, 20));
        out.add(new Badge(Id.IN_TUNE_90, inTune, 1));
        boolean three = false, master = false;
        for (DrillKind k : new DrillKind[]{DrillKind.READING, DrillKind.SCALE_RECOGNITION, DrillKind.FINGERING, DrillKind.EAR, DrillKind.THEORY_QUIZ}) {
            int max = Levels.count(k, lessons);
            boolean all = max > 0;
            for (int l = 1; l <= max; l++) {
                int st = Levels.best(drills, k, l);
                if (st >= 3) three = true; else all = false;
            }
            if (all) master = true;
        }
        out.add(new Badge(Id.THREE_STARS, three ? 1 : 0, 1));
        out.add(new Badge(Id.MASTER_DRILL, master ? 1 : 0, 1));
        java.util.Set<LocalDate> daily = new java.util.HashSet<>();
        int speed = 0;
        boolean perfect = false;
        for (DrillResultRecord d : drills) {
            if (d.getKind() == DrillKind.DAILY) daily.add(Stats.day(d.getAtMs(), zone));
            if (d.getKind() == DrillKind.READING_SPEED) speed = Math.max(speed, d.getCorrect());
            if (d.getKind() == DrillKind.PLAY_ALONG && d.getQuestions() > 0 && d.getCorrect() == d.getQuestions()) perfect = true;
        }
        out.add(new Badge(Id.DAILY_7, Stats.streaks(daily, today)[1], 7));
        out.add(new Badge(Id.SPEED_30, speed, 30));
        out.add(new Badge(Id.PERFECT_PLAY_ALONG, perfect ? 1 : 0, 1));
        out.add(new Badge(Id.FIRST_TAKE, recordings.isEmpty() ? 0 : 1, 1));
        return out;
    }
}
