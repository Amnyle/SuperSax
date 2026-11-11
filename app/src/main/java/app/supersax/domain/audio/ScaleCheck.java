package app.supersax.domain.audio;

import java.util.List;

/** Result of comparing what was played with the expected scale. */
public final class ScaleCheck {
    private final List<DetectedNote> played;
    private final List<Integer> expected;
    private final int matched, wrong, missing, extra;
    /** Notes per minute from the first onset to the last offset. */
    private final double notesPerMinute;

    public ScaleCheck(List<DetectedNote> played, List<Integer> expected, int matched, int wrong, int missing, int extra, double notesPerMinute) {
        this.played = played; this.expected = expected; this.matched = matched; this.wrong = wrong;
        this.missing = missing; this.extra = extra; this.notesPerMinute = notesPerMinute;
    }

    public List<DetectedNote> getPlayed() { return played; }
    public List<Integer> getExpected() { return expected; }
    public int getMatched() { return matched; }
    public int getWrong() { return wrong; }
    public int getMissing() { return missing; }
    public int getExtra() { return extra; }
    public double getNotesPerMinute() { return notesPerMinute; }

    /** A clean run: every expected note, in order, nothing wrong or extra. */
    public boolean getClean() { return matched == expected.size() && wrong == 0 && extra == 0 && missing == 0; }
}
