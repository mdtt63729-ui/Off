package com.off.audioconverter.video

/** One downloadable rendition of a video. */
data class VideoQuality(
    val label: String,
    val height: Int,
    /** True when the stream already carries audio, so no muxing is needed. */
    val muxed: Boolean,
    val videoUrl: String,
    /** Present only when [muxed] is false — the audio to pair with the video. */
    val audioUrl: String?,
    val mimeType: String,
    val sizeBytes: Long? = null
)

/** Everything the download screen needs about a pasted link. */
data class VideoInfo(
    val id: String,
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val qualities: List<VideoQuality>,
    /** Which backend answered: "NewPipe" or a Piped instance host. */
    val source: String
) {
    val best: VideoQuality? get() = qualities.firstOrNull()
}

/** A download in flight or finished. */
data class VideoDownloadTask(
    val id: String,
    val title: String,
    val label: String,
    val thumbnailUrl: String?,
    val status: VideoDownloadStatus = VideoDownloadStatus.QUEUED,
    val progress: Float? = null,
    val stage: String? = null,
    val savedName: String? = null,
    val savedUri: String? = null,
    val message: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class VideoDownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED }

/**
 * Turns a pasted link (or a bare id) into a YouTube video id.
 * Accepts watch/shorts/embed/live URLs, youtu.be short links and raw ids.
 */
fun extractVideoId(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null

    // A bare id.
    if (text.length == 11 && text.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return text

    val patterns = listOf(
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""/embed/([A-Za-z0-9_-]{11})"""),
        Regex("""/live/([A-Za-z0-9_-]{11})"""),
        Regex("""/v/([A-Za-z0-9_-]{11})""")
    )
    for (pattern in patterns) {
        pattern.find(text)?.groupValues?.get(1)?.let { return it }
    }
    return null
}

/** Canonical watch URL for an id. */
fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
