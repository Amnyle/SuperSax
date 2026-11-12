package app.supersax.domain.stats;

import app.supersax.domain.common.RunRecord;
import app.supersax.domain.common.SessionRecord;
import app.supersax.domain.common.TunerHoldRecord;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Practice statistics computed from the logs (pure). */
public final class Stats {
    private Stats() {}

    static LocalDate day(long ms, ZoneId zone) { return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(); }

    static LocalDate monday(LocalDate d) { return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

    /** Minutes played per day: [0] on the saxophone, [1] phone drills. */
    public static Map<LocalDate, int[]> dailyMinutes(List<SessionRecord> sessions, ZoneId zone) {
        Map<LocalDate, int[]> out = new HashMap<>();
        for (SessionRecord s : sessions) {
            int[] m = out.computeIfAbsent(day(s.getStartedAtMs(), zone), k -> new int[2]);
            m[s.getPhone() ? 1 : 0] += Math.round(s.getPlayedSec() / 60f);
        }
        return out;
    }

    /** One cell of the heatmap: a day and its total minutes. */
    public static final class Day {
        public final LocalDate date;
        public final int minutes;
        Day(LocalDate date, int minutes) { this.date = date; this.minutes = minutes; }
        public LocalDate getDate() { return date; }
        public int getMinutes() { return minutes; }
    }

    /** The last [weeks] weeks, Monday first, as columns of 7 days (days after today are null-like with −1 minutes). */
    public static List<List<Day>> heatmap(Map<LocalDate, int[]> daily, LocalDate today, int weeks) {
        LocalDate start = monday(today).minusWeeks(weeks - 1L);
        List<List<Day>> out = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            List<Day> col = new ArrayList<>();
            for (int d = 0; d < 7; d++) {
                LocalDate date = start.plusDays(w * 7L + d);
                int[] m = daily.get(date);
                col.add(new Day(date, date.isAfter(today) ? -1 : m == null ? 0 : m[0] + m[1]));
            }
            out.add(col);
        }
        return out;
    }

    /** Minutes per week for the last [weeks] weeks (oldest first): [0] sax, [1] phone. */
    public static List<int[]> weeklyMinutes(Map<LocalDate, int[]> daily, LocalDate today, int weeks) {
        LocalDate start = monday(today).minusWeeks(weeks - 1L);
        List<int[]> out = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            int[] sum = new int[2];
            for (int d = 0; d < 7; d++) {
                int[] m = daily.get(start.plusDays(w * 7L + d));
                if (m != null) { sum[0] += m[0]; sum[1] += m[1]; }
            }
            out.add(sum);
        }
        return out;
    }

    /** Current streak (ending today, or yesterday if today has nothing yet) and best streak, in days. */
    public static int[] streaks(Set<LocalDate> days, LocalDate today) {
        LocalDate d = days.contains(today) ? today : today.minusDays(1);
        int current = 0;
        while (days.contains(d)) { current++; d = d.minusDays(1); }
        int best = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate x : new java.util.TreeSet<>(days)) {
            run = prev != null && prev.plusDays(1).equals(x) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = x;
        }
        return new int[]{current, Math.max(best, current)};
    }

    /** Days with saxophone practice. */
    public static Set<LocalDate> saxDays(List<SessionRecord> sessions, ZoneId zone) {
        Set<LocalDate> out = new java.util.HashSet<>();
        for (SessionRecord s : sessions) if (!s.getPhone() && s.getPlayedSec() >= 60) out.add(day(s.getStartedAtMs(), zone));
        return out;
    }

    /** Best clean tempo per scale and per day: scale key → (day → BPM), days in order. */
    public static Map<String, TreeMap<LocalDate, Integer>> cleanTempo(List<SessionRecord> sessions, ZoneId zone) {
        Map<String, TreeMap<LocalDate, Integer>> out = new TreeMap<>();
        for (SessionRecord s : sessions) {
            LocalDate d = day(s.getStartedAtMs(), zone);
            for (RunRecord r : s.getRuns()) {
                if (!r.getClean()) continue;
                out.computeIfAbsent(r.getScaleKey(), k -> new TreeMap<>()).merge(d, r.getBpm(), Math::max);
            }
        }
        return out;
    }

    /** Average share of time in tune of long-tone holds per week, last [weeks] weeks (null when no hold). */
    public static List<Double> weeklyInTune(List<TunerHoldRecord> holds, ZoneId zone, LocalDate today, int weeks) {
        LocalDate start = monday(today).minusWeeks(weeks - 1L);
        double[] sum = new double[weeks];
        int[] n = new int[weeks];
        for (TunerHoldRecord h : holds) {
            long w = java.time.temporal.ChronoUnit.WEEKS.between(start, monday(day(h.getAtMs(), zone)));
            if (w < 0 || w >= weeks) continue;
            sum[(int) w] += h.getInTuneShare();
            n[(int) w]++;
        }
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < weeks; i++) out.add(n[i] == 0 ? null : sum[i] / n[i]);
        return out;
    }

    /** Total minutes on the saxophone. */
    public static int totalSaxMinutes(List<SessionRecord> sessions) {
        int sec = 0;
        for (SessionRecord s : sessions) if (!s.getPhone()) sec += s.getPlayedSec();
        return sec / 60;
    }
}
