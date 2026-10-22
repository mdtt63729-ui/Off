package com.off.audioconverter.player

import android.content.Context
import android.content.SharedPreferences
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
import com.off.audioconverter.music.TrackJson
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
 * observable state so the mini player, the full player and the queue all render
 * from the same source of truth. Streams are resolved just-in-time because
 * YouTube URLs are short-lived, and the queue is persisted so it comes back
 * after the app is closed.
 */
object PlayerController : Player.Listener {

    private const val PREFS = "off_queue"
    private const val KEY_QUEUE = "queue"
    private const val KEY_INDEX = "index"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = MusicRepository()

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null
    private var prefs: SharedPreferences? = null

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

    private val _queueTracks = MutableStateFlow<List<Track>>(emptyList())
    val queueTracks: StateFlow<List<Track>> = _queueTracks.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    fun connect(context: Context) {
        val app = context.applicationContext
        if (prefs == null) {
            prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            restoreQueue()
        }
        if (controller != null || future != null) return
        val token = SessionToken(app, PlaybackService.component(app))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            runCatching { f.get() }.onSuccess { c ->
                controller = c
                c.addListener(this)
                refreshFlags()
                prepareRestored()
            }.onFailure { _message.value = "Playback could not start: ${it.message}" }
        }, ContextCompat.getMainExecutor(app))
    }

    /** Plays [tracks] starting at [startIndex]. */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startIndex.coerceIn(0, tracks.size - 1)
        publishQueue()
        playCurrent()
    }

    /** Appends to the end of the queue. */
    fun addToQueue(track: Track) {
        queue = queue + track
        if (index < 0) index = 0
        publishQueue()
        refreshFlags()
    }

    /** Inserts right after the current track. */
    fun playNext(track: Track) {
        if (queue.isEmpty() || index < 0) {
            playQueue(listOf(track), 0)
            return
        }
        val mutable = queue.toMutableList()
        mutable.add(index + 1, track)
        queue = mutable
        publishQueue()
        refreshFlags()
    }

    fun playAt(position: Int) {
        if (position !in queue.indices) return
        index = position
        publishQueue()
        playCurrent()
    }

    private fun playCurrent() {
        val track = queue.getOrNull(index) ?: return
        _current.value = track
        _message.value = "Loading ${track.title}…"
        refreshFlags()
        scope.launch {
            runCatching { repository.resolveStream(track) }
                .onSuccess { resolved -> setItem(track, resolved.url, resolved.mimeType, autoplay = true) }
                .onFailure { _message.value = it.message ?: "This track could not be played." }
        }
    }

    /** After a restart: show the last track and have it ready, but don't autoplay. */
    private fun prepareRestored() {
        val track = queue.getOrNull(index) ?: return
        _current.value = track
        scope.launch {
            runCatching { repository.resolveStream(track) }
                .onSuccess { resolved -> setItem(track, resolved.url, resolved.mimeType, autoplay = false) }
        }
    }

    private fun setItem(track: Track, url: String, mimeType: String, autoplay: Boolean) {
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
        if (autoplay) {
            c.play()
            _message.value = null
            startTicker()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
            startTicker()
        }
    }

    fun next() {
        if (index + 1 < queue.size) {
            index += 1
            publishQueue()
            playCurrent()
        }
    }

    fun previous() {
        if (index - 1 >= 0) {
            index -= 1
            publishQueue()
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

    /* ------------------------------- persistence ------------------------------ */

    private fun publishQueue() {
        _queueTracks.value = queue
        _queueIndex.value = index
        refreshFlags()
        prefs?.edit()
            ?.putString(KEY_QUEUE, TrackJson.listToJson(queue))
            ?.putInt(KEY_INDEX, index)
            ?.apply()
    }

    private fun restoreQueue() {
        val store = prefs ?: return
        queue = TrackJson.listFromJson(store.getString(KEY_QUEUE, null))
        index = store.getInt(KEY_INDEX, -1).coerceIn(-1, (queue.size - 1).coerceAtLeast(-1))
        if (index < 0 && queue.isNotEmpty()) index = 0
        _queueTracks.value = queue
        _queueIndex.value = index
        refreshFlags()
    }

    private fun refreshFlags() {
        _hasNext.value = index >= 0 && index + 1 < queue.size
        _hasPrevious.value = index > 0
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                val c = controller
                if (c != null) {
                    _positionMs.value = c.currentPosition.coerceAtLeast(0)
                    val d = c.duration
                    if (d > 0) _durationMs.value = d
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
        if (playbackState == Player.STATE_ENDED) {
            if (index + 1 < queue.size) next()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        _message.value = "Playback error: ${error.errorCodeName}"
        _isBuffering.value = false
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        EqualizerController.attach(audioSessionId)
    }
}
