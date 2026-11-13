package app.supersax.domain.longtone;

import app.supersax.domain.audio.SampledInstrument;
import app.supersax.domain.pitch.Tuning;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * A sustained drone of one or more notes. Synth sounds are additive (phase kept between buffers, 50 ms fades);
 * the sax sound loops the recorded alto sax. Volume changes glide so a slider never clicks.
 */
public final class DroneChord {
    private final int sampleRate;
    private final DroneSound sound;
    private final double[] freqs, phases;
    private final List<SampledInstrument.Voice> voices = new ArrayList<>();
    private final float fadePerSample;
    private float level, target = 1f;
    private volatile float volume;
    private float gain;
    private final float perNote;
    private float[] tmp = new float[0];

    public DroneChord(int sampleRate, List<Integer> concertMidis, double a4, DroneSound sound, float volume, @Nullable SampledInstrument sax) {
        this.sampleRate = sampleRate;
        this.volume = volume;
        this.gain = volume;
        this.fadePerSample = 1f / (0.05f * sampleRate);
        boolean sampled = sound == DroneSound.SAX && sax != null;
        for (int m : concertMidis) if (sampled && !sax.loops(m)) sampled = false;
        this.sound = sampled ? DroneSound.SAX : (sound == DroneSound.SAX ? DroneSound.WARM : sound);
        freqs = new double[concertMidis.size()];
        phases = new double[concertMidis.size()];
        double shift = 12 * Math.log(a4 / 440.0) / Math.log(2);
        // Quieter per note as notes are added, so a chord is about as loud as one note.
        float perNote = (float) (0.32 / Math.sqrt(concertMidis.size()));
        for (int i = 0; i < freqs.length; i++) {
            int m = concertMidis.get(i);
            freqs[i] = Tuning.midiToFrequency(m, a4);
            if (sampled) voices.add(sax.start(m + shift, perNote * 1.6, sampleRate));
        }
        this.perNote = perNote;
    }

    public DroneSound getSound() { return sound; }
    public void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }
    public void release() { target = 0f; }
    public boolean getFinished() { return target == 0f && level == 0f; }

    public void mixInto(float[] out, int count) {
        if (sound == DroneSound.SAX) {
            if (tmp.length < count) tmp = new float[count];
            java.util.Arrays.fill(tmp, 0, count, 0f);
            for (SampledInstrument.Voice v : voices) v.mixInto(tmp, count);
            for (int i = 0; i < count; i++) { step(); out[i] += tmp[i] * level * gain; }
            return;
        }
        for (int i = 0; i < count; i++) {
            step();
            if (level == 0f) continue;
            double s = 0;
            for (int n = 0; n < freqs.length; n++) {
                s += shape(phases[n]);
                phases[n] += 2 * Math.PI * freqs[n] / sampleRate;
                if (phases[n] > 2 * Math.PI) phases[n] -= 2 * Math.PI;
            }
            out[i] += (float) (s * perNote * level * gain);
        }
    }

    private void step() {
        if (level < target) level = Math.min(target, level + fadePerSample);
        else if (level > target) level = Math.max(target, level - fadePerSample);
        float v = volume;
        if (gain < v) gain = Math.min(v, gain + 0.0005f); else if (gain > v) gain = Math.max(v, gain - 0.0005f);
    }

    private double shape(double p) {
        switch (sound) {
            case PURE: return Math.sin(p);
            case ORGAN: // flue pipes: 8' + 4' + 2 2/3' + 2'
                return (Math.sin(p) + 0.6 * Math.sin(2 * p) + 0.3 * Math.sin(3 * p) + 0.35 * Math.sin(4 * p) + 0.1 * Math.sin(6 * p)) / 1.8;
            default: // warm reed: odd harmonics a little stronger, gently falling
                return (Math.sin(p) + 0.35 * Math.sin(2 * p) + 0.4 * Math.sin(3 * p) + 0.12 * Math.sin(4 * p) + 0.15 * Math.sin(5 * p) + 0.05 * Math.sin(7 * p)) / 1.6;
        }
    }
}
