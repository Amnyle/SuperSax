package app.supersax.domain.audio;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Incremental hold detection for the live tuner: feed frames, get a report each time a hold ends. */
public final class HoldTracker {
    private final HoldConfig config;
    private @Nullable Integer targetMidi;
    private final List<PitchFrame> frames = new ArrayList<>();
    private boolean inHold;
    private int runStartIndex = -1;
    private int lastSoundIndex = -1;

    public HoldTracker(HoldConfig config, @Nullable Integer targetMidi) {
        this.config = config;
        this.targetMidi = targetMidi;
    }

    public HoldTracker(HoldConfig config) { this(config, null); }

    public HoldTracker() { this(new HoldConfig(), null); }

    public @Nullable Integer getTargetMidi() { return targetMidi; }

    public void setTargetMidi(@Nullable Integer targetMidi) { this.targetMidi = targetMidi; }

    /** True while a hold is in progress (after the onset time). */
    public boolean getHolding() { return inHold; }

    /** Seconds since the current hold began, or 0. */
    public double holdSeconds() {
        if (!inHold || runStartIndex < 0 || lastSoundIndex < 0) return 0.0;
        return frames.get(lastSoundIndex).getTimeSec() - frames.get(runStartIndex).getTimeSec();
    }

    public @Nullable HoldReport push(PitchFrame f) {
        frames.add(f);
        int i = frames.size() - 1;
        if (HoldAnalysis.isSound(f, config)) {
            if (runStartIndex == -1) runStartIndex = i;
            lastSoundIndex = i;
            if (!inHold && f.getTimeSec() - frames.get(runStartIndex).getTimeSec() >= config.getOnsetSec()) inHold = true;
            return null;
        }
        if (runStartIndex == -1) { frames.clear(); return null; }
        if (lastSoundIndex >= 0 && f.getTimeSec() - frames.get(lastSoundIndex).getTimeSec() >= config.getReleaseGapSec()) {
            HoldReport report = inHold
                ? HoldAnalysis.analyse(new ArrayList<>(frames.subList(runStartIndex, lastSoundIndex + 1)), targetMidi, config) : null;
            reset();
            return report;
        }
        return null;
    }

    public void reset() { frames.clear(); inHold = false; runStartIndex = -1; lastSoundIndex = -1; }
}
