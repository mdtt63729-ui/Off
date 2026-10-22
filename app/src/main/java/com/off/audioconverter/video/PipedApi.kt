package com.off.audioconverter.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A minimal client for the Piped API — the backend LibreTube is built on.
 *
 * Piped instances are community-run and come and go, so several are tried in
 * order and the first that answers wins. Only `/streams/{videoId}` is needed
 * here: it returns the same shape of information NewPipe does (title, uploader,
 * duration, thumbnail, muxed video streams, video-only streams and audio
 * streams), which makes it a clean fallback when local extraction fails.
 */
object PipedApi {

    val INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.private.coffee",
        "https://pipedapi.drgns.space"
    )

    suspend fun streams(videoId: String): VideoInfo? = withContext(Dispatchers.IO) {
        for (base in INSTANCES) {
            val json = runCatching { get("$base/streams/$videoId") }.getOrNull() ?: continue
            runCatching { parse(json, base) }.getOrNull()?.let { if (it.qualities.isNotEmpty()) return@withContext it }
        }
        null
    }

    private fun parse(json: JSONObject, base: String): VideoInfo {
        val muxed = json.optJSONArray("videoStreams") ?: JSONArray()
        val videoOnly = json.optJSONArray("videoOnlyStreams") ?: JSONArray()
        val audio = json.optJSONArray("audioStreams") ?: JSONArray()

        var bestAudioUrl: String? = null
        var bestAudioBitrate = -1
        for (i in 0 until audio.length()) {
            val item = audio.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (!url.startsWith("http")) continue
            val bitrate = item.optInt("bitrate", 0)
            if (bitrate > bestAudioBitrate) {
                bestAudioBitrate = bitrate
                bestAudioUrl = url
            }
        }

        val qualities = ArrayList<VideoQuality>()
        val muxedHeights = HashSet<Int>()

        for (i in 0 until muxed.length()) {
            val item = muxed.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (!url.startsWith("http")) continue
            val height = item.optInt("height", heightFromQuality(item.optString("quality")))
            muxedHeights += height
            qualities += VideoQuality(
                label = item.optString("quality").ifBlank { "${height}p" },
                height = height,
                muxed = true,
                videoUrl = url,
                audioUrl = null,
                mimeType = item.optString("mimeType").ifBlank { "video/mp4" },
                sizeBytes = item.optLong("contentLength", -1).takeIf { it > 0 }
            )
        }

        val seenHeights = HashSet<Int>()
        for (i in 0 until videoOnly.length()) {
            val item = videoOnly.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (!url.startsWith("http")) continue
            val height = item.optInt("height", heightFromQuality(item.optString("quality")))
            if (height in muxedHeights || !seenHeights.add(height)) continue
            qualities += VideoQuality(
                label = item.optString("quality").ifBlank { "${height}p" },
                height = height,
                muxed = false,
                videoUrl = url,
                audioUrl = bestAudioUrl,
                mimeType = item.optString("mimeType").ifBlank { "video/mp4" },
                sizeBytes = item.optLong("contentLength", -1).takeIf { it > 0 }
            )
        }

        return VideoInfo(
            id = json.optString("id").ifBlank { "" },
            title = json.optString("title").ifBlank { "Video" },
            uploader = json.optString("uploader").ifBlank { "Unknown" },
            durationSec = json.optLong("duration", 0L),
            thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.startsWith("http") },
            qualities = qualities.sortedByDescending { it.height },
            source = base.removePrefix("https://")
        )
    }

    private fun heightFromQuality(quality: String): Int =
        quality.filter { it.isDigit() }.toIntOrNull() ?: 0

    private fun get(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Off-Android/3.4.0")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("Piped returned HTTP $code")
            val body = connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            JSONObject(body)
        } finally {
            runCatching { connection.disconnect() }
        }
    }
}
