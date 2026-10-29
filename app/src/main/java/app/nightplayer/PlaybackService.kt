package app.nightplayer

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTimer(false)
            savePosition()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            val oldId = oldPosition.mediaItem?.mediaId ?: return
            if (oldId == newPosition.mediaItem?.mediaId) return
            val pos = if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) 0L else oldPosition.positionMs
            Store.savePosition(oldId, pos)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) Store.lastTrack = mediaItem.mediaId
        }
    }

    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                item.buildUpon().setUri(item.localConfiguration?.uri ?: Uri.parse(item.mediaId)).build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.addListener(listener)
        session = MediaSession.Builder(this, player).setCallback(callback).build()
        scope.launch { Store.speed.collect { player.setPlaybackSpeed(it) } }
        scope.launch { Store.pauseAtEnd.collect { player.pauseAtEndOfMediaItems = it } }
        scope.launch { Store.sleepMinutes.collect { if (it > 0 && player.isPlaying) startTimer(false) } }
        scope.launch {
            Store.commands.collect { cmd ->
                when (cmd) {
                    TimerCommand.Cancel -> stopTimer()
                    TimerCommand.Restart -> startTimer(true)
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) savePosition()
            }
        }
    }

    private fun savePosition() {
        val item = player.currentMediaItem ?: return
        Store.savePosition(item.mediaId, player.currentPosition)
        Store.lastTrack = item.mediaId
    }

    private fun startTimer(restart: Boolean) {
        if (Store.sleepMinutes.value <= 0) return
        if (timerJob?.isActive == true && !restart) return
        timerJob?.cancel()
        player.volume = 1f
        timerJob = scope.launch {
            var elapsed = 0L
            var last = SystemClock.elapsedRealtime()
            while (true) {
                val fadeAt = Store.sleepMinutes.value * 60_000L
                if (fadeAt <= 0L) break
                val stopAt = fadeAt + Store.fadeMinutes.value * 60_000L
                Store.timer.value = TimerStatus(elapsed, fadeAt, stopAt)
                if (elapsed >= stopAt) {
                    player.pause()
                    break
                }
                player.volume = if (elapsed < fadeAt) {
                    1f
                } else {
                    val left = 1f - (elapsed - fadeAt).toFloat() / (stopAt - fadeAt)
                    left * left
                }
                delay(250)
                val now = SystemClock.elapsedRealtime()
                if (player.isPlaying) elapsed += now - last
                last = now
            }
            player.volume = 1f
            Store.timer.value = null
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        player.volume = 1f
        Store.timer.value = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        savePosition()
        stopTimer()
        scope.cancel()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }
}
