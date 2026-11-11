package app.supersax.domain.library;

import java.util.Objects;

/** Why a piece is locked or a stretch. Semitone counts are how far the range is exceeded. */
public abstract class Missing {
    private Missing() {}

    public static final class RangeLow extends Missing {
        private final int semitones, writtenMidi;
        public RangeLow(int semitones, int writtenMidi) { this.semitones = semitones; this.writtenMidi = writtenMidi; }
        public int getSemitones() { return semitones; }
        public int getWrittenMidi() { return writtenMidi; }
        @Override public boolean equals(Object o) { return o instanceof RangeLow && ((RangeLow) o).semitones == semitones && ((RangeLow) o).writtenMidi == writtenMidi; }
        @Override public int hashCode() { return Objects.hash(1, semitones, writtenMidi); }
        @Override public String toString() { return "RangeLow(" + semitones + ", " + writtenMidi + ")"; }
    }

    public static final class RangeHigh extends Missing {
        private final int semitones, writtenMidi;
        public RangeHigh(int semitones, int writtenMidi) { this.semitones = semitones; this.writtenMidi = writtenMidi; }
        public int getSemitones() { return semitones; }
        public int getWrittenMidi() { return writtenMidi; }
        @Override public boolean equals(Object o) { return o instanceof RangeHigh && ((RangeHigh) o).semitones == semitones && ((RangeHigh) o).writtenMidi == writtenMidi; }
        @Override public int hashCode() { return Objects.hash(2, semitones, writtenMidi); }
        @Override public String toString() { return "RangeHigh(" + semitones + ", " + writtenMidi + ")"; }
    }

    public static final class Scale extends Missing {
        private final String scaleKey;
        private final boolean nextUnlock;
        public Scale(String scaleKey, boolean nextUnlock) { this.scaleKey = scaleKey; this.nextUnlock = nextUnlock; }
        public String getScaleKey() { return scaleKey; }
        public boolean isNextUnlock() { return nextUnlock; }
        @Override public boolean equals(Object o) { return o instanceof Scale && ((Scale) o).scaleKey.equals(scaleKey) && ((Scale) o).nextUnlock == nextUnlock; }
        @Override public int hashCode() { return Objects.hash(3, scaleKey, nextUnlock); }
    }

    /** Metadata-only entry: no notes in the app. */
    public static final class NoNotes extends Missing {
        public static final NoNotes INSTANCE = new NoNotes();
        private NoNotes() {}
    }
}
