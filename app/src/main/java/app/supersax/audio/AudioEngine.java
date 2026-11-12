package app.supersax.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import app.supersax.domain.audio.MelodyNote;
import app.supersax.domain.audio.MelodyPlayer;
import app.supersax.domain.audio.MetronomeGenerator;
import app.supersax.domain.audio.Timbre;
import app.supersax.domain.audio.ToneVoice;
import app.supersax.domain.pitch.Tuning;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.Nullable;

/**
 * One streaming AudioTrack that mixes the metronome, the drone, single reference tones and melody
 * playback. All timing comes from sample counts in the pure generators (no Handler timing). The
 * thread runs only while something is sounding.
 */
public final class AudioEngine {
    private static final int SAMPLE_RATE = 44_100;

    /** A reference tone and the samples left before its release. */
    private static final class Tone {
        final ToneVoice voice;
        long left;
        Tone(ToneVoice voice, long left) { this.voice = voice; this.left = left; }
    }

    private final Object lock = new Object();
    private final MutableStateFlow<OutputState> state = StateFlowKt.MutableStateFlow(new OutputState());

    private @Nullable MetronomeGenerator metronome;
    private @Nullable ToneVoice drone;
    private final List<Tone> tones = new ArrayList<>();
    private @Nullable MelodyPlayer melody;
    private @Nullable Runnable onMelodyDone;
    /** Pre-rendered sounds (recorded instruments) and how far each has played. */
    private final List<int[]> clipPos = new ArrayList<>();
    private final List<float[]> clips = new ArrayList<>();

    private volatile double a4 = Tuning.DEFAULT_A4;
    private volatile Timbre timbre = Timbre.SINE;

    private volatile @Nullable Thread thread;
    private volatile boolean running;

    public StateFlow<OutputState> getState() { return state; }
    public double getA4() { return a4; }
    public void setA4(double v) { a4 = v; }
    public Timbre getTimbre() { return timbre; }
    public void setTimbre(Timbre v) { timbre = v; }

    private void update(UnaryOperator<OutputState> f) {
        synchronized (state) { state.setValue(f.apply(state.getValue())); }
    }

    public void setMetronome(boolean on, int bpm, int beatsPerBar, int subdivision, boolean accent) {
        synchronized (lock) {
            if (on) {
                MetronomeGenerator m = metronome;
                if (m == null) metronome = new MetronomeGenerator(SAMPLE_RATE, bpm, beatsPerBar, subdivision, accent);
                else { m.setBpm(bpm); m.setBeatsPerBar(beatsPerBar); m.setSubdivision(subdivision); m.setAccent(accent); }
            } else metronome = null;
        }
        int clamped = Math.max(MetronomeGenerator.MIN_BPM, Math.min(MetronomeGenerator.MAX_BPM, bpm));
        update(s -> s.withMetronome(on, clamped, beatsPerBar, subdivision, accent));
        ensureRunning();
    }

    public void setMetronome(boolean on, int bpm) {
        OutputState s = state.getValue();
        setMetronome(on, bpm, s.getBeatsPerBar(), s.getSubdivision(), s.getAccent());
    }

    public void setMetronome(boolean on) { setMetronome(on, state.getValue().getBpm()); }

    public void setBeatsPerBar(int n) {
        OutputState s = state.getValue();
        setMetronome(s.getMetronomeOn(), s.getBpm(), n, s.getSubdivision(), s.getAccent());
    }

    public void setSubdivision(int n) {
        OutputState s = state.getValue();
        setMetronome(s.getMetronomeOn(), s.getBpm(), s.getBeatsPerBar(), n, s.getAccent());
    }

    public void setAccent(boolean on) {
        OutputState s = state.getValue();
        setMetronome(s.getMetronomeOn(), s.getBpm(), s.getBeatsPerBar(), s.getSubdivision(), on);
    }

    /** Starts (or changes) the drone on a concert MIDI note; null stops it. */
    public void setDrone(@Nullable Integer concertMidi) {
        synchronized (lock) {
            if (drone != null) drone.release();
            drone = concertMidi == null ? null : new ToneVoice(SAMPLE_RATE, Tuning.midiToFrequency(concertMidi, a4), timbre, 0.25f);
        }
        update(s -> s.withDroneMidi(concertMidi));
        ensureRunning();
    }

    /** Plays a reference tone (concert MIDI) for millis. */
    public void playTone(int concertMidi, long millis) {
        synchronized (lock) {
            tones.add(new Tone(new ToneVoice(SAMPLE_RATE, Tuning.midiToFrequency(concertMidi, a4), timbre, 0.3f), millis * SAMPLE_RATE / 1000));
        }
        ensureRunning();
    }

    public void playTone(int concertMidi) { playTone(concertMidi, 1500); }

    private @Nullable app.supersax.domain.audio.SampledInstrument.Voice hold;
    private @Nullable ToneVoice holdTone;

