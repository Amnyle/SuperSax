package app.supersax.domain.audio;

/**
 * Pure sample generation for the tone synth, drone and melody playback. Phase is carried between buffers
 * so a continuous tone has no clicks; 20 ms gain ramps avoid clicks at start and stop.
 */
public final class ToneVoice {
    private final int sampleRate;
    private double frequencyHz;
    private Timbre timbre;
    private float gain;
    private double phase;
    private float level;
    private float targetLevel = 1f;
    private final float rampPerSample;

    public ToneVoice(int sampleRate, double frequencyHz, Timbre timbre, float gain) {
        this.sampleRate = sampleRate;
        this.frequencyHz = frequencyHz;
        this.timbre = timbre;
        this.gain = gain;
        this.rampPerSample = 1f / (0.02f * sampleRate);
    }

    public ToneVoice(int sampleRate, double frequencyHz, Timbre timbre) { this(sampleRate, frequencyHz, timbre, 0.3f); }

    public double getFrequencyHz() { return frequencyHz; }
    public void setFrequencyHz(double v) { frequencyHz = v; }
    public Timbre getTimbre() { return timbre; }
    public void setTimbre(Timbre v) { timbre = v; }
    public float getGain() { return gain; }
    public void setGain(float v) { gain = v; }

    public boolean getFinished() { return targetLevel == 0f && level == 0f; }

    public void release() { targetLevel = 0f; }

    /** Adds this voice into {@code out} (mixing). */
    public void mixInto(float[] out, int count) {
        double step = 2 * Math.PI * frequencyHz / sampleRate;
        for (int i = 0; i < count; i++) {
            if (level < targetLevel) level = Math.min(targetLevel, level + rampPerSample);
            else if (level > targetLevel) level = Math.max(targetLevel, level - rampPerSample);
            if (level == 0f && targetLevel == 0f) continue;
            out[i] += (float) (sample(phase) * gain * level);
            phase += step;
            if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
        }
    }

    public void mixInto(float[] out) { mixInto(out, out.length); }

    private double sample(double p) {
        if (timbre == Timbre.SINE) return Math.sin(p);
        // Fundamental plus gently falling harmonics, normalised to about the same loudness.
        return (Math.sin(p) + 0.45 * Math.sin(2 * p) + 0.22 * Math.sin(3 * p) + 0.10 * Math.sin(4 * p) + 0.05 * Math.sin(5 * p)) / 1.45;
    }
}
