package com.off.audioconverter.video

import com.off.audioconverter.music.HttpDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Resolves a pasted link into something downloadable.
 *
 * NewPipe Extractor does the work locally first — no instance, no middleman —
 * and the Piped API (the backend LibreTube uses) is the fallback for when
 * YouTube changes something the extractor has not caught up with yet.
 *
 * Muxed (progressive) streams are preferred, because they already contain audio
 * and need no post-processing. Where a height only exists as video-only, the
 * best audio stream is carried alongside it and the two are muxed with FFmpeg
 * after download.
 */
class VideoRepository {

    companion object {
        private const val YOUTUBE_SERVICE_ID = 0

        @Volatile
        private var newPipeReady = false

        private fun ensureNewPipe() {
            if (newPipeReady) return
            synchronized(this) {
                if (!newPipeReady) {
                    runCatching { NewPipe.init(HttpDownloader()) }
                    newPipeReady = true
                }
            }
        }
    }

    init {
        ensureNewPipe()
    }

    /** Throws with a readable message if the link cannot be read at all. */
    suspend fun resolve(input: String): VideoInfo = withContext(Dispatchers.IO) {
        val id = extractVideoId(input)
            ?: error("That doesn't look like a YouTube link. Paste a video URL or an 11-character video id.")

        val local = runCatching { resolveWithNewPipe(id) }.getOrNull()
        if (local != null && local.qualities.isNotEmpty()) return@withContext local

        PipedApi.streams(id)?.let { return@withContext it }

        error("Could not read that video. Check the link, and try again in a moment.")
    }

    private fun resolveWithNewPipe(videoId: String): VideoInfo {
        val service = NewPipe.getService(YOUTUBE_SERVICE_ID)
        val info = StreamInfo.getInfo(service, watchUrl(videoId))

        val muxed = info.videoStreams
            .filter { it.isUrl && !it.url.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedByDescending { it.height }

        val videoOnly = info.videoOnlyStreams
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            .sortedByDescending { it.height }

        val bestAudio = info.audioStreams
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            .maxByOrNull { it.averageBitrate }

        val qualities = ArrayList<VideoQuality>()
        val muxedHeights = HashSet<Int>()

        for (stream in muxed) {
            val url = stream.url ?: continue
            muxedHeights += stream.height
            qualities += VideoQuality(
                label = labelFor(stream.height, stream.fps),
                height = stream.height,
                muxed = true,
                videoUrl = url,
                audioUrl = null,
                mimeType = stream.format?.mimeType ?: "video/mp4"
            )
        }

        val seen = HashSet<Int>()
        for (stream in videoOnly) {
            val url = stream.url ?: continue
            if (stream.height in muxedHeights || !seen.add(stream.height)) continue
            qualities += VideoQuality(
                label = labelFor(stream.height, stream.fps),
                height = stream.height,
                muxed = false,
                videoUrl = url,
                audioUrl = bestAudio?.url,
                mimeType = stream.format?.mimeType ?: "video/mp4"
            )
        }

        return VideoInfo(
            id = videoId,
            title = info.name ?: "Video",
            uploader = info.uploaderName ?: "Unknown",
            durationSec = info.duration.coerceAtLeast(0L),
            thumbnailUrl = info.thumbnails.lastOrNull()?.url,
            qualities = qualities.sortedByDescending { it.height },
            source = "NewPipe"
        )
    }

    private fun labelFor(height: Int, fps: Int): String =
        if (fps > 30) "${height}p$fps" else "${height}p"
}
