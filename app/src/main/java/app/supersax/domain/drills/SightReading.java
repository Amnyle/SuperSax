package app.supersax.domain.drills;

import app.supersax.domain.library.Score;
import app.supersax.domain.pitch.SpelledPitch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Play-along sight-reading: a short melody (two bars of quarter notes) at a note-reading level, moving
 * mostly by step with a few small leaps, starting and ending on comfortable notes. The app follows the
 * player note by note with the microphone.
 */
public final class SightReading {
    private SightReading() {}

    public static final int NOTES = 8;

    public static List<SpelledPitch> melody(int level, long seed) {
        List<SpelledPitch> pool = new ArrayList<>(ReadingDrill.candidates(ReadingDrill.level(level)));
        pool.sort((a, b) -> Integer.compare(a.getMidi(), b.getMidi()));
        // One spelling per pitch keeps the melody readable.
        List<SpelledPitch> unique = new ArrayList<>();
        for (SpelledPitch p : pool) if (unique.isEmpty() || unique.get(unique.size() - 1).getMidi() != p.getMidi()) unique.add(p);
        Random rnd = new Random(seed);
        int idx = unique.size() / 3 + rnd.nextInt(Math.max(1, unique.size() / 3));
        List<SpelledPitch> out = new ArrayList<>();
        for (int i = 0; i < NOTES; i++) {
            out.add(unique.get(idx));
            int step = rnd.nextInt(10) < 7 ? 1 : 2 + rnd.nextInt(2);
            int dir = rnd.nextBoolean() ? 1 : -1;
            if (idx + dir * step < 0 || idx + dir * step >= unique.size()) dir = -dir;
            idx = Math.max(0, Math.min(unique.size() - 1, idx + dir * step));
        }
        return out;
    }

    /** The melody as a two-bar score (quarter notes) for the staff. */
    public static Score score(List<SpelledPitch> notes, int bpm) {
        List<app.supersax.domain.library.Measure> measures = new ArrayList<>();
        for (int m = 0; m < notes.size() / 4; m++) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 4; i++) { if (i > 0) b.append(' '); b.append(notes.get(m * 4 + i)).append(":1/4"); }
            measures.add(Score.parseMeasure(b.toString()));
        }
        return new Score(0, 4, 4, bpm, measures);
    }
}
