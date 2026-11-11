package app.supersax.domain.audio;

/** A detected note: concert MIDI (rounded), start and end time. */
public final class DetectedNote {
    private final int midi;
    private final double startSec;
    private final double endSec;

    public DetectedNote(int midi, double startSec, double endSec) {
        this.midi = midi;
        this.startSec = startSec;
        this.endSec = endSec;
    }

    public int getMidi() { return midi; }
    public double getStartSec() { return startSec; }
    public double getEndSec() { return endSec; }
    public double getDurationSec() { return endSec - startSec; }
}
