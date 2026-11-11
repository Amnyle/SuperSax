package app.supersax.domain.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Sample-accurate metronome: clicks are written at exact sample positions computed from the tempo,
 * never timed with handlers. Beat 1 of each bar is accented; subdivisions add quieter ticks.
 */
public final class MetronomeGenerator {
    public static final int MIN_BPM = 30;
    public static final int MAX_BPM = 240;

    private final int sampleRate;
    private int bpm;
    private int beatsPerBar;
    private int subdivision;
    private boolean accent;
    /** Fractional sample position of the next tick, relative to the start of the next buffer. */
    private double nextTick;
    private long tickIndex;
    private final float[] accentClick, beatClick, subClick;
    /** Remaining part of a click that crossed the previous buffer boundary. */
    private float[] tail;
    private int tailPos;
    private final List<Long> tickLog = new ArrayList<>();
    private boolean logTicks;
    private long written;

    public MetronomeGenerator(int sampleRate, int bpm, int beatsPerBar, int subdivision, boolean accent) {
        this.sampleRate = sampleRate;
        setBpm(bpm);
        this.beatsPerBar = beatsPerBar;
        this.subdivision = subdivision;
        this.accent = accent;
        int clickLen = (int) (0.025 * sampleRate);
        accentClick = click(clickLen, 2000.0, 0.9f);
        beatClick = click(clickLen, 1300.0, 0.7f);
        subClick = click(clickLen, 1300.0, 0.3f);
    }

    public MetronomeGenerator(int sampleRate, int bpm, int beatsPerBar, int subdivision) { this(sampleRate, bpm, beatsPerBar, subdivision, true); }

    public MetronomeGenerator(int sampleRate, int bpm) { this(sampleRate, bpm, 4, 1, true); }

    public int getBpm() { return bpm; }
    public void setBpm(int v) { bpm = Math.max(MIN_BPM, Math.min(MAX_BPM, v)); }
    public int getBeatsPerBar() { return beatsPerBar; }
    public void setBeatsPerBar(int v) { beatsPerBar = v; }
    public int getSubdivision() { return subdivision; }
    public void setSubdivision(int v) { subdivision = v; }
    public boolean getAccent() { return accent; }
    public void setAccent(boolean v) { accent = v; }

    /** Positions (in samples, absolute) of every tick written so far: for tests. */
    public List<Long> getTickLog() { return tickLog; }
    public boolean getLogTicks() { return logTicks; }
    public void setLogTicks(boolean v) { logTicks = v; }

    private float[] click(int len, double freq, float amp) {
        float[] c = new float[len];
        for (int i = 0; i < len; i++) c[i] = (float) (amp * Math.sin(2 * Math.PI * freq * i / sampleRate) * Math.exp(-i / (0.004 * sampleRate)));
        return c;
    }

    public double samplesPerTick() { return sampleRate * 60.0 / bpm / subdivision; }

    public void reset() { nextTick = 0; tickIndex = 0; tail = null; written = 0; tickLog.clear(); }

    /** Mixes clicks into {@code out}. */
    public void mixInto(float[] out, int count) {
        if (tail != null) {
            int n = Math.min(tail.length - tailPos, count);
            for (int i = 0; i < n; i++) out[i] += tail[tailPos + i];
            tailPos += n;
            if (tailPos >= tail.length) tail = null;
        }
        while (nextTick < count) {
            int pos = (int) nextTick;
            int inBeat = (int) (tickIndex % subdivision);
            long beat = (tickIndex / subdivision) % beatsPerBar;
            float[] c = inBeat != 0 ? subClick : (beat == 0 && accent ? accentClick : beatClick);
            if (logTicks) tickLog.add(written + pos);
            int n = Math.min(c.length, count - pos);
            for (int i = 0; i < n; i++) out[pos + i] += c[i];
            if (n < c.length) { tail = c; tailPos = n; }
            tickIndex++;
            nextTick += samplesPerTick();
        }
        nextTick -= count;
        written += count;
    }

    public void mixInto(float[] out) { mixInto(out, out.length); }
}
