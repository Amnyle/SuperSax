package app.supersax.domain.rank;

import app.supersax.domain.audio.HoldReport;
import app.supersax.domain.common.DrillKind;
import app.supersax.domain.common.DrillResultRecord;
import app.supersax.domain.common.LessonProgressRecord;
import app.supersax.domain.common.PieceProgressRecord;
import app.supersax.domain.common.PieceStage;
import app.supersax.domain.common.RankEventKind;
import app.supersax.domain.common.RankEventRecord;
import app.supersax.domain.common.SessionRecord;
import app.supersax.domain.common.TunerHoldRecord;
import app.supersax.domain.pitch.AltoRegisters;
import app.supersax.domain.program.Pillar;
import app.supersax.domain.scale.ScaleId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rank calculation: a pure function of the logs, the config and the time.
 * <ul>
 *   <li>A pillar's tier is the highest tier (1..8) whose requirements are all met; 0 = Unranked.</li>
 *   <li>Musicianship counts at most floor(average of the other five) + 1.</li>
 *   <li>Overall = floor(average of the six); the fractional part gives the division in four equal bands
 *       (IV..I); an average of 8 is Master without division.</li>
 *   <li>The limiter is the lowest pillar, ties broken in the order Sound, Technique, Range, Repertoire,
 *       Consistency, Musicianship.</li>
 *   <li>Only Consistency is rolling; skill pillars use all history, so they never decay.</li>
 * </ul>
 */
public final class RankCalculator {
    private static final double EPS = 1e-9;

    private RankCalculator() {}

    private interface Reqs { List<Requirement> forTier(int tier); }

    public static RankResult computeRank(RankEvents events, RankConfig config, long now) {
        Map<Pillar, Integer> raw = new EnumMap<>(Pillar.class);
        Map<Pillar, List<Requirement>> next = new EnumMap<>(Pillar.class);
        for (Pillar p : Pillar.values()) {
            Reqs reqs = requirementsOf(p, events, config, now);
            int tier = 0;
            for (int t = 1; t <= 8; t++) {
                List<Requirement> r = reqs.forTier(t);
                boolean all = !r.isEmpty();
                for (Requirement q : r) all &= q.getMet();
                if (all) tier = t;
            }
            raw.put(p, tier);
            next.put(p, tier >= 8 ? Collections.emptyList() : reqs.forTier(tier + 1));
        }
        double others = 0;
        for (Pillar p : Pillar.values()) if (p != Pillar.MUSICIANSHIP) others += raw.get(p);
        int cap = (int) Math.floor(others / 5) + 1;
        Map<Pillar, PillarResult> pillars = new EnumMap<>(Pillar.class);
        double sum = 0;
        for (Pillar p : Pillar.values()) {
            int effective = p == Pillar.MUSICIANSHIP ? Math.min(raw.get(p), cap) : raw.get(p);
            pillars.put(p, new PillarResult(p, effective, raw.get(p), next.get(p)));
            sum += effective;
        }
        double avg = sum / Pillar.values().length;
        int tier = Math.max(0, Math.min(8, (int) Math.floor(avg + EPS)));
        int division = tier == 8 || tier == 0 ? -1 : Math.max(0, Math.min(3, (int) Math.floor((avg - tier) * 4 + EPS)));
        Pillar limiter = Pillar.values()[0];
        for (Pillar p : Pillar.values()) if (pillars.get(p).getTier() < pillars.get(limiter).getTier()) limiter = p; // declaration order = tie-break
        return new RankResult(pillars, avg, tier, division, limiter, cap);
    }

    private static Reqs requirementsOf(Pillar p, RankEvents e, RankConfig c, long now) {
        switch (p) {
            case TECHNIQUE: return t -> technique(t, e, c);
            case RANGE: return t -> range(t, e, c);
            case SOUND: return t -> sound(t, e, c);
            case CONSISTENCY: return t -> consistency(t, e, c, now);
            case REPERTOIRE: return t -> repertoire(t, e, c);
            default: return t -> musicianship(t, e, c);
        }
    }

