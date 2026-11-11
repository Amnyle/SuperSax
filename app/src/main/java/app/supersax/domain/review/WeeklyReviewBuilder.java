package app.supersax.domain.review;

import app.supersax.domain.common.BlockLogRecord;
import app.supersax.domain.common.RankSnapshotRecord;
import app.supersax.domain.common.RunRecord;
import app.supersax.domain.common.SessionRecord;
import app.supersax.domain.common.TunerHoldRecord;
import app.supersax.domain.goals.GoalEvaluator;
import app.supersax.domain.pitch.NoteNaming;
import app.supersax.domain.program.Block;
import app.supersax.domain.program.BlockKind;
import app.supersax.domain.program.Program;
import app.supersax.domain.program.ProgramDay;
import app.supersax.domain.rank.RankNames;
import app.supersax.domain.scale.ScaleNames;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kotlin.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the weekly review from the logs (pure), and its plain, direct text in English or French.
 * Planned = what the active program planned for each day of the week; days after today are not
 * counted as skipped yet.
 */
public final class WeeklyReviewBuilder {
    private WeeklyReviewBuilder() {}

    public static WeeklyReview build(
            LocalDate weekStart, LocalDate today, ZoneId zone, @Nullable Program program,
            List<SessionRecord> sessions, List<TunerHoldRecord> holds, List<RankSnapshotRecord> snapshots) {
        long startMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
        List<SessionRecord> week = new ArrayList<>();
        for (SessionRecord s : sessions) if (s.getStartedAtMs() >= startMs && s.getStartedAtMs() < endMs) week.add(s);
        List<SessionRecord> sax = new ArrayList<>();
        int phoneSec = 0;
        for (SessionRecord s : week) {
            if (s.getPhone()) phoneSec += s.getPlayedSec(); else sax.add(s);
        }
        Set<Integer> plannedDays = new LinkedHashSet<>();
        if (program != null) for (ProgramDay d : program.getDays()) plannedDays.add(d.getDayOfWeek());
        LocalDate lastDay = weekStart.plusDays(6);
        LocalDate lastCounted = today.isBefore(lastDay) ? today : lastDay;
        List<Integer> plannedSoFar = new ArrayList<>();
        for (int day : plannedDays) {
            if (day >= 1 && day <= 7 && !weekStart.plusDays(day - 1L).isAfter(lastCounted)) plannedSoFar.add(day);
        }
        Set<Integer> daysPlayed = new LinkedHashSet<>();
        int extra = 0, playedSec = 0, tired = 0;
        for (SessionRecord s : sax) {
            int dow = dayOf(s.getStartedAtMs(), zone).getDayOfWeek().getValue();
            daysPlayed.add(dow);
            if (!plannedDays.contains(dow)) extra++;
            playedSec += s.getPlayedSec();
            for (BlockLogRecord b : s.getBlocks()) if (b.getLipsTired()) tired++;
        }
        int done = 0;
        List<Integer> skipped = new ArrayList<>();
        for (int d : plannedSoFar) {
            if (daysPlayed.contains(d)) done++; else skipped.add(d);
        }
        Collections.sort(skipped);

        Map<BlockKind, Integer> plannedPerBlock = new EnumMap<>(BlockKind.class);
        Map<BlockKind, Integer> actualSecPerBlock = new EnumMap<>(BlockKind.class);
        for (BlockKind k : BlockKind.values()) { plannedPerBlock.put(k, 0); actualSecPerBlock.put(k, 0); }
        if (program != null) {
            for (ProgramDay d : program.getDays()) {
                if (!plannedSoFar.contains(d.getDayOfWeek())) continue;
                for (Block b : d.getBlocks()) plannedPerBlock.merge(b.getKind(), b.getMinutes(), Integer::sum);
            }
        }
        for (SessionRecord s : sax) {
            for (BlockLogRecord b : s.getBlocks()) {
                if (b.getBlockKind() != null) actualSecPerBlock.merge(b.getBlockKind(), b.getActualSec(), Integer::sum);
            }
        }
        List<BlockStat> perBlock = new ArrayList<>();
        int plannedMinutes = 0;
        for (BlockKind k : BlockKind.values()) {
            int planned = plannedPerBlock.get(k), actual = actualSecPerBlock.get(k) / 60;
            plannedMinutes += planned;
            if (planned > 0 || actual > 0) perBlock.add(new BlockStat(k, planned, actual));
        }

        Map<String, Integer> before = new HashMap<>();
        for (SessionRecord s : sessions) {
            if (s.getStartedAtMs() < startMs) for (Pair<String, Integer> p : GoalEvaluator.cleanPairs(s)) before.merge(p.getFirst(), p.getSecond(), Math::max);
        }
        Map<String, Integer> during = new HashMap<>();
        TreeSet<String> practised = new TreeSet<>();
        for (SessionRecord s : week) {
            for (RunRecord r : s.getRuns()) practised.add(r.getScaleKey());
            for (Pair<String, Integer> p : GoalEvaluator.cleanPairs(s)) during.merge(p.getFirst(), p.getSecond(), Math::max);
        }
        practised.addAll(during.keySet());
        List<ScaleTempo> scales = new ArrayList<>();
        for (String k : practised) scales.add(new ScaleTempo(k, before.get(k), during.get(k)));

        int holdCount = 0;
        double inTune = 0, drift = 0;
        for (TunerHoldRecord h : holds) {
            if (h.getAtMs() >= startMs && h.getAtMs() < endMs) { holdCount++; inTune += h.getInTuneShare(); drift += h.getDriftCents(); }
        }
        RankSnapshotRecord rankBefore = null, rankAfter = null;
        for (RankSnapshotRecord r : snapshots) {
            if (r.getAtMs() < startMs && (rankBefore == null || r.getAtMs() > rankBefore.getAtMs())) rankBefore = r;
            if (r.getAtMs() < endMs && (rankAfter == null || r.getAtMs() > rankAfter.getAtMs())) rankAfter = r;
        }
        return new WeeklyReview(
                weekStart, plannedSoFar.size(), done, extra, plannedMinutes, playedSec / 60, perBlock, skipped, tired, scales,
                holdCount, holdCount == 0 ? null : inTune / holdCount, holdCount == 0 ? null : drift / holdCount,
                rankBefore, rankAfter, phoneSec / 60);
    }

