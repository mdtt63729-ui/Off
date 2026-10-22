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
 * NewPipe Extractor does the work locally first — no instance, no middleman. For
 * YouTube links the Piped API (the backend LibreTube uses) is the fallback for
 * when YouTube changes something the extractor has not caught up with yet. For
 * any other site NewPipe supports (PeerTube, SoundCloud, Bandcamp, media.ccc.de)
 * the extractor picks the right service by itself.
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
        val text = input.trim()
        if (text.isEmpty()) error("Paste a video link first.")

        val videoId = extractVideoId(text)
        if (videoId != null) {
            val local = runCatching { resolveYouTube(videoId) }.getOrNull()
            if (local != null && local.qualities.isNotEmpty()) return@withContext local

            PipedApi.streams(videoId)?.let { return@withContext it }

            error("Could not read that video. Check the link, and try again in a moment.")
        }

        // Not a YouTube link — let the extractor choose the service.
        val generic = runCatching { resolveWithService(text) }.getOrNull()
        if (generic != null && generic.qualities.isNotEmpty()) return@withContext generic

        error("That link isn't supported. YouTube works, as do the sites NewPipe handles (PeerTube, SoundCloud, Bandcamp).")
    }

    private fun resolveYouTube(videoId: String): VideoInfo {
        val service = NewPipe.getService(YOUTUBE_SERVICE_ID)
        val info = StreamInfo.getInfo(service, watchUrl(videoId))
        return buildInfo(info, id = videoId, source = "NewPipe")
    }

    private fun resolveWithService(url: String): VideoInfo {
        val service = NewPipe.getServiceByUrl(url)
        val info = StreamInfo.getInfo(service, url)
        val name = runCatching { service.serviceInfo.name }.getOrNull() ?: "NewPipe"
        return buildInfo(info, id = url, source = name)
    }

    private fun buildInfo(info: StreamInfo, id: String, source: String): VideoInfo {
        val muxed = info.videoStreams
            .filter { it.isUrl && !it.url.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedByDescending { it.height }

        val videoOnly = info.videoOnlyStreams
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            .sortedByDescending { it.height }

        // Prefer AAC-in-MP4 audio, so a video-only rendition can be muxed into MP4 with -c copy.
        val bestAudio = info.audioStreams
            .filter { it.isUrl && !it.url.isNullOrBlank() }
            .sortedWith(
                compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                    it.format?.mimeType?.contains("mp4") == true
                }.thenByDescending { it.averageBitrate }
            )
            .firstOrNull()

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
            id = id,
            title = info.name ?: "Video",
            uploader = info.uploaderName ?: "Unknown",
            durationSec = info.duration.coerceAtLeast(0L),
            thumbnailUrl = info.thumbnails.lastOrNull()?.url,
            qualities = qualities.sortedByDescending { it.height },
            source = source
        )
    }

    private fun labelFor(height: Int, fps: Int): String =
        if (fps > 30) "${height}p$fps" else "${height}p"
}
