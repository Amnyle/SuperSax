package app.supersax.domain.audio;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A recorded instrument: zones of samples (key range, loudness range, root pitch, optional loop), read from a sound
 * file of assets/sounds (see tools/build_sounds.py). Notes are mixed into a float buffer at any output rate,
 * pitch-shifted from the nearest sample with cubic interpolation; louder notes use louder recordings when the
 * instrument has several. Optional expression bends the pitch: a scoop into the note, vibrato, a fall at its end.
 */
public final class SampledInstrument {
    static final class Zone {
        int lo, hi, vlo = 0, vhi = 127, loopStart, loopEnd, length, offset;
        float root, gain;
    }

    /** Pitch shaping of a note, in cents. */
    public static final class Expression {
        public final double scoopCents, scoopSec, vibratoCents, vibratoDelaySec, fallCents, fallSec;
        public Expression(double scoopCents, double scoopSec, double vibratoCents, double vibratoDelaySec, double fallCents, double fallSec) {
            this.scoopCents = scoopCents; this.scoopSec = scoopSec; this.vibratoCents = vibratoCents;
            this.vibratoDelaySec = vibratoDelaySec; this.fallCents = fallCents; this.fallSec = fallSec;
        }
        public static final Expression NONE = new Expression(0, 0, 0, 0, 0, 0);
    }

    private final Zone[] zones;
    private final short[] pcm;
    private final int rate;

    private SampledInstrument(Zone[] zones, short[] pcm, int rate) { this.zones = zones; this.pcm = pcm; this.rate = rate; }