    private static LocalDate dayOf(long ms, ZoneId zone) {
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate();
    }

    private static String blockName(BlockKind k, boolean fr) {
        switch (k) {
            case BREATHING: return fr ? "Respiration" : "Breathing";
            case LONG_TONES: return fr ? "Notes tenues" : "Long tones";
            case SCALES: return fr ? "Gammes" : "Scales";
            case NEW_MATERIAL: return fr ? "Nouveau morceau" : "New material";
            case PLAY_FOR_FUN:
            default: return fr ? "Plaisir" : "Play for fun";
        }
    }

    private static String dayName(int d, boolean fr) {
        return DayOfWeek.of(d).getDisplayName(TextStyle.FULL, fr ? Locale.FRENCH : Locale.ENGLISH);
    }

    /** Kotlin roundToInt semantics (half up). */
    private static int round(double v) { return (int) Math.round(v); }

    private static String signed(double v) {
        int c = round(v);
        return c > 0 ? "+" + c : String.valueOf(c);
    }

    /** Plain text, one fact per line. Meant to be pasted to an assistant or sent to a teacher. */
    public static String text(WeeklyReview r, String language, NoteNaming naming) {
        boolean fr = language.startsWith("fr");
        Locale locale = fr ? Locale.FRENCH : Locale.ENGLISH;
        String date = r.getWeekStart().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale));
        List<String> lines = new ArrayList<>();
        lines.add(fr ? "Super Sax – semaine du " + date : "Super Sax – week of " + date);
        String extra = r.getExtraSessions() > 0
                ? (fr ? ", plus " + r.getExtraSessions() + " hors plan." : ", plus " + r.getExtraSessions() + " unplanned.") : ".";
        lines.add(fr ? "Séances : " + r.getDoneSessions() + " sur " + r.getPlannedSessions() + " prévues" + extra
                : "Sessions: " + r.getDoneSessions() + " of " + r.getPlannedSessions() + " planned" + extra);
        lines.add(fr ? "Minutes de saxophone : " + r.getActualMinutes() + " sur " + r.getPlannedMinutes() + " prévues."
                : "Sax minutes: " + r.getActualMinutes() + " of " + r.getPlannedMinutes() + " planned.");
        if (!r.getSkippedDays().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (int d : r.getSkippedDays()) names.add(dayName(d, fr));
            String days = String.join(", ", names);
            lines.add(fr ? "Jours prévus sans séance : " + days + "." : "Planned days with no session: " + days + ".");
        }
        for (BlockStat b : r.getPerBlock()) {
            lines.add(fr ? blockName(b.getKind(), true) + " : " + b.getActualMin() + " sur " + b.getPlannedMin() + " min."
                    : blockName(b.getKind(), false) + ": " + b.getActualMin() + " of " + b.getPlannedMin() + " min.");
        }
        if (r.getTiredBlocks() > 0) {
            lines.add(fr ? "Blocs arrêtés pour lèvres fatiguées : " + r.getTiredBlocks() + "." : "Blocks ended with tired lips: " + r.getTiredBlocks() + ".");
        }
        for (ScaleTempo s : r.getScales()) {
            String name = ScaleNames.written(s.getScaleKey(), naming, language);
            Integer during = s.getDuring(), before = s.getBefore();
            if (during == null) lines.add(fr ? name + " : aucune paire propre cette semaine." : name + ": no clean pair this week.");
            else if (before == null) lines.add(fr ? name + " : première paire propre à " + during + " BPM." : name + ": first clean pair at " + during + " BPM.");
            else if (during > before) lines.add(fr ? name + " : " + before + " → " + during + " BPM." : name + ": " + before + " → " + during + " BPM.");
            else lines.add(fr ? name + " : " + during + " BPM, pas plus rapide qu'avant (" + before + ")."
                    : name + ": " + during + " BPM, not faster than before (" + before + ").");
        }
        if (r.getHoldCount() == 0) lines.add(fr ? "Accordeur : aucune tenue mesurée." : "Tuner: no holds measured.");
        else {
            int pct = round((r.getAvgInTune() == null ? 0.0 : r.getAvgInTune()) * 100);
            int n = r.getHoldCount();
            String drift = signed(r.getAvgDrift() == null ? 0.0 : r.getAvgDrift());
            lines.add(fr ? "Accordeur : " + n + " " + (n == 1 ? "tenue" : "tenues") + ", juste " + pct + " % du temps en moyenne, dérive typique " + drift + "¢."
                    : "Tuner: " + n + " " + (n == 1 ? "hold" : "holds") + ", in tune " + pct + "% of the time on average, typical drift " + drift + "¢.");
        }
        RankSnapshotRecord after = r.getRankAfter();
        if (after != null) {
            String now = RankNames.full(after.getOverallTier(), after.getDivision(), language);
            RankSnapshotRecord before = r.getRankBefore();
            if (before == null) lines.add(fr ? "Rang : " + now + "." : "Rank: " + now + ".");
            else if (before.getOverallTier() == after.getOverallTier() && before.getDivision() == after.getDivision()) {
                lines.add(fr ? "Rang : inchangé (" + now + ")." : "Rank: unchanged (" + now + ").");
            } else {
                lines.add((fr ? "Rang" : "Rank") + ": " + RankNames.full(before.getOverallTier(), before.getDivision(), language) + " → " + now + ".");
            }
        }
        if (r.getPhoneMinutes() > 0) lines.add(fr ? "Exercices sur téléphone : " + r.getPhoneMinutes() + " min." : "Phone drills: " + r.getPhoneMinutes() + " min.");
        return String.join("\n", lines);
    }
}
