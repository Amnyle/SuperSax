package app.supersax.domain.audio;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** A note of a melody to play back (concert MIDI, or null for a rest), with its length in beats. */
public final class MelodyNote {
    private final @Nullable Integer midi;
    private final double beats;

    public MelodyNote(@Nullable Integer midi, double beats) {
        this.midi = midi;
        this.beats = beats;
    }

    public @Nullable Integer getMidi() { return midi; }

    public double getBeats() { return beats; }

    /** The same note with another length (used to merge tied notes). */
    public MelodyNote withBeats(double b) { return new MelodyNote(midi, b); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof MelodyNote)) return false;
        MelodyNote n = (MelodyNote) o;
        return Objects.equals(midi, n.midi) && beats == n.beats;
    }

    @Override public int hashCode() { return Objects.hash(midi, beats); }
}
