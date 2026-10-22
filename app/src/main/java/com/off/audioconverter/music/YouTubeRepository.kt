package com.off.audioconverter.music

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.HttpURLConnection
import java.net.URL

/** A resolved, directly playable audio resource. */
data class ResolvedAudio(
    val url: String,
    val mimeType: String,
    val extension: String,
    val bitrateKbps: Int
)

/**
 * A [Downloader] backed by plain [HttpURLConnection]. NewPipe Extractor needs one of
 * these to fetch pages; it is intentionally dependency-free.
 */
class HttpDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 25000
        connection.instanceFollowRedirects = true
        connection.requestMethod = request.httpMethod()
        request.headers().forEach { (name, values) ->
            values.forEach { value -> connection.addRequestProperty(name, value) }
        }
        val body = request.dataToSend()
        if (body != null && body.isNotEmpty()) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val headers = connection.headerFields
            ?.filterKeys { it != null }
            ?.mapValues { entry -> entry.value ?: emptyList() }
            ?: emptyMap()
        return Response(code, connection.responseMessage ?: "", headers, text, connection.url.toString())
    }
}

/**
 * YouTube search and audio resolution through NewPipe Extractor.
 *
 * This reads YouTube's public pages/APIs the way a browser would; it is not an
 * official Google API and YouTube can change it at any time.
 */
class YouTubeRepository {

    companion object {
        private const val YOUTUBE_SERVICE_ID = 0

        @Volatile
        private var initialised = false

        private fun ensureInitialised() {
            if (initialised) return
            synchronized(this) {
                if (!initialised) {
                    NewPipe.init(HttpDownloader())
                    initialised = true
                }
            }
        }
    }

    init {
        ensureInitialised()
    }

    private fun service() = NewPipe.getService(YOUTUBE_SERVICE_ID)

    fun search(query: String): List<Track> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val handler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(term)
        val info = SearchInfo.getInfo(service(), handler)
        val out = ArrayList<Track>()
        for (item in info.relatedItems) {
            val stream = item as? StreamInfoItem ?: continue
            val url = stream.url ?: continue
            val id = extractId(url)
            out += Track(
                id = "yt:$id",
                source = MusicSource.YOUTUBE,
                title = stream.name ?: "Untitled",
                artist = stream.uploaderName?.takeIf { it.isNotBlank() } ?: "YouTube",
                album = null,
                artworkUrl = stream.thumbnails.lastOrNull()?.url,
                streamUrl = null, // resolved lazily, just-in-time
                sourceId = id,
                pageUrl = url,
                durationSec = stream.duration.coerceAtLeast(0L),
                bitrateKbps = null,
                mimeType = null
            )
        }
        return out
    }

    /** Resolves the best playable audio URL for a YouTube track. */
    fun resolveAudio(track: Track): ResolvedAudio {
        val url = track.pageUrl
            ?: track.sourceId?.let { "https://www.youtube.com/watch?v=$it" }
            ?: error("This YouTube track has no video id.")
        val info = StreamInfo.getInfo(service(), url)
        val candidates = info.audioStreams.filter { it.isUrl && !it.url.isNullOrBlank() }
        if (candidates.isEmpty()) error("No audio stream is available for this track.")

        val progressive = candidates
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }
        val best = progressive
            ?: candidates.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: candidates.maxByOrNull { it.averageBitrate }

        val chosen: AudioStream = best ?: candidates.first()
        val format = chosen.format
        return ResolvedAudio(
            url = chosen.url ?: error("The selected audio stream has no URL."),
            mimeType = format?.mimeType ?: "audio/mp4",
            extension = format?.suffix ?: "m4a",
            bitrateKbps = chosen.averageBitrate
        )
    }

    private fun extractId(url: String): String {
        val marker = "v="
        val start = url.indexOf(marker)
        if (start >= 0) {
            val rest = url.substring(start + marker.length)
            return rest.substringBefore('&')
        }
        return url.substringAfterLast('/')
    }
}
