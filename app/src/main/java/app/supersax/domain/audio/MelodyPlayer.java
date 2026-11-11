package app.supersax.domain.audio;

import app.supersax.domain.pitch.Tuning;
import java.util.List;

/** Plays a melody with a {@link ToneVoice} envelope at a tempo, sample-accurately. */
public final class MelodyPlayer {
    private final int sampleRate;
    private final List<MelodyNote> notes;
    private final double a4;
    private final Timbre timbre;
    private final double samplesPerBeat;
    private int index;
    private double remaining;
    private ToneVoice voice;

    public MelodyPlayer(int sampleRate, List<MelodyNote> notes, int bpm, double a4, Timbre timbre) {
        this.sampleRate = sampleRate;
        this.notes = notes;
        this.a4 = a4;
        this.timbre = timbre;
        this.samplesPerBeat = sampleRate * 60.0 / bpm;
        this.remaining = notes.isEmpty() ? 0.0 : notes.get(0).getBeats() * samplesPerBeat;
        this.voice = startVoice(0);
    }

    public boolean getFinished() { return index >= notes.size(); }

    /** Index of the note being played (for highlighting). */
    public int getCurrentIndex() { return index; }

    private ToneVoice startVoice(int i) {
        if (i >= notes.size() || notes.get(i).getMidi() == null) return null;
        return new ToneVoice(sampleRate, Tuning.midiToFrequency(notes.get(i).getMidi(), a4), timbre, 0.3f);
    }

    public void mixInto(float[] out, int count) {
        int pos = 0;
        while (pos < count && !getFinished()) {
            int n = Math.min(Math.max(1, (int) remaining), count - pos);
            float[] tmp = new float[n];
            if (voice != null) voice.mixInto(tmp, n);
            for (int i = 0; i < n; i++) out[pos + i] += tmp[i];
            remaining -= n;
            // Release slightly before the next note so repeated notes are articulated.
            if (remaining < 0.03 * sampleRate && voice != null) voice.release();
            pos += n;
            if (remaining <= 0) {
                index++;
                if (!getFinished()) {
                    remaining += notes.get(index).getBeats() * samplesPerBeat;
                    voice = startVoice(index);
                }
            }
        }
    }

    public void mixInto(float[] out) { mixInto(out, out.length); }
}
