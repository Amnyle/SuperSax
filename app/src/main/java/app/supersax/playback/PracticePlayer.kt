package app.supersax.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import app.supersax.audio.SongEngine
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The practice engine seen as a media player, so the notification and lock screen can pause, play and seek it. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PracticePlayer(val engine: SongEngine, private val songId: String, private val title: String) : SimpleBasePlayer(Looper.getMainLooper()) {
    /** Set when the practice screen closed while playing: the engine now belongs to the service. */
    @Volatile var keepEngine = false

    fun refresh() = invalidateState()

    override fun getState(): State {
        val u = engine.ui.value
        val item = MediaItemData.Builder(songId)
            .setMediaItem(MediaItem.Builder().setMediaId(songId)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("Super Sax · practice").build()).build())
            .setDurationUs(u.durationMs * 1000)
            .setIsSeekable(true)
            .build()
        return State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAll(
                Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TIMELINE, Player.COMMAND_STOP).build())
            .setPlaylist(listOf(item))
            .setPlaybackState(if (engine.isReady) Player.STATE_READY else Player.STATE_IDLE)
            .setPlayWhenReady(u.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs { engine.position() }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) engine.play() else engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        engine.seekTo(positionMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.pause()
        return Futures.immediateVoidFuture()
    }
}