    // --- Technique ---------------------------------------------------------------------------

    /** Best clean-pair BPM per scale key. */
    public static Map<String, Integer> bestCleanBpm(RankEvents e) {
        Map<String, Integer> best = new HashMap<>();
        for (RankEventRecord r : e.getRankEvents()) {
            if (r.getKind() != RankEventKind.CLEAN_PAIR || r.getScaleKey() == null || r.getBpm() == null) continue;
            best.merge(r.getScaleKey(), r.getBpm(), Math::max);
        }
        return best;
    }

    private static List<Requirement> technique(int t, RankEvents e, RankConfig c) {
        List<Requirement> out = new ArrayList<>();
        TechniqueTier tier = null;
        for (TechniqueTier x : c.getTechnique()) if (x.getTier() == t) tier = x;
        if (tier == null) return out;
        Map<String, Integer> best = bestCleanBpm(e);
        for (TechniqueReq r : tier.getRequirements()) {
            int n = 0;
            for (Map.Entry<String, Integer> b : best.entrySet()) {
                ScaleId id;
                try { id = ScaleId.parse(b.getKey()); } catch (RuntimeException ex) { continue; }
                if (ScaleFamily.Companion.of(id.getType()) == r.getFamily() && b.getValue() >= r.getMinBpm()) n++;
            }
            out.add(new Requirement(ReqKind.SCALES_AT_BPM, r.getCount(), n, n >= r.getCount(), r.getFamily(), (double) r.getMinBpm(), null, null));
        }
        return out;
    }

    // --- Range -------------------------------------------------------------------------------

    private static List<Requirement> range(int t, RankEvents e, RankConfig c) {
        List<Requirement> out = new ArrayList<>();
        RangeTier tier = null;
        for (RangeTier x : c.getRange()) if (x.getTier() == t) tier = x;
        if (tier == null) return out;
        Integer lowest = null, highestAny = null, highestVerified = null;
        boolean overtone = false;
        for (RankEventRecord r : e.getRankEvents()) {
            if (r.getKind() == RankEventKind.OVERTONE_MILESTONE && r.getVerified()) overtone = true;
            if (r.getKind() != RankEventKind.RANGE_NOTE || r.getWrittenMidi() == null) continue;
            int w = r.getWrittenMidi();
            lowest = lowest == null ? w : Math.min(lowest, w);
            highestAny = highestAny == null ? w : Math.max(highestAny, w);
            if (r.getVerified()) highestVerified = highestVerified == null ? w : Math.max(highestVerified, w);
        }
        out.add(new Requirement(ReqKind.LOWEST_NOTE, tier.getLowAtMost(), lowest == null ? 0 : lowest, lowest != null && lowest <= tier.getLowAtMost()));
        if (t >= c.getRangeVerifiedFromTier()) {
            out.add(new Requirement(ReqKind.OVERTONE_MILESTONE, 1, overtone ? 1 : 0, overtone));
            out.add(new Requirement(ReqKind.HIGHEST_NOTE_VERIFIED, tier.getHighAtLeast(), highestVerified == null ? 0 : highestVerified,
                highestVerified != null && highestVerified >= tier.getHighAtLeast()));
        } else {
            out.add(new Requirement(ReqKind.HIGHEST_NOTE, tier.getHighAtLeast(), highestAny == null ? 0 : highestAny,
                highestAny != null && highestAny >= tier.getHighAtLeast()));
        }
        return out;
    }

    // --- Sound -------------------------------------------------------------------------------

