package app.supersax.playback

import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.supersax.AppContainer
import app.supersax.audio.SongEngine
import app.supersax.domain.common.AudioRefRecord
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Playback carried to another screen: switching between a song and its AI version, at the same spot. */
data class Handoff(val songId: String, val positionMs: Long, val playing: Boolean, val speed: Float, val semitones: Int)

/** What the mini player shows. */
data class NowPlaying(val songId: String, val title: String, val playing: Boolean, val hasNext: Boolean, val hasPrevious: Boolean,
                      val practice: Boolean, val positionMs: Long, val durationMs: Long)

/**
 * The app side of background playback: connects to [PlaybackService] (which starts it), plays songs and
 * playlists, hands the practice engine to the session, and publishes what is playing.
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackHub(private val c: AppContainer) {
    private var future: ListenableFuture<MediaController>? = null
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now = _now.asStateFlow()

    /** Metronome and volumes last set for the practised song, so reopening it changes nothing. */
    var practiceMix: Pair<String, Any>? = null

    /** The practice engine kept between screens (one song at a time), with its song id. */
    private var practiceEngine: SongEngine? = null
    private var practiceSong: String? = null
    private var practicePlayer: PracticePlayer? = null

    private suspend fun controller(): MediaController = withContext(Dispatchers.Main) {
        val f = future ?: MediaController.Builder(c.context, SessionToken(c.context, ComponentName(c.context, PlaybackService::class.java)))
            .buildAsync().also { future = it }
        val ctl = suspendCancellableCoroutine { cont ->
            f.addListener({ runCatching { f.get() }.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) } }, { it.run() })
        }
        if (!listening) { ctl.addListener(listener(ctl)); listening = true }
        ctl
    }

    private var listening = false

    private fun listener(ctl: MediaController) = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(ctl)
    }

    private fun publish(ctl: MediaController) {
        val item = ctl.currentMediaItem
        _now.value = if (item == null) null else NowPlaying(
            item.mediaId, item.mediaMetadata.title?.toString() ?: "", ctl.isPlaying || ctl.playWhenReady,
            ctl.hasNextMediaItem(), ctl.hasPreviousMediaItem(), item.mediaMetadata.artist?.toString()?.contains("practice") == true,
            ctl.currentPosition, ctl.duration.coerceAtLeast(0),
        )
    }

    /** Refreshes the position shown (the session sends events on changes, not every second). */
    fun tick() { future?.takeIf { it.isDone }?.let { f -> runCatching { publish(f.get()) } } }

    /** Plays [songs] in the background from [start] (shuffled if asked), like a music app. */
    fun playSongs(songs: List<AudioRefRecord>, start: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        c.appScope.launch(Dispatchers.Main) {
            val ctl = controller()
            PlaybackService.instance?.useListening()
            val items = songs.map { s ->
                MediaItem.Builder().setMediaId(s.id).setUri(Uri.parse(s.uri))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist("Super Sax").build()).build()
            }
            ctl.shuffleModeEnabled = shuffle
            ctl.setMediaItems(items, if (shuffle) 0 else start.coerceIn(0, items.size - 1), 0L)
            ctl.prepare()
            ctl.play()
        }
    }

    fun toggle() = c.appScope.launch(Dispatchers.Main) { val ctl = controller(); if (ctl.isPlaying || ctl.playWhenReady) ctl.pause() else ctl.play() }
    fun next() = c.appScope.launch(Dispatchers.Main) { controller().seekToNextMediaItem() }
    fun previous() = c.appScope.launch(Dispatchers.Main) { controller().seekToPreviousMediaItem() }
    fun stop() = c.appScope.launch(Dispatchers.Main) { val ctl = controller(); ctl.stop(); ctl.clearMediaItems() }

    /** For the practice engine: which song id each loaded version is (0 = the song opened). */
    var practiceVersions: MutableMap<String, Int> = mutableMapOf()
        private set

    /** Set just before opening another version of the song: the new screen carries on from here. */
    var handoff: Handoff? = null

    /**
     * If the music player is playing [songId] (started from the song list or a playlist), pauses it and returns
     * where it was and whether it played, so the song's practice screen can carry on from there.
     */
    suspend fun takeListening(songId: String): Pair<Long, Boolean>? = withContext(Dispatchers.Main) {
        val svc = PlaybackService.instance ?: return@withContext null
        val exo = svc.exo
        if (svc.current !== exo || exo.currentMediaItem?.mediaId != songId) return@withContext null
        val at = exo.currentPosition to (exo.isPlaying || exo.playWhenReady)
        exo.pause()
        at
    }

    // --- practice ------------------------------------------------------------------------------

    /** The practice engine for [songId]: the one still playing in the background if it is that song, else a new one. */
    fun practiceEngine(songId: String): SongEngine {
        val e = practiceEngine
        if (e != null && practiceSong == songId) { practicePlayer?.keepEngine = false; return e }
        val n = SongEngine()
        practiceEngine = n; practiceSong = songId; practicePlayer = null
        practiceVersions = mutableMapOf(songId to 0)
        return n
    }

    /** The practised song started playing: the session (notification, lock screen) now controls it. */
    fun attachPractice(engine: SongEngine, songId: String, title: String) {
        c.appScope.launch(Dispatchers.Main) {
            controller()
            val svc = PlaybackService.instance ?: return@launch
            val p = practicePlayer?.takeIf { it.engine === engine } ?: PracticePlayer(engine, songId, title).also { practicePlayer = it }
            svc.usePractice(p)
            p.refresh()
        }
    }

    /** Tells the session the engine changed (play, pause, seek from the screen). */
    fun practiceChanged(engine: SongEngine) { practicePlayer?.takeIf { it.engine === engine }?.refresh() }

    /**
     * The practice screen closed: a playing engine keeps going in the background (the service owns it until
     * another song is practised); a paused one is released.
     */
    fun practiceScreenClosed(engine: SongEngine) {
        if (engine !== practiceEngine) { engine.release(); return }
        if (engine.ui.value.playing && practicePlayer != null) { practicePlayer?.keepEngine = true; return }
        practicePlayer?.let { p -> PlaybackService.instance?.dropPractice(p) }
        engine.release()
        practiceEngine = null; practiceSong = null; practicePlayer = null
    }
}
