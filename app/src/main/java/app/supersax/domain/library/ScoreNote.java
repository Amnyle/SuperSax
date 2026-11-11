package app.supersax.domain.library;

import app.supersax.domain.pitch.SpelledPitch;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** One note or rest (pitch null) of a single-voice score; {@code tie} ties it to the next note. */
public final class ScoreNote {
    private final @Nullable SpelledPitch pitch;
    private final Fraction duration;
    private final boolean tie;

    public ScoreNote(@Nullable SpelledPitch pitch, Fraction duration, boolean tie) {
        this.pitch = pitch;
        this.duration = duration;
        this.tie = tie;
    }

    public ScoreNote(@Nullable SpelledPitch pitch, Fraction duration) { this(pitch, duration, false); }

    public @Nullable SpelledPitch getPitch() { return pitch; }
    public Fraction getDuration() { return duration; }
    public boolean getTie() { return tie; }
    public boolean isRest() { return pitch == null; }

    public ScoreNote withPitch(@Nullable SpelledPitch p) { return new ScoreNote(p, duration, tie); }

    public ScoreNote withDurationAndTie(Fraction d, boolean t) { return new ScoreNote(pitch, d, t); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof ScoreNote)) return false;
        ScoreNote n = (ScoreNote) o;
        return Objects.equals(pitch, n.pitch) && duration.equals(n.duration) && tie == n.tie;
    }

    @Override public int hashCode() { return Objects.hash(pitch, duration, tie); }
}
