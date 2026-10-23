package com.off.audioconverter.video

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.off.audioconverter.ui.CrashLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The on-device yt-dlp engine, reached through Chaquopy.
 *
 * yt-dlp is the catch-all extractor: whatever NewPipe Extractor and the Piped API
 * cannot read, this can (Instagram, TikTok, Facebook, X, Reddit, Vimeo,
 * Dailymotion, Twitch VOD, SoundCloud and the rest of yt-dlp's list). It runs
 * entirely on the phone — no Off-owned backend, no third-party extraction API.
 *
 * Every call here blocks, so call them off the main thread.
 */
object YtDlpEngine {

    @Volatile private var started = false
    @Volatile private var ffmpegReady = false
    @Volatile private var engineVersion: String? = null

    /** Returns false when the interpreter could not be started. Never throws. */
    private fun ensureStarted(context: Context): Boolean {
        if (started) return true
        synchronized(this) {
            if (started) return true
            return try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
                started = true
                true
            } catch (t: Throwable) {
                CrashLog.record(context, "Could not start the Python engine", t)
                false
            }
        }
    }

    private fun bridge(context: Context) = Python.getInstance().getModule("bridge")

    /**
     * Points yt-dlp at the bundled ffmpeg, so separate video and audio streams can
     * be merged. Returns false when the binaries are missing, in which case only
     * single-file formats will work.
     */
    fun prepare(context: Context): Boolean {
        if (ffmpegReady) return true
        if (!ensureStarted(context)) return false
        return runCatching {
            val dir = File(context.filesDir, "ffmpeg").apply { mkdirs() }
            bridge(context).callAttr(
                "prepare_ffmpeg",
                context.applicationInfo.nativeLibraryDir,
                dir.absolutePath
            )
            ffmpegReady = true
            true
        }.getOrDefault(false)
    }

    fun version(context: Context): String? {
        engineVersion?.let { return it }
        if (!ensureStarted(context)) return null
        return runCatching {
            bridge(context).callAttr("version").toString().also { engineVersion = it }
        }.getOrNull()
    }

    /** Reads metadata for a link. Throws with a readable message if it cannot. */
    fun extractInfo(context: Context, url: String): JSONObject {
        if (!ensureStarted(context)) {
            throw IllegalStateException("The on-device engine could not start.")
        }
        val raw = runCatching {
            bridge(context).callAttr("extract_info", url).toString()
        }.getOrElse { throw IllegalStateException("The on-device engine could not start.") }

        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IllegalStateException("The on-device engine returned something unexpected.")
        if (!json.optBoolean("ok")) {
            throw IllegalStateException(
                json.optString("error").ifBlank { "That link could not be read." }
            )
        }
        return json
    }

    /** Blocks until the download finishes. Poll [progress] from another thread. */
    fun startDownload(context: Context, url: String, selector: String, outputDir: File, jobId: String): Boolean {
        if (!ensureStarted(context)) return false
        runCatching { outputDir.mkdirs() }
        return runCatching {
            val raw = bridge(context)
                .callAttr("start_download", url, selector, outputDir.absolutePath, jobId)
                .toString()
            JSONObject(raw).optBoolean("ok")
        }.getOrDefault(false)
    }

    fun cancel(context: Context, jobId: String) {
        if (!started) return
        runCatching { bridge(context).callAttr("cancel_job", jobId) }
    }

    fun progress(context: Context, jobId: String): YtDlpProgress? {
        if (!started) return null
        val raw = runCatching { bridge(context).callAttr("get_progress", jobId).toString() }.getOrNull() ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return YtDlpProgress(
            status = json.optString("status"),
            percent = json.optDouble("percent", 0.0).toFloat(),
            message = json.optString("message"),
            speedBytesPerSecond = json.optLong("speed"),
            etaSeconds = if (json.isNull("eta")) null else json.optInt("eta")
        )
    }

    fun cleanup(context: Context, jobId: String) {
        if (!started) return
        runCatching { bridge(context).callAttr("cleanup_progress", jobId) }
    }

    /* ------------------------------- mapping ------------------------------- */

    private data class RawFormat(
        val id: String,
        val ext: String,
        val height: Int,
        val size: Long,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val abr: Double
    )

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "m4a", "mp4" -> "video/mp4"
        else -> "video/mp4"
    }

    private fun audioMimeFor(ext: String): String = when (ext.lowercase()) {
        "webm" -> "audio/webm"
        "mp3" -> "audio/mpeg"
        else -> "audio/mp4"
    }

    /**
     * Turns yt-dlp's format list into Off's model. A height that already carries
     * audio is offered as-is; where only video-only exists, the best audio stream
     * is paired with it and yt-dlp merges the two using the bundled ffmpeg.
     */
    fun toVideoInfo(pageUrl: String, json: JSONObject): VideoInfo {
        val array = json.optJSONArray("formats") ?: JSONArray()
        val formats = ArrayList<RawFormat>()
        for (i in 0 until array.length()) {
            val f = array.optJSONObject(i) ?: continue
            val id = f.optString("format_id")
            if (id.isBlank()) continue
            formats += RawFormat(
                id = id,
                ext = f.optString("ext"),
                height = f.optInt("height"),
                size = f.optLong("filesize"),
                hasVideo = f.optBoolean("has_video"),
                hasAudio = f.optBoolean("has_audio"),
                abr = f.optDouble("abr", 0.0)
            )
        }

        val videos = formats.filter { it.hasVideo }
        val audios = formats.filter { !it.hasVideo && it.hasAudio }
        val bestAudio = audios.maxByOrNull { it.abr } ?: audios.maxByOrNull { it.size }

        val qualities = ArrayList<VideoQuality>()
        val heights = videos.map { it.height }.filter { it > 0 }.distinct().sortedDescending()
        for (height in heights) {
            val atHeight = videos.filter { it.height == height }
            val muxed = atHeight.firstOrNull { it.hasAudio }
            if (muxed != null) {
                qualities += VideoQuality(
                    label = "${height}p",
                    height = height,
                    muxed = true,
                    videoUrl = null,
                    audioUrl = null,
                    mimeType = mimeFor(muxed.ext),
                    sizeBytes = muxed.size.takeIf { it > 0 },
                    ytDlpSelector = muxed.id
                )
            } else {
                val videoOnly = atHeight.maxByOrNull { it.size } ?: atHeight.first()
                qualities += VideoQuality(
                    label = "${height}p",
                    height = height,
                    muxed = false,
                    videoUrl = null,
                    audioUrl = null,
                    mimeType = mimeFor(videoOnly.ext),
                    sizeBytes = videoOnly.size.takeIf { it > 0 },
                    ytDlpSelector = if (bestAudio != null) "${videoOnly.id}+${bestAudio.id}" else "${videoOnly.id}+bestaudio"
                )
            }
        }

        val audioOptions = ArrayList<AudioOption>()
        if (audios.isNotEmpty()) {
            val selector = bestAudio?.id ?: "bestaudio"
            val ext = bestAudio?.ext ?: "m4a"
            audioOptions += AudioOption(
                label = "M4A · original",
                url = null,
                outputFormat = "m4a",
                mp3BitrateKbps = null,
                sourceMime = audioMimeFor(ext),
                ytDlpSelector = selector
            )
            audioOptions += AudioOption(
                label = "MP3 · 320 kbps",
                url = null,
                outputFormat = "mp3",
                mp3BitrateKbps = 320,
                sourceMime = audioMimeFor(ext),
                ytDlpSelector = selector
            )
            audioOptions += AudioOption(
                label = "MP3 · 192 kbps",
                url = null,
                outputFormat = "mp3",
                mp3BitrateKbps = 192,
                sourceMime = audioMimeFor(ext),
                ytDlpSelector = selector
            )
        }

        val extractor = json.optString("extractor").ifBlank { "yt-dlp" }
        return VideoInfo(
            id = json.optString("id").ifBlank { pageUrl },
            title = json.optString("title").ifBlank { "Video" },
            uploader = json.optString("uploader").ifBlank { "Unknown" },
            durationSec = json.optLong("duration").coerceAtLeast(0L),
            thumbnailUrl = json.optString("thumbnail").takeIf { it.startsWith("http") },
            description = json.optString("description"),
            qualities = qualities,
            audioOptions = audioOptions,
            source = "yt-dlp · $extractor"
        )
    }
}

data class YtDlpProgress(
    val status: String,
    val percent: Float,
    val message: String,
    val speedBytesPerSecond: Long,
    val etaSeconds: Int?
)
