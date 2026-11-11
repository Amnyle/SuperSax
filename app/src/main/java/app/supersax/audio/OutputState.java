package app.supersax.audio;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** What the output engine is doing (for the UI). Immutable; change it with the with... methods. */
public final class OutputState {
    private final boolean metronomeOn;
    private final int bpm;
    private final int beatsPerBar;
    private final int subdivision;
    private final boolean accent;
    private final @Nullable Integer droneMidi;
    private final boolean melodyPlaying;
    private final int melodyIndex;
    private final @Nullable String error;

    public OutputState(boolean metronomeOn, int bpm, int beatsPerBar, int subdivision, boolean accent,
                       @Nullable Integer droneMidi, boolean melodyPlaying, int melodyIndex, @Nullable String error) {
        this.metronomeOn = metronomeOn;
        this.bpm = bpm;
        this.beatsPerBar = beatsPerBar;
        this.subdivision = subdivision;
        this.accent = accent;
        this.droneMidi = droneMidi;
        this.melodyPlaying = melodyPlaying;
        this.melodyIndex = melodyIndex;
        this.error = error;
    }

    public OutputState() { this(false, 80, 4, 1, true, null, false, -1, null); }

    public boolean getMetronomeOn() { return metronomeOn; }
    public int getBpm() { return bpm; }
    public int getBeatsPerBar() { return beatsPerBar; }
    public int getSubdivision() { return subdivision; }
    public boolean getAccent() { return accent; }
    public @Nullable Integer getDroneMidi() { return droneMidi; }
    public boolean getMelodyPlaying() { return melodyPlaying; }
    public int getMelodyIndex() { return melodyIndex; }
    public @Nullable String getError() { return error; }

    public OutputState withMetronome(boolean on, int bpm, int beatsPerBar, int subdivision, boolean accent) {
        return new OutputState(on, bpm, beatsPerBar, subdivision, accent, droneMidi, melodyPlaying, melodyIndex, error);
    }
    public OutputState withDroneMidi(@Nullable Integer v) {
        return new OutputState(metronomeOn, bpm, beatsPerBar, subdivision, accent, v, melodyPlaying, melodyIndex, error);
    }
    public OutputState withMelody(boolean playing, int index) {
        return new OutputState(metronomeOn, bpm, beatsPerBar, subdivision, accent, droneMidi, playing, index, error);
    }
    public OutputState withError(@Nullable String v) {
        return new OutputState(metronomeOn, bpm, beatsPerBar, subdivision, accent, droneMidi, melodyPlaying, melodyIndex, v);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutputState)) return false;
        OutputState s = (OutputState) o;
        return metronomeOn == s.metronomeOn && bpm == s.bpm && beatsPerBar == s.beatsPerBar && subdivision == s.subdivision
                && accent == s.accent && Objects.equals(droneMidi, s.droneMidi) && melodyPlaying == s.melodyPlaying
                && melodyIndex == s.melodyIndex && Objects.equals(error, s.error);
    }

    @Override public int hashCode() {
        return Objects.hash(metronomeOn, bpm, beatsPerBar, subdivision, accent, droneMidi, melodyPlaying, melodyIndex, error);
    }

    @Override public String toString() {
        return "OutputState(metronomeOn=" + metronomeOn + ", bpm=" + bpm + ", beatsPerBar=" + beatsPerBar + ", subdivision=" + subdivision
                + ", accent=" + accent + ", droneMidi=" + droneMidi + ", melodyPlaying=" + melodyPlaying + ", melodyIndex=" + melodyIndex
                + ", error=" + error + ")";
    }
}
