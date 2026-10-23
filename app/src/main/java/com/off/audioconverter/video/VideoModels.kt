package com.off.audioconverter.video

/** One downloadable rendition of a video. */
data class VideoQuality(
    val label: String,
    val height: Int,
    /** True when the stream already carries audio, so no muxing is needed. */
    val muxed: Boolean,
    /** Null for yt-dlp renditions, which yt-dlp fetches itself. */
    val videoUrl: String?,
    /** Present only when [muxed] is false — the audio to pair with the video. */
    val audioUrl: String?,
    val mimeType: String,
    val sizeBytes: Long? = null,
    /** Set when this rendition comes from yt-dlp: the format selector to hand it. */
    val ytDlpSelector: String? = null
)

/** An audio-only download: the source audio, optionally re-encoded to MP3. */
data class AudioOption(
    val label: String,
    val url: String?,
    /** "mp3" re-encodes with FFmpeg; "m4a" saves the original audio untouched. */
    val outputFormat: String,
    val mp3BitrateKbps: Int?,
    val sourceMime: String,
    val ytDlpSelector: String? = null
)

/** Everything the download screen needs about a pasted link. */
data class VideoInfo(
    val id: String,
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val description: String,
    val qualities: List<VideoQuality>,
    val audioOptions: List<AudioOption>,
    /** Which backend answered: "NewPipe", a Piped host, or "yt-dlp · <extractor>". */
    val source: String
) {
    val best: VideoQuality? get() = qualities.firstOrNull()
}

/** What the user picked to download. */
data class DownloadSelection(
    val label: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val sourceMime: String,
    /** "mp4", "webm", "mp3" or "m4a". */
    val outputFormat: String,
    val mp3BitrateKbps: Int? = null,
    /** The page the user pasted — yt-dlp downloads from this, not from a stream URL. */
    val pageUrl: String? = null,
    /** When set, the download runs through yt-dlp with this format selector. */
    val ytDlpSelector: String? = null
) {
    val isAudio: Boolean get() = outputFormat == "mp3" || outputFormat == "m4a"
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
    /** Where the half-finished download lives, so a paused one can pick up again. */
    val workDir: String? = null,
    /** Everything needed to restart the download after a pause. */
    val selection: DownloadSelection? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class VideoDownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

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

/**
 * The audio choices offered for every video: the original audio untouched, plus
 * two MP3 re-encodes. All three read from the best audio stream available.
 */
fun audioOptionsFrom(bestAudioUrl: String?, sourceMime: String?): List<AudioOption> {
    if (bestAudioUrl.isNullOrBlank()) return emptyList()
    val mime = sourceMime ?: "audio/mp4"
    return listOf(
        AudioOption("M4A · original", bestAudioUrl, outputFormat = "m4a", mp3BitrateKbps = null, sourceMime = mime),
        AudioOption("MP3 · 320 kbps", bestAudioUrl, outputFormat = "mp3", mp3BitrateKbps = 320, sourceMime = mime),
        AudioOption("MP3 · 192 kbps", bestAudioUrl, outputFormat = "mp3", mp3BitrateKbps = 192, sourceMime = mime)
    )
}
