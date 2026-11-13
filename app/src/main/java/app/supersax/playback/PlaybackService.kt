package app.supersax.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.supersax.MainActivity
import app.supersax.R

/**
 * Plays songs with the screen off, like a music app: a media session shows play/pause, next/previous and the
 * position in the notification, on the lock screen and on headphones. The session plays either the song list
 * or playlists (ExoPlayer), or the song being practised (the practice engine, with its speed, key, loop and clicks).
 */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackService : MediaSessionService() {
    lateinit var exo: ExoPlayer
        private set
    private lateinit var session: MediaSession
    private var practice: PracticePlayer? = null

    override fun onCreate() {
        super.onCreate()
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val open = PendingIntent.getActivity(this, 7, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SONGS, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, exo).setSessionActivity(open).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_note) })
        instance = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    /** Songs and playlists: the practice engine is paused and the session follows the music player again. */
    fun useListening() {
        practice?.let { it.engine.pause(); it.refresh() }
        if (session.player !== exo) session.player = exo
    }

    /** The song being practised takes the controls (the music player is paused). */
    fun usePractice(p: PracticePlayer) {
        exo.pause()
        // Only one song at a time: the song practised before stops.
        if (practice !== p) practice?.let { old -> old.engine.pause(); if (!old.keepEngine) old.engine.release() }
        practice = p
        if (session.player !== p) session.player = p
    }

    /** The practised song is gone (screen closed while paused): back to the music player. */
    fun dropPractice(p: PracticePlayer) {
        if (practice !== p) return
        practice = null
        if (session.player === p) session.player = exo
    }

    val current: Player get() = session.player

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiped away from recents: keep playing if something plays, else stop the service.
        if (!session.player.playWhenReady || session.player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        instance = null
        practice?.engine?.release()
        session.release()
        exo.release()
        super.onDestroy()
    }

    companion object {
        /** The running service (same process), or null. */
        @Volatile var instance: PlaybackService? = null
            private set
    }
}