    /** Starts a note that sounds until [stopHold]: on [inst] if given, else the tone synth. */
    public void startHold(@Nullable app.supersax.domain.audio.SampledInstrument inst, int concertMidi) {
        synchronized (lock) {
            if (hold != null) hold.release();
            if (holdTone != null) holdTone.release();
            double shift = 12 * Math.log(a4 / 440.0) / Math.log(2);
            if (inst != null) { hold = inst.start(concertMidi + shift, 0.6, SAMPLE_RATE); holdTone = null; }
            else { holdTone = new ToneVoice(SAMPLE_RATE, Tuning.midiToFrequency(concertMidi, a4), timbre, 0.3f); hold = null; }
        }
        ensureRunning();
    }

    public void stopHold() {
        synchronized (lock) {
            if (hold != null) hold.release();
            if (holdTone != null) holdTone.release();
        }
    }

    /** Output sample rate, for sounds rendered with [playClip]. */
    public int getSampleRate() { return SAMPLE_RATE; }

    /** Plays a pre-rendered mono buffer at [getSampleRate], replacing other clips. */
    public void playClip(float[] samples) {
        synchronized (lock) { clips.clear(); clipPos.clear(); clips.add(samples); clipPos.add(new int[]{0}); }
        ensureRunning();
    }

    /** Plays a melody with the tone synth at bpm (quarter-note beats). */
    public void playMelody(List<MelodyNote> notes, int bpm, @Nullable Runnable onDone) {
        synchronized (lock) {
            melody = new MelodyPlayer(SAMPLE_RATE, notes, bpm, a4, timbre);
            onMelodyDone = onDone;
        }
        update(s -> s.withMelody(true, 0));
        ensureRunning();
    }

    public void playMelody(List<MelodyNote> notes, int bpm) { playMelody(notes, bpm, null); }

    public void stopMelody() {
        synchronized (lock) { melody = null; onMelodyDone = null; }
        update(s -> s.withMelody(false, -1));
    }

    public void stopAll() {
        synchronized (lock) { metronome = null; drone = null; tones.clear(); melody = null; clips.clear(); clipPos.clear(); hold = null; holdTone = null; }
        update(s -> new OutputState(false, s.getBpm(), s.getBeatsPerBar(), s.getSubdivision(), s.getAccent(), null, false, -1, null));
    }

    private boolean busy() {
        synchronized (lock) { return metronome != null || drone != null || !tones.isEmpty() || melody != null || !clips.isEmpty() || hold != null || holdTone != null; }
    }

    private void ensureRunning() {
        if (running || !busy()) return;
        running = true;
        Thread t = new Thread(this::loop, "sax-audio-out");
        t.setPriority(Thread.MAX_PRIORITY);
        thread = t;
        t.start();
    }

    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private void loop() {
        int minBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(minBytes, 4096 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build();
        } catch (Exception e) {
            String msg = message(e);
            update(s -> s.withError(msg));
            running = false;
            return;
        }
        float[] buf = new float[512];
        try {
            track.play();
            update(s -> s.withError(null));
            int idleBuffers = 0;
            while (running) {
                Arrays.fill(buf, 0f);
                Runnable done = null;
                boolean melodyDone = false;
                synchronized (lock) {
                    if (metronome != null) metronome.mixInto(buf);
                    if (drone != null) {
                        drone.mixInto(buf);
                        if (drone.getFinished()) drone = null;
                    }
                    Iterator<Tone> it = tones.iterator();
                    while (it.hasNext()) {
                        Tone t = it.next();
                        t.voice.mixInto(buf);
                        t.left -= buf.length;
                        if (t.left <= 0) t.voice.release();
                        if (t.voice.getFinished()) it.remove();
                    }
                    if (hold != null) { hold.mixInto(buf, buf.length); if (hold.getFinished()) hold = null; }
                    if (holdTone != null) { holdTone.mixInto(buf); if (holdTone.getFinished()) holdTone = null; }
                    for (int c = clips.size() - 1; c >= 0; c--) {
                        float[] clip = clips.get(c);
                        int[] at = clipPos.get(c);
                        int n = Math.min(buf.length, clip.length - at[0]);
                        for (int i = 0; i < n; i++) buf[i] += clip[at[0] + i];
                        at[0] += n;
                        if (at[0] >= clip.length) { clips.remove(c); clipPos.remove(c); }
                    }
                    MelodyPlayer m = melody;
                    if (m != null) {
                        m.mixInto(buf);
                        int idx = m.getCurrentIndex();
                        if (state.getValue().getMelodyIndex() != idx) update(s -> s.withMelody(s.getMelodyPlaying(), idx));
                        if (m.getFinished()) { melody = null; done = onMelodyDone; onMelodyDone = null; melodyDone = true; }
                    }
                }
                if (melodyDone) {
                    update(s -> s.withMelody(false, -1));
                    if (done != null) done.run();
                }
                for (int i = 0; i < buf.length; i++) buf[i] = Math.max(-1f, Math.min(1f, buf[i]));
                track.write(buf, 0, buf.length, AudioTrack.WRITE_BLOCKING);
                idleBuffers = busy() ? 0 : idleBuffers + 1;
                if (idleBuffers > 20) running = false;
            }
        } catch (Exception e) {
            String msg = message(e);
            update(s -> s.withError(msg));
        } finally {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
            running = false;
            if (busy()) ensureRunning();
        }
    }
}
