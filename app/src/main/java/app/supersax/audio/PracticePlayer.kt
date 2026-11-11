package app.supersax.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUi(
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val error: String? = null,
)

/**
 * Media3 ExoPlayer wrapper: play, seek, speed without changing pitch, and an A–B loop enforced by
 * polling [tick] (called by the UI about every 50 ms).
 */
class PracticePlayer(context: Context) {
    private val exo = ExoPlayer.Builder(context).build()
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _ui.value = _ui.value.copy(playing = isPlaying) }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) _ui.value = _ui.value.copy(durationMs = exo.duration.coerceAtLeast(0))
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _ui.value = _ui.value.copy(error = error.errorCodeName, playing = false)
            }
        })
    }

    fun load(uri: Uri) {
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        _ui.value = PlayerUi(speed = _ui.value.speed)
        exo.playbackParameters = PlaybackParameters(_ui.value.speed, 1f)
    }

    fun play() { if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(_ui.value.loopStartMs ?: 0); exo.play() }
    fun pause() = exo.pause()
    fun toggle() { if (exo.isPlaying) pause() else play() }
    fun seekTo(ms: Long) { exo.seekTo(ms); _ui.value = _ui.value.copy(positionMs = ms) }
    fun position(): Long = exo.currentPosition

    /** Playback speed (0.5..1.0 for practice); pitch stays the same. */
    fun setSpeed(speed: Float) {
        val s = speed.coerceIn(0.25f, 2f)
        exo.playbackParameters = PlaybackParameters(s, 1f)
        _ui.value = _ui.value.copy(speed = s)
    }

    fun setLoop(startMs: Long?, endMs: Long?) { _ui.value = _ui.value.copy(loopStartMs = startMs, loopEndMs = endMs) }

    fun setVolume(v: Float) { exo.volume = v }

    /** Keeps the position fresh and wraps around the A–B loop. */
    fun tick() {
        val pos = exo.currentPosition
        val u = _ui.value
        val a = u.loopStartMs; val b = u.loopEndMs
        if (a != null && b != null && b > a && pos >= b) exo.seekTo(a)
        _ui.value = u.copy(positionMs = exo.currentPosition, durationMs = exo.duration.coerceAtLeast(0))
    }

    fun release() = exo.release()
}
