package app.supersax.audio;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** What a PracticePlayer shows. Immutable. */
public final class PlayerUi {
    private final boolean playing;
    private final long positionMs;
    private final long durationMs;
    private final float speed;
    private final @Nullable Long loopStartMs;
    private final @Nullable Long loopEndMs;
    private final @Nullable String error;
    /** Key shift in semitones (speed is unchanged). */
    private final int semitones;
    /** Playback pauses when it reaches this position (null = no stop). */
    private final @Nullable Long stopAtMs;

    public PlayerUi(boolean playing, long positionMs, long durationMs, float speed,
                    @Nullable Long loopStartMs, @Nullable Long loopEndMs, @Nullable String error) {
        this(playing, positionMs, durationMs, speed, loopStartMs, loopEndMs, error, 0, null);
    }

    public PlayerUi(boolean playing, long positionMs, long durationMs, float speed,
                    @Nullable Long loopStartMs, @Nullable Long loopEndMs, @Nullable String error, int semitones, @Nullable Long stopAtMs) {
        this.semitones = semitones;
        this.stopAtMs = stopAtMs;
        this.playing = playing;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.speed = speed;
        this.loopStartMs = loopStartMs;
        this.loopEndMs = loopEndMs;
        this.error = error;
    }

    public PlayerUi() { this(false, 0, 0, 1f, null, null, null); }

    public boolean getPlaying() { return playing; }
    public long getPositionMs() { return positionMs; }
    public long getDurationMs() { return durationMs; }
    public float getSpeed() { return speed; }
    public @Nullable Long getLoopStartMs() { return loopStartMs; }
    public @Nullable Long getLoopEndMs() { return loopEndMs; }
    public @Nullable String getError() { return error; }
    public int getSemitones() { return semitones; }
    public @Nullable Long getStopAtMs() { return stopAtMs; }

    public PlayerUi withPlaying(boolean v) { return new PlayerUi(v, positionMs, durationMs, speed, loopStartMs, loopEndMs, error, semitones, stopAtMs); }
    public PlayerUi withPosition(long pos, long dur) { return new PlayerUi(playing, pos, dur, speed, loopStartMs, loopEndMs, error, semitones, stopAtMs); }
    public PlayerUi withPositionMs(long v) { return withPosition(v, durationMs); }
    public PlayerUi withDurationMs(long v) { return withPosition(positionMs, v); }
    public PlayerUi withSpeed(float v) { return new PlayerUi(playing, positionMs, durationMs, v, loopStartMs, loopEndMs, error, semitones, stopAtMs); }
    public PlayerUi withLoop(@Nullable Long a, @Nullable Long b) { return new PlayerUi(playing, positionMs, durationMs, speed, a, b, error, semitones, stopAtMs); }
    public PlayerUi withError(@Nullable String v) { return new PlayerUi(playing, positionMs, durationMs, speed, loopStartMs, loopEndMs, v, semitones, stopAtMs); }
    public PlayerUi withSemitones(int v) { return new PlayerUi(playing, positionMs, durationMs, speed, loopStartMs, loopEndMs, error, v, stopAtMs); }
    public PlayerUi withStopAt(@Nullable Long v) { return new PlayerUi(playing, positionMs, durationMs, speed, loopStartMs, loopEndMs, error, semitones, v); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerUi)) return false;
        PlayerUi p = (PlayerUi) o;
        return playing == p.playing && positionMs == p.positionMs && durationMs == p.durationMs && Float.compare(speed, p.speed) == 0
                && Objects.equals(loopStartMs, p.loopStartMs) && Objects.equals(loopEndMs, p.loopEndMs) && Objects.equals(error, p.error)
                && semitones == p.semitones && Objects.equals(stopAtMs, p.stopAtMs);
    }

    @Override public int hashCode() { return Objects.hash(playing, positionMs, durationMs, speed, loopStartMs, loopEndMs, error, semitones, stopAtMs); }
}