    private static List<Requirement> sound(int t, RankEvents e, RankConfig c) {
        List<Requirement> out = new ArrayList<>();
        SoundTier s = null;
        for (SoundTier x : c.getSound()) if (x.getTier() == t) s = x;
        if (s == null) return out;
        if (s.getLongToneBlocks() > 0) {
            int n = 0;
            for (RankEventRecord r : e.getRankEvents()) if (r.getKind() == RankEventKind.LONG_TONE_BLOCK) n++;
            out.add(new Requirement(ReqKind.LONG_TONE_BLOCKS, s.getLongToneBlocks(), n, n >= s.getLongToneBlocks()));
            return out;
        }
        // Re-evaluated from the stored traces at this tier's tolerance (thresholds can change later).
        Set<Integer> notes = new HashSet<>();
        for (TunerHoldRecord h : e.getHolds()) {
            if (h.getStableSec() >= s.getSeconds() && HoldReport.inTuneShareAt(h.getTrace(), s.getToleranceCents()) >= s.getInTuneShare()) notes.add(h.getRefMidi());
        }
        out.add(new Requirement(ReqKind.HOLD_NOTES, s.getDistinctNotes(), notes.size(), notes.size() >= s.getDistinctNotes(),
            null, s.getSeconds(), s.getToleranceCents(), s.getInTuneShare()));
        if (s.getAllRegisters()) {
            Set<Integer> regs = new HashSet<>();
            for (int n : notes) {
                AltoRegisters.Register r = AltoRegisters.registerOf(e.getInstrument().concertToWrittenMidi(n));
                regs.add(r == AltoRegisters.Register.LOW ? 0 : r == AltoRegisters.Register.MIDDLE ? 1 : 2);
            }
            out.add(new Requirement(ReqKind.HOLD_REGISTERS, 3, regs.size(), regs.size() >= 3, null, s.getSeconds(), s.getToleranceCents(), null));
        }
        return out;
    }

    // --- Consistency -------------------------------------------------------------------------

    private static LocalDate date(long ms, ZoneId zone) { return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(); }

