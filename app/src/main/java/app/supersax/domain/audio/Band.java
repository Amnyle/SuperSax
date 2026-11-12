package app.supersax.domain.audio;

import org.jetbrains.annotations.Nullable;

/** The recorded instruments a backing track or the ear trainer can use (any may be missing: synthesis then). */
public final class Band {
    public final @Nullable SampledInstrument piano, epiano, bass, ebass, altoSax, jazzKit, brushKit, funkKit;

    public Band(@Nullable SampledInstrument piano, @Nullable SampledInstrument epiano, @Nullable SampledInstrument bass,
                @Nullable SampledInstrument ebass, @Nullable SampledInstrument altoSax, @Nullable SampledInstrument jazzKit,
                @Nullable SampledInstrument brushKit, @Nullable SampledInstrument funkKit) {
        this.piano = piano; this.epiano = epiano; this.bass = bass; this.ebass = ebass; this.altoSax = altoSax;
        this.jazzKit = jazzKit; this.brushKit = brushKit; this.funkKit = funkKit;
    }

    /** The sound files, by name in assets/sounds. */
    public static final String[] FILES = {"piano", "epiano", "bass", "ebass", "altosax", "jazzkit", "brushkit", "funkkit"};

    public interface Opener { java.io.InputStream open(String name) throws java.io.IOException; }

    /** Loads every instrument it can; a missing or broken file leaves that slot empty. */
    public static Band load(Opener opener) {
        SampledInstrument[] s = new SampledInstrument[FILES.length];
        for (int i = 0; i < FILES.length; i++) {
            try (java.io.InputStream in = opener.open(FILES[i])) { s[i] = SampledInstrument.read(in); } catch (Exception e) { s[i] = null; }
        }
        return new Band(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7]);
    }
}
