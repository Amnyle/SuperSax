package app.supersax.domain.audio;

import app.supersax.domain.pitch.Tuning;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Experimental scale checker. Segments a pitch stream into notes with hysteresis (a new note starts only
 * when the pitch stays more than {@code changeCents} away from the current note for {@code confirmSec}),
 * drops notes shorter than {@code minNoteSec}, then aligns the result with the expected sequence by edit
 * distance. It only reports: the player confirms before anything is logged.
 */
public final class ScaleChecker {
    private final double changeCents, confirmSec, minNoteSec, a4, minConfidence;

    public ScaleChecker(double changeCents, double confirmSec, double minNoteSec, double a4, double minConfidence) {
        this.changeCents = changeCents; this.confirmSec = confirmSec; this.minNoteSec = minNoteSec;
        this.a4 = a4; this.minConfidence = minConfidence;
    }

    public ScaleChecker(double a4) { this(60.0, 0.045, 0.07, a4, 0.8); }

    public ScaleChecker() { this(Tuning.DEFAULT_A4); }

    public List<DetectedNote> segment(List<PitchFrame> frames) {
        List<DetectedNote> notes = new ArrayList<>();
        Double curMidi = null;
        double curStart = 0, lastTime = 0, candidateStart = 0;
        Double candidate = null;
        List<Double> values = new ArrayList<>();
        for (PitchFrame f : frames) {
            Double hz = f.getConfidence() >= minConfidence ? f.getFrequencyHz() : null;
            if (hz == null) {
                if (curMidi != null && f.getTimeSec() - lastTime > confirmSec) {
                    close(notes, values, curStart, lastTime);
                    curMidi = null;
                }
                candidate = null;
                continue;
            }
            double m = Tuning.frequencyToMidi(hz, a4);
            if (curMidi == null) {
                curMidi = m; curStart = f.getTimeSec(); values.add(m);
            } else if (Math.abs(m - curMidi) * 100 > changeCents) {
                if (candidate == null || Math.abs(m - candidate) * 100 > changeCents) { candidate = m; candidateStart = f.getTimeSec(); }
                if (f.getTimeSec() - candidateStart >= confirmSec) {
                    close(notes, values, curStart, candidateStart);
                    curMidi = m; curStart = candidateStart; values.add(m);
                    candidate = null;
                }
            } else {
                candidate = null;
                values.add(m);
                curMidi = median(values); // follow slow drift
            }
            lastTime = f.getTimeSec();
        }
        if (curMidi != null) close(notes, values, curStart, lastTime);
        return notes;
    }

    /** Ends the current note at {@code end} (kept if long enough) and clears its values. */
    private void close(List<DetectedNote> notes, List<Double> values, double start, double end) {
        if (end - start >= minNoteSec && !values.isEmpty()) notes.add(new DetectedNote((int) Math.round(median(values)), start, end));
        values.clear();
    }

    private static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(s.size() / 2);
    }

    public ScaleCheck check(List<PitchFrame> frames, List<Integer> expected) {
        List<DetectedNote> played = segment(frames);
        int n = expected.size(), m = played.size();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) for (int j = 1; j <= m; j++) {
            int sub = expected.get(i - 1) == played.get(j - 1).getMidi() ? 0 : 1;
            d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + sub);
        }
        int i = n, j = m, matched = 0, wrong = 0, missing = 0, extra = 0;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                boolean same = expected.get(i - 1) == played.get(j - 1).getMidi();
                if (d[i][j] == d[i - 1][j - 1] + (same ? 0 : 1)) {
                    if (same) matched++; else wrong++;
                    i--; j--;
                    continue;
                }
            }
            if (i > 0 && d[i][j] == d[i - 1][j] + 1) { missing++; i--; } else { extra++; j--; }
        }
        double span = m >= 2 ? played.get(m - 1).getEndSec() - played.get(0).getStartSec() : 0.0;
        double npm = span > 0 ? m / span * 60 : 0.0;
        return new ScaleCheck(played, expected, matched, wrong, missing, extra, npm);
    }
}