    /** Adherence (0..1) and sessions per week over the rolling window: {adherence, sessionsPerWeek}. */
    public static double[] adherence(RankEvents e, RankConfig c, long now) {
        ZoneId zone = e.getZone();
        LocalDate today = date(now, zone);
        LocalDate windowStart = today.minusDays(c.getConsistency().getWindowDays() - 1L);
        List<SessionRecord> sax = new ArrayList<>();
        for (SessionRecord s : e.getSessions()) if (!s.getPhone()) sax.add(s);
        if (sax.isEmpty()) return new double[]{0.0, 0.0};
        long firstMs = Long.MAX_VALUE;
        Set<LocalDate> played = new HashSet<>();
        for (SessionRecord s : sax) { firstMs = Math.min(firstMs, s.getStartedAtMs()); played.add(date(s.getStartedAtMs(), zone)); }
        LocalDate first = date(firstMs, zone);
        LocalDate start = first.isAfter(windowStart) ? first : windowStart;
        int planned = 0, done = 0;
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            boolean isPlanned = e.getPlannedDays().contains(d.getDayOfWeek().getValue());
            boolean wasPlayed = played.contains(d);
            // Today counts only once it has been played (the day is not over).
            if (isPlanned && (!d.equals(today) || wasPlayed)) { planned++; if (wasPlayed) done++; }
        }
        long days = ChronoUnit.DAYS.between(start, today) + 1;
        double weeks = Math.max(1.0, days / 7.0);
        int inWindow = 0;
        for (SessionRecord s : sax) { LocalDate x = date(s.getStartedAtMs(), zone); if (!x.isBefore(start) && !x.isAfter(today)) inWindow++; }
        double adherence = planned == 0 ? 0.0 : Math.min(1.0, (double) done / planned);
        return new double[]{adherence, inWindow / weeks};
    }

    private static List<Requirement> consistency(int t, RankEvents e, RankConfig c, long now) {
        List<Requirement> out = new ArrayList<>();
        ConsistencyConfig cc = c.getConsistency();
        if (t - 1 >= cc.getThresholds().size()) return out;
        int threshold = cc.getThresholds().get(t - 1);
        double[] a = adherence(e, c, now);
        double pct = a[0] * 100;
        out.add(new Requirement(ReqKind.ADHERENCE, threshold, pct, pct + EPS >= threshold));
        if (t >= cc.getSessionsFloorFromTier()) {
            out.add(new Requirement(ReqKind.SESSIONS_PER_WEEK, cc.getMinSessionsPerWeek(), a[1], a[1] + EPS >= cc.getMinSessionsPerWeek()));
        }
        return out;
    }

    // --- Repertoire --------------------------------------------------------------------------

    public static int hardestCleanPiece(RankEvents e) {
        int hardest = 0;
        for (PieceProgressRecord p : e.getPieceProgress()) {
            if (p.getStage() == PieceStage.CLEAN_AT_TEMPO && p.getSectionId().isEmpty()) {
                hardest = Math.max(hardest, e.getPieceDifficulty().getOrDefault(p.getPieceId(), 0));
            }
        }
        return hardest;
    }

    private static List<Requirement> repertoire(int t, RankEvents e, RankConfig c) {
        List<Requirement> out = new ArrayList<>();
        for (RepertoireTier r : c.getRepertoire()) {
            if (r.getTier() != t) continue;
            int hardest = hardestCleanPiece(e);
            out.add(new Requirement(ReqKind.PIECE_DIFFICULTY, r.getMinDifficulty(), hardest, hardest >= r.getMinDifficulty()));
        }
        return out;
    }

    // --- Musicianship ------------------------------------------------------------------------

    private static List<Requirement> musicianship(int t, RankEvents e, RankConfig c) {
        List<Requirement> out = new ArrayList<>();
        MusicianshipTier m = null;
        for (MusicianshipTier x : c.getMusicianship()) if (x.getTier() == t) m = x;
        if (m == null) return out;
        if (m.getAnyActivity()) {
            int n = e.getDrills().size() + e.getLessons().size();
            out.add(new Requirement(ReqKind.ANY_ACTIVITY, 1, n, n >= 1));
        }
        if (m.getReadingLevel() > 0) {
            double best = 0;
            for (DrillResultRecord d : e.getDrills()) {
                if (d.getKind() == DrillKind.READING && d.getLevel() >= m.getReadingLevel() && d.getQuestions() > 0 && d.getMeanResponseMs() <= m.getReadingMeanMs()) {
                    best = Math.max(best, (double) d.getCorrect() / d.getQuestions());
                }
            }
            Double meanMs = m.getReadingMeanMs() == Long.MAX_VALUE ? null : (double) m.getReadingMeanMs();
            out.add(new Requirement(ReqKind.READING_ROUND, m.getReadingLevel(), best * 100, best + EPS >= m.getReadingAccuracy(),
                null, m.getReadingAccuracy() * 100, meanMs, null));
        }
        if (m.getScaleLevel() > 0 || m.getScaleAccuracy() > 0) {
            double best = 0;
            for (DrillResultRecord d : e.getDrills()) {
                if (d.getKind() == DrillKind.SCALE_RECOGNITION && d.getLevel() >= m.getScaleLevel() && d.getQuestions() > 0) {
                    best = Math.max(best, (double) d.getCorrect() / d.getQuestions());
                }
            }
            out.add(new Requirement(ReqKind.SCALE_ROUND, m.getScaleLevel(), best * 100, best + EPS >= m.getScaleAccuracy(),
                null, m.getScaleAccuracy() * 100, null, null));
        }
        if (m.getLessons() > 0) {
            int n = 0;
            for (LessonProgressRecord l : e.getLessons()) if (l.getQuizScore() + EPS >= m.getLessonMinScore()) n++;
            out.add(new Requirement(ReqKind.LESSONS, m.getLessons(), n, n >= m.getLessons(), null, m.getLessonMinScore() * 100, null, null));
        }
        return out;
    }
}
