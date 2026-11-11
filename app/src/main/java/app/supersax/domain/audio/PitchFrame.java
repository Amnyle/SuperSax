package app.supersax.domain.audio;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** One analysis frame: time of the frame centre, detected frequency (null if unvoiced), confidence, level. */
public final class PitchFrame {
    private final double timeSec;
    private final @Nullable Double frequencyHz;
    private final double confidence;
    private final double rms;

    public PitchFrame(double timeSec, @Nullable Double frequencyHz, double confidence, double rms) {
        this.timeSec = timeSec;
        this.frequencyHz = frequencyHz;
        this.confidence = confidence;
        this.rms = rms;
    }

    public double getTimeSec() { return timeSec; }

    public @Nullable Double getFrequencyHz() { return frequencyHz; }

    public double getConfidence() { return confidence; }

    public double getRms() { return rms; }

    public boolean getVoiced() { return frequencyHz != null; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof PitchFrame)) return false;
        PitchFrame f = (PitchFrame) o;
        return timeSec == f.timeSec && Objects.equals(frequencyHz, f.frequencyHz) && confidence == f.confidence && rms == f.rms;
    }

    @Override public int hashCode() { return Objects.hash(timeSec, frequencyHz, confidence, rms); }

    @Override public String toString() { return "PitchFrame(" + timeSec + ", " + frequencyHz + ", " + confidence + ", " + rms + ")"; }
}
