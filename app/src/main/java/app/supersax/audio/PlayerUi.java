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

    public PlayerUi(boolean playing, long positionMs, long durationMs, float speed,
                    @Nullable Long loopStartMs, @Nullable Long loopEndMs, @Nullable String error) {
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

    public PlayerUi withPlaying(boolean v) { return new PlayerUi(v, positionMs, durationMs, speed, loopStartMs, loopEndMs, error); }
    public PlayerUi withPosition(long pos, long dur) { return new PlayerUi(playing, pos, dur, speed, loopStartMs, loopEndMs, error); }
    public PlayerUi withPositionMs(long v) { return withPosition(v, durationMs); }
    public PlayerUi withDurationMs(long v) { return withPosition(positionMs, v); }
    public PlayerUi withSpeed(float v) { return new PlayerUi(playing, positionMs, durationMs, v, loopStartMs, loopEndMs, error); }
    public PlayerUi withLoop(@Nullable Long a, @Nullable Long b) { return new PlayerUi(playing, positionMs, durationMs, speed, a, b, error); }
    public PlayerUi withError(@Nullable String v) { return new PlayerUi(playing, positionMs, durationMs, speed, loopStartMs, loopEndMs, v); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerUi)) return false;
        PlayerUi p = (PlayerUi) o;
        return playing == p.playing && positionMs == p.positionMs && durationMs == p.durationMs && Float.compare(speed, p.speed) == 0
                && Objects.equals(loopStartMs, p.loopStartMs) && Objects.equals(loopEndMs, p.loopEndMs) && Objects.equals(error, p.error);
    }

    @Override public int hashCode() { return Objects.hash(playing, positionMs, durationMs, speed, loopStartMs, loopEndMs, error); }
}
