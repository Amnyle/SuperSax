package app.supersax.domain.audio;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;

/**
 * A recorded instrument: zones of samples (key range, root pitch, optional loop), read from a sound file of
 * assets/sounds (see tools/build_sounds.py). Notes are mixed into a float buffer at any output rate, pitch-shifted
 * from the nearest sample with cubic interpolation.
 */
public final class SampledInstrument {
    static final class Zone {
        int lo, hi, loopStart, loopEnd, length, offset;
        float root, gain;
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
        d.readInt();
        int rate = d.readInt(), n = d.readInt();
        Zone[] zones = new Zone[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            Zone z = new Zone();
            z.lo = d.readUnsignedByte(); z.hi = d.readUnsignedByte();
            z.root = d.readFloat(); z.gain = d.readFloat();
            z.loopStart = d.readInt(); z.loopEnd = d.readInt(); z.length = d.readInt(); z.offset = d.readInt();
            zones[i] = z;
            total = Math.max(total, z.offset + z.length);
        }
        short[] pcm = new short[total];
        byte[] buf = new byte[total * 2];
        d.readFully(buf);
        for (int i = 0; i < total; i++) pcm[i] = (short) ((buf[2 * i] << 8) | (buf[2 * i + 1] & 0xFF));
        return new SampledInstrument(zones, pcm, rate);
    }

    /** The zone for [midi]: the one whose range holds it, else the one with the nearest root. */
    Zone zone(int midi) {
        Zone best = null;
        for (Zone z : zones) if (midi >= z.lo && midi <= z.hi) return z;
        for (Zone z : zones) if (best == null || Math.abs(z.root - midi) < Math.abs(best.root - midi)) best = z;
        return best;
    }

    /** True when a sample exists for exactly this key (kits: one zone per drum). */
    public boolean has(int midi) {
        for (Zone z : zones) if (midi >= z.lo && midi <= z.hi) return true;
        return false;
    }

    /**
     * Adds a note to [out] (sampled at [outRate]) from sample [startFrame]: pitch [midi] (fractional allowed),
     * loudness [gain], held [holdSec] then released over [releaseSec]. Unlooped samples simply play out.
     */
    public void play(float[] out, int outRate, int startFrame, double midi, double gain, double holdSec, double releaseSec) {
        Zone z = zone((int) Math.round(midi));
        if (z == null) return;
        double step = Math.pow(2, (midi - z.root) / 12.0) * rate / outRate;
        boolean loop = z.loopEnd > z.loopStart && z.loopStart >= 0;
        int hold = (int) (holdSec * outRate), rel = Math.max(1, (int) (releaseSec * outRate));
        int maxFrames = hold + rel;
        double pos = 0, g = gain;
        for (int i = 0; i < maxFrames; i++) {
            int o = startFrame + i;
            if (o >= out.length) break;
            if (loop) {
                while (pos >= z.loopEnd) pos -= z.loopEnd - z.loopStart;
            } else if (pos >= z.length - 2) break;
            int k = (int) pos;
            float f = (float) (pos - k);
            float y0 = at(z, k - 1, loop), y1 = at(z, k, loop), y2 = at(z, k + 1, loop), y3 = at(z, k + 2, loop);
            // Catmull-Rom
            float c1 = 0.5f * (y2 - y0), c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3, c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
            float y = ((c3 * f + c2) * f + c1) * f + y1;
            double env = i < hold ? 1 : 1 - (double) (i - hold) / rel;
            if (i < 32) env *= i / 32.0; // no click on attack
            if (o >= 0) out[o] += (float) (g * env * env * y);
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
