package app.supersax.domain.longtone;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Notes a drone can add above its root: the unison itself, a major third and a perfect fifth. */
public enum DroneInterval {
    UNISON(0), MAJOR_THIRD(4), FIFTH(7);

    public final int semitones;
    DroneInterval(int semitones) { this.semitones = semitones; }

    /**
     * Concert notes to sound for [root]. The third and fifth are placed above the root; if nothing is
     * chosen, the root alone sounds, so the drone never goes silent by accident.
     */
    public static List<Integer> notes(int root, Set<DroneInterval> on) {
        List<Integer> out = new ArrayList<>();
        for (DroneInterval i : values()) if (on.contains(i)) out.add(root + i.semitones);
        if (out.isEmpty()) out.add(root);
        return out;
    }
}
