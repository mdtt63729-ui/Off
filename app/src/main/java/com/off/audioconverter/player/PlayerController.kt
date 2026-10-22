package com.off.audioconverter.player

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.off.audioconverter.music.MusicRepository
import com.off.audioconverter.music.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide playback brain.
 *
 * Owns a [MediaController] connected to [PlaybackService] and exposes simple,
 * observable state so both the mini player and the full player can render from
 * the same source of truth. Streams are resolved just-in-time because YouTube
 * URLs are short-lived.
 */
object PlayerController : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = MusicRepository()

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null

    private var queue: List<Track> = emptyList()
    private var index: Int = -1

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious: StateFlow<Boolean> = _hasPrevious.asStateFlow()

    fun connect(context: Context) {
        if (controller != null || future != null) return
        val app = context.applicationContext
        val token = SessionToken(app, PlaybackService.component(app))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            runCatching { f.get() }.onSuccess { c ->
                controller = c
                c.addListener(this)
                refreshFlags()
            }.onFailure { _message.value = "Playback could not start: ${it.message}" }
        }, ContextCompat.getMainExecutor(app))
    }

    /** Plays [tracks] starting at [startIndex]. */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startIndex.coerceIn(0, tracks.size - 1)
        playCurrent()
    }

    private fun playCurrent() {
        val track = queue.getOrNull(index) ?: return
        _current.value = track
        _message.value = "Loading ${track.title}…"
        refreshFlags()
        scope.launch {
            runCatching { repository.resolveStream(track) }
                .onSuccess { resolved -> startPlayback(track, resolved.url, resolved.mimeType) }
                .onFailure { _message.value = it.message ?: "This track could not be played." }
        }
    }

    private fun startPlayback(track: Track, url: String, mimeType: String) {
        val c = controller ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply { track.artworkUrl?.let { setArtworkUri(it.toUri()) } }
            .build()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(track.id)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
        _message.value = null
        startTicker()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        if (index + 1 < queue.size) {
            index += 1
            playCurrent()
        }
    }

    fun previous() {
        if (index - 1 >= 0) {
            index -= 1
            playCurrent()
        } else {
            controller?.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
        _positionMs.value = positionMs.coerceAtLeast(0)
    }

    fun stop() {
        controller?.stop()
        _current.value = null
        _isPlaying.value = false
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun refreshFlags() {
        _hasNext.value = index >= 0 && index + 1 < queue.size
        _hasPrevious.value = index > 0
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val c = controller
                if (c != null) {
                    _positionMs.value = c.currentPosition.coerceAtLeast(0)
                    val d = c.duration
                    _durationMs.value = if (d > 0) d else 0L
                }
                delay(200)
            }
        }
    }

    /* ------------------------------ Player.Listener ----------------------------- */

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        _isBuffering.value = playbackState == Player.STATE_BUFFERING
        if (playbackState == Player.STATE_READY) {
            val d = controller?.duration ?: 0L
            if (d > 0) _durationMs.value = d
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        _message.value = "Playback error: ${error.errorCodeName}"
        _isBuffering.value = false
    }
}