    public static SampledInstrument read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new BufferedInputStream(in, 1 << 16));
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (magic[0] != 'S' || magic[1] != 'X' || magic[2] != 'S' || magic[3] != 'B') throw new IOException("not a sound file");
        int version = d.readInt();
        int rate = d.readInt(), n = d.readInt();
        Zone[] zones = new Zone[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            Zone z = new Zone();
            z.lo = d.readUnsignedByte(); z.hi = d.readUnsignedByte();
            if (version >= 2) { z.vlo = d.readUnsignedByte(); z.vhi = d.readUnsignedByte(); }
            z.root = d.readFloat(); z.gain = d.readFloat();
            z.loopStart = d.readInt(); z.loopEnd = d.readInt(); z.length = d.readInt(); z.offset = d.readInt();
            zones[i] = z;
            total = Math.max(total, z.offset + z.length);
        }
        short[] pcm = new short[total];
        byte[] buf = new byte[1 << 16];
        int k = 0;
        while (k < total) {
            int want = Math.min(buf.length, (total - k) * 2);
            d.readFully(buf, 0, want);
            for (int i = 0; i < want; i += 2) pcm[k++] = (short) ((buf[i] << 8) | (buf[i + 1] & 0xFF));
        }
        return new SampledInstrument(zones, pcm, rate);
    }

    /** The zone for [midi] at loudness [vel] (0–127): in range, else the nearest root among the right loudness. */
    Zone zone(int midi, int vel) {
        Zone best = null;
        for (Zone z : zones) if (midi >= z.lo && midi <= z.hi && vel >= z.vlo && vel <= z.vhi) return z;
        for (Zone z : zones) if (midi >= z.lo && midi <= z.hi) return z;
        for (Zone z : zones) if (best == null || Math.abs(z.root - midi) < Math.abs(best.root - midi)) best = z;
        return best;
    }

    Zone zone(int midi) { return zone(midi, 100); }

    /** True when a sample exists for exactly this key (kits: one zone per drum). */
    /** True when the note's sample has a sustain loop, so it can be held for as long as needed. */
    public boolean loops(int midi) {
        Zone z = zone(midi);
        return z != null && z.loopEnd > z.loopStart && z.loopStart >= 0;
    }

    public boolean has(int midi) {
        for (Zone z : zones) if (midi >= z.lo && midi <= z.hi) return true;
        return false;
    }

    public void play(float[] out, int outRate, int startFrame, double midi, double gain, double holdSec, double releaseSec) {
        play(out, outRate, startFrame, midi, gain, holdSec, releaseSec, 0.75, Expression.NONE);
    }

    /**
     * Adds a note to [out] (sampled at [outRate]) from sample [startFrame]: pitch [midi] (fractional allowed),
     * loudness [gain], recording chosen by [layer] (0–1: soft to loud), held [holdSec] then released over
     * [releaseSec], with [expr] pitch shaping. Unlooped samples simply play out.
     */
    public void play(float[] out, int outRate, int startFrame, double midi, double gain, double holdSec, double releaseSec,
                     double layer, Expression expr) {
        int vel = (int) Math.max(0, Math.min(127, Math.round(layer * 127)));
        Zone z = zone((int) Math.round(midi), vel);
        if (z == null) return;
        double baseStep = Math.pow(2, (midi - z.root) / 12.0) * rate / outRate;
        boolean loop = z.loopEnd > z.loopStart && z.loopStart >= 0;
        int hold = (int) (holdSec * outRate), rel = Math.max(1, (int) (releaseSec * outRate));
        int maxFrames = hold + rel;
        boolean bend = expr != Expression.NONE;
        int scoopN = (int) (expr.scoopSec * outRate), fallN = (int) (expr.fallSec * outRate);
        int vibStart = (int) (expr.vibratoDelaySec * outRate);
        double vibPhase = 0, vibW = 2 * Math.PI * 5.4 / outRate;
        double pos = 0;
        for (int i = 0; i < maxFrames; i++) {
            int o = startFrame + i;
            if (o >= out.length) break;
            if (loop) {
                while (pos >= z.loopEnd) pos -= z.loopEnd - z.loopStart;
            } else if (pos >= z.length - 2) break;
            int k = (int) pos;
            float f = (float) (pos - k);
            float y0 = at(z, k - 1, loop), y1 = at(z, k, loop), y2 = at(z, k + 1, loop), y3 = at(z, k + 2, loop);
            float c1 = 0.5f * (y2 - y0), c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3, c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
            float y = ((c3 * f + c2) * f + c1) * f + y1;
            double env = i < hold ? 1 : 1 - (double) (i - hold) / rel;
            if (i < 32) env *= i / 32.0; // no click on attack
            if (o >= 0) out[o] += (float) (gain * env * env * y);
            double step = baseStep;
            if (bend) {
                double cents = 0;
                if (i < scoopN) { double a = 1 - (double) i / scoopN; cents -= expr.scoopCents * a * a; }
                if (i > vibStart && expr.vibratoCents > 0) {
                    double depth = expr.vibratoCents * Math.min(1, (i - vibStart) / (0.4 * outRate));
                    cents += depth * Math.sin(vibPhase);
                    vibPhase += vibW;
                }
                if (fallN > 0 && i > hold - fallN && i < hold + rel) { double a = (double) (i - (hold - fallN)) / fallN; cents -= expr.fallCents * a * a; }
                step = baseStep * Math.pow(2, cents / 1200.0);
            }
            pos += step;
        }
    }

    private float at(Zone z, int k, boolean loop) {
        if (k < 0) k = 0;
        if (loop && k >= z.loopEnd) k -= z.loopEnd - z.loopStart;
        if (k >= z.length) return 0;
        return pcm[z.offset + k] / 32768f;
    }

    /** A note that plays until released, for live sound (e.g. while a finger is held down). */
    public final class Voice {
        private final Zone z;
        private final double step;
        private final double gain;
        private final boolean loop;
        private final int outRate;
        private double pos;
        private int age, releaseLeft = -1, releaseTotal;
        private boolean finished;

        Voice(double midi, double gain, int outRate) {
            this.z = zone((int) Math.round(midi));
            this.step = z == null ? 0 : Math.pow(2, (midi - z.root) / 12.0) * rate / outRate;
            this.gain = gain;
            this.loop = z != null && z.loopEnd > z.loopStart && z.loopStart >= 0;
            this.outRate = outRate;
            this.finished = z == null;
        }

        public void release() { if (releaseLeft < 0) { releaseTotal = Math.max(1, outRate / 12); releaseLeft = releaseTotal; } }
        public boolean getFinished() { return finished; }

        public void mixInto(float[] out, int count) {
            for (int i = 0; i < count && !finished; i++) {
                if (loop) { while (pos >= z.loopEnd) pos -= z.loopEnd - z.loopStart; }
                else if (pos >= z.length - 2) { finished = true; break; }
                int k = (int) pos;
                float f = (float) (pos - k);
                float y0 = at(z, k - 1, loop), y1 = at(z, k, loop), y2 = at(z, k + 1, loop), y3 = at(z, k + 2, loop);
                float c1 = 0.5f * (y2 - y0), c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3, c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
                float y = ((c3 * f + c2) * f + c1) * f + y1;
                double env = age < 64 ? age / 64.0 : 1;
                if (releaseLeft >= 0) {
                    double r = (double) releaseLeft / releaseTotal;
                    env *= r * r;
                    if (--releaseLeft <= 0) finished = true;
                }
                out[i] += (float) (gain * env * y);
                pos += step;
                age++;
            }
        }
    }

    /** Starts a held note at [midi] for an output at [outRate]; call release() on it to end it. */
    public Voice start(double midi, double gain, int outRate) { return new Voice(midi, gain, outRate); }
}
