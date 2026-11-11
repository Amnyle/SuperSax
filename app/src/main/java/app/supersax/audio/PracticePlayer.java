package app.supersax.audio;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.Nullable;

/**
 * Media3 ExoPlayer wrapper: play, seek, speed without changing pitch, and an A–B loop enforced by
 * polling tick() (called by the UI about every 50 ms).
 */
public final class PracticePlayer {
    private final ExoPlayer exo;
    private final MutableStateFlow<PlayerUi> ui = StateFlowKt.MutableStateFlow(new PlayerUi());

    public PracticePlayer(Context context) {
        exo = new ExoPlayer.Builder(context).build();
        exo.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) { ui.setValue(ui.getValue().withPlaying(isPlaying)); }

            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) ui.setValue(ui.getValue().withDurationMs(Math.max(0, exo.getDuration())));
            }

            @Override public void onPlayerError(PlaybackException error) {
                ui.setValue(ui.getValue().withError(error.getErrorCodeName()).withPlaying(false));
            }
        });
    }

    public StateFlow<PlayerUi> getUi() { return ui; }

    public void load(Uri uri) {
        exo.setMediaItem(MediaItem.fromUri(uri));
        exo.prepare();
        float speed = ui.getValue().getSpeed();
        ui.setValue(new PlayerUi().withSpeed(speed));
        exo.setPlaybackParameters(new PlaybackParameters(speed, 1f));
    }

    public void play() {
        if (exo.getPlaybackState() == Player.STATE_ENDED) {
            Long a = ui.getValue().getLoopStartMs();
            exo.seekTo(a == null ? 0 : a);
        }
        exo.play();
    }

    public void pause() { exo.pause(); }

    public void toggle() { if (exo.isPlaying()) pause(); else play(); }

    public void seekTo(long ms) { exo.seekTo(ms); ui.setValue(ui.getValue().withPositionMs(ms)); }

    public long position() { return exo.getCurrentPosition(); }

    /** Playback speed (0.5..1.0 for practice); pitch stays the same. */
    public void setSpeed(float speed) {
        float s = Math.max(0.25f, Math.min(2f, speed));
        exo.setPlaybackParameters(new PlaybackParameters(s, 1f));
        ui.setValue(ui.getValue().withSpeed(s));
    }

    public void setLoop(@Nullable Long startMs, @Nullable Long endMs) { ui.setValue(ui.getValue().withLoop(startMs, endMs)); }

    public void setVolume(float v) { exo.setVolume(v); }

    /** Keeps the position fresh and wraps around the A–B loop. */
    public void tick() {
        long pos = exo.getCurrentPosition();
        PlayerUi u = ui.getValue();
        Long a = u.getLoopStartMs(), b = u.getLoopEndMs();
        if (a != null && b != null && b > a && pos >= b) exo.seekTo(a);
        ui.setValue(u.withPosition(exo.getCurrentPosition(), Math.max(0, exo.getDuration())));
    }

    public void release() { exo.release(); }
}
